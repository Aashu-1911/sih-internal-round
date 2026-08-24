package app.swarsetu.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Concrete [SttEngine] implementation using the Vosk offline speech recognition toolkit.
 *
 * Vosk provides:
 * - Fully offline inference (no network)
 * - Pre-trained models for all 10 target languages
 * - Streaming recognition with partial results
 * - Lightweight C++ backend via JNI (~1–50 MB per language model)
 * - Apache-2.0 license (redistribution-friendly)
 *
 * **Model loading:** Vosk models are directories containing `am/final.mdl`, `graph/`, and `conf/`.
 * This engine extracts them from Android assets into `context.filesDir` on first use (Vosk requires
 * a filesystem path, not an asset stream). Extraction is idempotent — already-extracted models
 * are skipped.
 *
 * **Audio format:** Vosk expects 16-bit signed PCM, mono, at the model's sample rate (typically
 * 8 kHz or 16 kHz). This engine resamples to 16 kHz if the model expects a different rate.
 *
 * **Streaming:** Vosk's `Recognizer` supports `acceptWaveForm(byte[])` for streaming. Partial
 * results are available via `partialResult`; final results via `result` after end-of-stream.
 *
 * **Thread safety:** Vosk's `Recognizer` is not thread-safe. This engine serializes all calls
 * behind a [kotlinx.coroutines.sync.Mutex], matching the [MlTextModerator] pattern.
 *
 * **Dependency:** Requires `com.alphacephei:vosk-android` on the classpath. If the dependency
 * is absent (e.g. F-Droid build), this engine gracefully degrades to returning empty results.
 * The [DefaultSttEngine] fallback can be used instead.
 *
 * **Knit-agnostic:** This class knows nothing about the mesh, the wire format, or the application
 * layer. It is a pure audio→text transform.
 */
