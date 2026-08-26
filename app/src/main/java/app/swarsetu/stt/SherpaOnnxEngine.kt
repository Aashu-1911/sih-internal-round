package app.swarsetu.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Concrete [SttEngine] implementation for Sherpa-ONNX offline speech recognition.
 *
 * Follows the same pattern as [VoskEngine]: implements [SttEngine] directly (not
 * [DefaultSttEngine]), creates a fresh [OfflineRecognizer] per transcription, and
 * handles native library loading failures gracefully.
 *
 * If the native sherpa-onnx-jni library cannot be loaded or crashes, this engine
 * degrades to returning empty results rather than crashing the app.
 */
class SherpaOnnxEngine(
    private val context: Context,
    private val modelManager: SttModelManager,
) : SttEngine {

    private val mutex = Mutex()
    private var initialized = false
    private var _config: SttConfig? = null
    private var _currentLanguage: SttLanguage? = null

    /** Whether the native library loaded successfully at least once. */
    private var nativeLibLoaded = false

    /** Whether the native library failed to load. If true, all calls return empty. */
    private var nativeLibFailed = false

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

        // Probe native library loading once. If it fails, all future calls return empty.
        if (!nativeLibLoaded && !nativeLibFailed) {
            try {
                Log.i(TAG, "Probing native library load...")
                SttTraceLogger.log("STT-030A", "probing native lib load")
                // Force class initialization which triggers System.loadLibrary
                Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
                nativeLibLoaded = true
                Log.i(TAG, "Native library loaded successfully")
                SttTraceLogger.log("STT-030B", "native lib loaded ok")
            } catch (e: Throwable) {
                nativeLibFailed = true
                Log.e(TAG, "Native library FAILED to load: ${e.javaClass.simpleName}: ${e.message}")
                SttTraceLogger.error("STT-030E", "native lib load failed", e)
                initialized = true // Graceful degradation
                return@withLock
            }
        }

        if (nativeLibFailed) {
            Log.w(TAG, "Native lib previously failed — returning empty results")
            initialized = true
            return@withLock
        }

        // Verify model assets exist
        val assetDir = config.language.assetDir
        if (assetDir == null) {
            Log.w(TAG, "No assetDir for ${config.language.code}")
            initialized = true
            return@withLock
        }

        try {
            val exists = context.assets.list(assetDir)?.isNotEmpty() == true
            Log.i(TAG, "Asset dir '$assetDir' exists=$exists")
            SttTraceLogger.log("STT-032", "assetDir=$assetDir exists=$exists")
            if (!exists) {
                Log.w(TAG, "Model assets not found for ${config.language.code}")
                initialized = true
                return@withLock
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not check assets: ${e.javaClass.simpleName}: ${e.message}")
        }

        initialized = true
        Log.d(TAG, "Initialized for ${config.language.code}")
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
            if (!initialized || nativeLibFailed) return@withLock SttResult.empty(language)
            if (!modelManager.isAvailable(language)) return@withLock SttResult.empty(language)

            val startMs = System.currentTimeMillis()
            val result = runCatching { inferPcmInternal(pcm, language) }
                .getOrElse { e ->
                    Log.w(TAG, "Inference failed: ${e.javaClass.simpleName}: ${e.message}")
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
        if (pcm.isEmpty() || nativeLibFailed || !initialized) {
            emit(SttResult.empty(language))
            return@flow
        }

        val rec = createRecognizer(language) ?: run {
            emit(SttResult.empty(language))
            return@flow
        }

        try {
            val chunkSize = language.sampleRate / 4 // 250ms chunks
            val totalChunks = (pcm.size + chunkSize - 1) / chunkSize
            val accumulated = StringBuilder()

            for (i in 0 until totalChunks) {
                val start = i * chunkSize
                val end = minOf(start + chunkSize, pcm.size)
                val chunk = pcm.copyOfRange(start, end)

                val floatChunk = FloatArray(chunk.size) { chunk[it].toFloat() / Short.MAX_VALUE }

                val stream = rec.createStream()
                stream.acceptWaveform(floatChunk, language.sampleRate)
                rec.decode(stream)
                val result = rec.getResult(stream)
                stream.release()

                val text = result.text.trim()
                if (text.isNotBlank()) {
                    accumulated.append(text)
                }

                val type = if (i == totalChunks - 1) SttResultType.FINAL else SttResultType.PARTIAL
                emit(
                    SttResult(
                        text = accumulated.toString().trim(),
                        type = type,
                        language = language,
                        confidence = 0.95f,
                    ),
                )
            }

            if (accumulated.isEmpty()) {
                emit(SttResult.empty(language))
            }
        } finally {
            runCatching { rec.release() }
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

    private fun releaseModelInternal() {
        // Nothing to release — we create fresh recognizers per transcription
    }

    /**
     * Create a fresh [OfflineRecognizer] for a single transcription call.
     * Returns null if creation fails (native lib crash, missing model, etc.).
     */
    private fun createRecognizer(language: SttLanguage): OfflineRecognizer? {
        val assetDir = language.assetDir ?: return null

        val modelAsset = "$assetDir/model.int8.onnx"
        val tokensAsset = "$assetDir/tokens.txt"

        return try {
            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    paraformer = OfflineParaformerModelConfig(
                        model = modelAsset,
                    ),
                    tokens = tokensAsset,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
                maxActivePaths = 4,
            )
            SttTraceLogger.log("STT-037", "create OfflineRecognizer for ${language.code}")
            val rec = OfflineRecognizer(
                assetManager = context.assets,
                config = config,
            )
            SttTraceLogger.log("STT-038", "OfflineRecognizer created ok")
            rec
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create OfflineRecognizer for ${language.code}: ${e.javaClass.simpleName}: ${e.message}")
            SttTraceLogger.error("STT-038E", "OfflineRecognizer creation failed", e)
            null
        }
    }

    // --- Internal inference ---

    private fun inferPcmInternal(pcm: ShortArray, language: SttLanguage): SttResult {
        val rec = createRecognizer(language) ?: return SttResult.empty(language)
        try {
            val floatPcm = FloatArray(pcm.size) { pcm[it].toFloat() / Short.MAX_VALUE }

            val stream = rec.createStream()
            stream.acceptWaveform(floatPcm, language.sampleRate)
            rec.decode(stream)
            val result = rec.getResult(stream)
            stream.release()

            val text = result.text.trim()
            return if (text.isNotBlank()) {
                SttResult(text = text, type = SttResultType.FINAL, language = language, confidence = 0.95f)
            } else {
                SttResult.empty(language)
            }
        } finally {
            runCatching { rec.release() }
        }
    }

    private companion object {
        const val TAG = "SherpaOnnxEngine"
    }
}
