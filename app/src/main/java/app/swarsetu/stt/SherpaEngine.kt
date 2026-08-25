package app.swarsetu.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Concrete [SttEngine] implementation using sherpa-onnx with AI4Bharat's IndicConformer models.
 *
 * sherpa-onnx provides:
 * - Fully offline inference (no network)
 * - INT8-quantized models for all target languages (~188 MB each)
 * - NeMo CTC-based models (IndicConformer architecture)
 * - Apache-2.0 license (redistribution-friendly)
 * - Native .so for all Android ABIs
 *
 * **Model loading:** Models are ONNX files in `assets/stt-{code}/`. This engine extracts them
 * to `context.filesDir` on first use (sherpa-onnx requires filesystem paths, not asset streams).
 * Extraction is idempotent — already-extracted models are skipped.
 *
 * **Audio format:** 16-bit signed PCM, mono, 16 kHz. All IndicConformer models expect this.
 *
 * **Thread safety:** sherpa-onnx's recognizer is not thread-safe. This engine serializes all calls
 * behind a [Mutex], matching the project's [app.swarsetu.moderation.MlTextModerator] pattern.
 *
 * **Knit-agnostic:** This class knows nothing about the mesh, the wire format, or the application
 * layer. It is a pure audio→text transform.
 */
class SherpaEngine(
    private val context: Context,
    private val modelManager: SttModelManager,
) : SttEngine {

    private val mutex = Mutex()
    private var initialized = false
    private var _config: SttConfig? = null
    private var _currentLanguage: SttLanguage? = null

    /** The loaded sherpa-onnx recognizer, or null if not loaded. */
    private var recognizer: OfflineRecognizer? = null

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
            if (!initialized || recognizer == null) return@withLock SttResult.empty(language)

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
        if (!initialized || recognizer == null) {
            emit(SttResult.empty(language))
            return@flow
        }

        // sherpa-onnx offline recognizer doesn't support true streaming,
        // so we chunk the PCM and emit partial results
        val chunkSize = language.sampleRate / 4 // 250ms chunks
        val totalChunks = (pcm.size + chunkSize - 1) / chunkSize
        val accumulated = StringBuilder()

        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, pcm.size)
            val chunk = pcm.copyOfRange(start, end)

            val partial = withContext(Dispatchers.Default) {
                mutex.withLock { inferPcmInternal(chunk, language) }
            }

            if (partial.text.isNotBlank()) {
                accumulated.append(partial.text)
            }

            val isFinal = (i == totalChunks - 1)
            val displayText = accumulated.toString().trim()

            emit(
                SttResult(
                    text = displayText,
                    type = if (isFinal) SttResultType.FINAL else SttResultType.PARTIAL,
                    language = language,
                ),
            )
        }

        // If no chunks produced text, emit one empty final result
        if (accumulated.isEmpty()) {
            emit(SttResult.empty(language))
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun release() {
        mutex.withLock {
            releaseModelInternal()
            initialized = false
            _config = null
            _currentLanguage = null
            Log.d(TAG, "Released")
        }
    }

    // --- Internal model management ---

    private suspend fun loadModelInternal(language: SttLanguage) {
        val modelDir = extractModelToInternal(language)
        val modelFile = File(modelDir, MODEL_FILENAME)
        val tokensFile = File(modelDir, TOKENS_FILENAME)

        if (!modelFile.exists()) {
            throw SttException.ModelLoadError(modelFile.absolutePath)
        }
        if (!tokensFile.exists()) {
            throw SttException.ModelLoadError(tokensFile.absolutePath)
        }

        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(
                    model = modelFile.absolutePath,
                ),
                tokens = tokensFile.absolutePath,
                numThreads = 2,
                provider = "cpu",
            ),
            decodingMethod = "greedy_search",
        )

        // Note: endpoint/silence rules are streaming-recognizer-only params; the offline
        // recognizer decodes complete utterances, and VAD silence detection is handled by
        // SttPipeline's VoiceActivityDetector.
        recognizer = OfflineRecognizer(assetManager = null, config = config)

        modelManager.markLoaded(
            SttModelInfo(
                language = language,
                modelPath = modelFile.absolutePath,
                description = "sherpa-onnx IndicConformer for ${language.displayName}",
            ),
        )
        Log.d(TAG, "Model loaded: ${language.code} from ${modelDir.absolutePath}")
    }

    private fun releaseModelInternal() {
        recognizer = null
        _currentLanguage?.let {
            runCatching { /* modelManager.markReleased handled by caller */ }
        }
    }

    /**
     * Extract the ONNX model from assets to internal storage. sherpa-onnx requires filesystem paths.
     * Already-extracted models are skipped (idempotent).
     */
    private fun extractModelToInternal(language: SttLanguage): File {
        val assetDir = language.assetDir ?: throw SttException.UnsupportedLanguage(language)
        val targetDir = File(context.filesDir, "stt/$assetDir")

        if (targetDir.exists() && File(targetDir, MODEL_FILENAME).exists()) {
            Log.d(TAG, "Model already extracted: $assetDir")
            return targetDir
        }

        targetDir.mkdirs()

        // Extract model.int8.onnx
        extractAssetFile(assetDir, MODEL_FILENAME, File(targetDir, MODEL_FILENAME))

        // Extract tokens.txt
        extractAssetFile(assetDir, TOKENS_FILENAME, File(targetDir, TOKENS_FILENAME))

        return targetDir
    }

    private fun extractAssetFile(assetDir: String, fileName: String, targetFile: File) {
        val assetPath = "$assetDir/$fileName"
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract $assetPath: ${e.message}")
            throw SttException.ModelLoadError(assetPath, e)
        }
    }

    // --- Internal inference ---

    private fun inferPcmInternal(pcm: ShortArray, language: SttLanguage): SttResult {
        val rec = recognizer ?: return SttResult.empty(language)
        return try {
            val stream = rec.createStream()
            // Convert ShortArray to FloatArray for sherpa-onnx (expects float samples)
            val floatPcm = FloatArray(pcm.size) { pcm[it].toFloat() / Short.MAX_VALUE }
            stream.acceptWaveform(floatPcm, language.sampleRate)
            rec.decode(stream)
            val text = rec.getResult(stream).text
            stream.release()
            SttResult(
                text = text,
                type = SttResultType.FINAL,
                language = language,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Inference error for ${language.code}: ${e.message}")
            SttResult.empty(language)
        }
    }

    private companion object {
        const val TAG = "SherpaEngine"
        const val MODEL_FILENAME = "model.int8.onnx"
        const val TOKENS_FILENAME = "tokens.txt"
    }
}