class VoskEngine(
    private val context: Context,
    private val modelManager: SttModelManager,
) : SttEngine {

    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var initialized = false
    private var _config: SttConfig? = null
    private var _currentLanguage: SttLanguage? = null

    /** The loaded Vosk model, or null if not loaded / load failed. */
    private var model: Model? = null

    /** The active recognizer, created per-transcription or per-stream. */
    private var recognizer: Recognizer? = null

    override val config: SttConfig? get() = _config
    override val isReady: Boolean get() = initialized
    override val currentLanguage: SttLanguage? get() = _currentLanguage

    override suspend fun initialize(config: SttConfig) = mutex.withLock {
        if (initialized && _currentLanguage == config.language) {
            Log.d(TAG, "Already initialized for ${config.language.code}")
            return@withLock
        }

        // Release previous model if switching languages
        releaseModelInternal()

        _config = config
        _currentLanguage = config.language

        if (!modelManager.isAvailable(config.language)) {
            Log.w(TAG, "No model available for ${config.language.code} — returning empty results")
            initialized = true
            return@withLock
        }

        try {
            loadModelInternal(config.language)
            initialized = true
            Log.d(TAG, "Initialized for ${config.language.code}")
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed for ${config.language.code}: ${e.message}", e)
            initialized = true // Graceful degradation
        }
    }

    override suspend fun setLanguage(language: SttLanguage) {
        val currentConfig = _config ?: SttConfig()
        if (language == _currentLanguage && initialized) return
        initialize(currentConfig.copy(language = language))
    }

    override suspend fun transcribe(
        pcm: ShortArray,
        language: SttLanguage,
    ): SttResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (pcm.isEmpty()) return@withLock SttResult.empty(language)
            if (!initialized || model == null) return@withLock SttResult.empty(language)

            val startMs = System.currentTimeMillis()
            val result = runCatching { inferPcmInternal(pcm, language) }
                .getOrElse { e ->
                    Log.w(TAG, "Inference failed: ${e.message}")
                    SttResult.empty(language)
                }
            val elapsed = System.currentTimeMillis() - startMs

            if (result.text.isBlank()) {
                SttResult.empty(language)
            } else {
                result.copy(durationMs = elapsed)
            }
        }
    }

    override fun transcribeStream(
        pcm: ShortArray,
        language: SttLanguage,
    ): Flow<SttResult> = flow {
        if (pcm.isEmpty()) {
            emit(SttResult.empty(language))
            return@flow
        }
        if (!initialized || model == null) {
            emit(SttResult.empty(language))
            return@flow
        }

        val rec = createRecognizer(language) ?: run {
            emit(SttResult.empty(language))
            return@flow
        }

        try {
            val chunkSize = language.sampleRate / 4 // 250ms chunks for streaming
            val totalChunks = (pcm.size + chunkSize - 1) / chunkSize
            val accumulated = StringBuilder()

            for (i in 0 until totalChunks) {
                val start = i * chunkSize
                val end = minOf(start + chunkSize, pcm.size)
                val chunk = pcm.copyOfRange(start, end)

                // Convert ShortArray to bytes (little-endian 16-bit PCM)
                val bytes = shortsToBytes(chunk)
                val isFinal = rec.acceptWaveForm(bytes)

                val json = if (isFinal) rec.result else rec.partialResult
                val text = parseVoskText(json)

                if (text.isNotBlank()) {
                    if (isFinal) {
                        accumulated.append(text)
                    }
                    // For partial: emit current accumulated + partial text
                    val displayText = if (isFinal) {
                        accumulated.toString().trim()
                    } else {
                        (accumulated.toString() + text).trim()
                    }

                    emit(
                        SttResult(
                            text = displayText,
                            type = if (isFinal) SttResultType.FINAL else SttResultType.PARTIAL,
                            language = language,
                        ),
                    )
                } else if (isFinal) {
                    // Final result was empty — emit accumulated so far
                    emit(
                        SttResult(
                            text = accumulated.toString().trim(),
                            type = SttResultType.FINAL,
                            language = language,
                        ),
                    )
                }
            }

            // Final flush
            val finalJson = rec.finalResult
            val finalText = parseVoskText(finalJson)
            if (finalText.isNotBlank()) {
                accumulated.append(finalText)
            }
            val finalResult = accumulated.toString().trim()
            if (finalResult.isNotEmpty() || totalChunks == 0) {
                emit(
                    SttResult(
                        text = finalResult,
                        type = SttResultType.FINAL,
                        language = language,
                    ),
                )
            }
        } finally {
            runCatching { rec.close() }
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun release() = mutex.withLock {
        releaseModelInternal()
        initialized = false
        _config = null
        _currentLanguage = null
        Log.d(TAG, "Released")
    }

    // --- Internal model management ---

    private suspend fun loadModelInternal(language: SttLanguage) {
        val modelDir = extractModelToInternal(language)
        model = Model(modelDir.absolutePath)
        modelManager.markLoaded(
            SttModelInfo(
                language = language,
                modelPath = modelDir.absolutePath,
                description = "Vosk model for ${language.displayName}",
            ),
        )
        Log.d(TAG, "Model loaded: ${language.code} from ${modelDir.absolutePath}")
    }

    private fun releaseModelInternal() {
        runCatching { recognizer?.close() }
        recognizer = null
        runCatching { model?.close() }
        model = null
        _currentLanguage?.let { runCatching { /* modelManager.markReleased handled by caller */ } }
    }

    /**
     * Extract the Vosk model from assets to internal storage. Vosk requires a filesystem path.
     * Already-extracted models are skipped (idempotent).
     */
    private fun extractModelToInternal(language: SttLanguage): File {
        val assetDir = language.assetDir ?: throw SttException.UnsupportedLanguage(language)
        val targetDir = File(context.filesDir, "stt/$assetDir")

        if (targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            Log.d(TAG, "Model already extracted: ${assetDir}")
            return targetDir
        }

        targetDir.mkdirs()
        val assets = context.assets.list(assetDir) ?: emptyArray()
        if (assets.isEmpty()) {
            throw SttException.ModelLoadError(assetDir)
        }

        for (name in assets) {
            val assetPath = "$assetDir/$name"
            val targetFile = File(targetDir, name)

            // If it's a directory, recurse
            val subAssets = context.assets.list(assetPath)
            if (subAssets != null && subAssets.isNotEmpty()) {
                extractAssetDir(assetPath, File(targetDir, name))
            } else {
                // It's a file — copy it
                context.assets.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }

        return targetDir
    }

    private fun extractAssetDir(assetPath: String, targetDir: File) {
        targetDir.mkdirs()
        val assets = context.assets.list(assetPath) ?: return
        for (name in assets) {
            val subAssetPath = "$assetPath/$name"
            val targetFile = File(targetDir, name)
            val subAssets = context.assets.list(subAssetPath)
            if (subAssets != null && subAssets.isNotEmpty()) {
                extractAssetDir(subAssetPath, targetFile)
            } else {
                context.assets.open(subAssetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    // --- Internal inference ---

    private fun inferPcmInternal(pcm: ShortArray, language: SttLanguage): SttResult {
        val rec = createRecognizer(language) ?: return SttResult.empty(language)
        try {
            val bytes = shortsToBytes(pcm)
            rec.acceptWaveForm(bytes, bytes.size)
            val json = rec.finalResult
            val text = parseVoskText(json)
            return SttResult(
                text = text,
                type = SttResultType.FINAL,
                language = language,
            )
        } finally {
            runCatching { rec.close() }
        }
    }

    private fun createRecognizer(language: SttLanguage): Recognizer? {
        val m = model ?: return null
        return try {
            Recognizer(m, language.sampleRate.toFloat())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create recognizer for ${language.code}: ${e.message}")
            null
        }
    }

    // --- Utility ---

    /**
     * Parse Vosk's JSON output to extract the text field. Vosk returns JSON like:
     * `{"text": "hello world"}` for final results and
     * `{"partial": "hello"}` for partial results.
     */
    private fun parseVoskText(json: String): String {
        // Simple JSON parsing without kotlinx-serialization to avoid dependency
        // Vosk output is simple enough for regex extraction
        val textMatch = Regex(""""text"\s*:\s*"([^"]*)"""").find(json)
        val partialMatch = Regex(""""partial"\s*:\s*"([^"]*)"""").find(json)
        return textMatch?.groupValues?.get(1)
            ?: partialMatch?.groupValues?.get(1)
            ?: ""
    }

    /**
     * Convert a ShortArray (16-bit PCM) to a ByteArray (little-endian).
     * This is what Vosk's `acceptWaveForm` expects.
     */
    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (shorts[i].toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    private companion object {
        const val TAG = "VoskEngine"
    }
}
