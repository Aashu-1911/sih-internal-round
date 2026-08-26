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
 * Uses the Sherpa-ONNX Kotlin API directly (not reflection) with Paraformer models
 * bundled as `model.int8.onnx` + `tokens.txt` per language.
 *
 * Inherits thread-safety from [DefaultSttEngine] and adds Sherpa-ONNX-specific
 * model loading and inference.
 */
class SherpaOnnxEngine(
    private val context: Context,
    private val modelManager: SttModelManager,
) : DefaultSttEngine(context, modelManager) {

    private val mutex = Mutex()
    private var recognizer: OfflineRecognizer? = null

    override suspend fun loadModel(language: SttLanguage) {
        val assetDir = language.assetDir ?: return
        val modelAsset = "$assetDir/model.int8.onnx"
        val tokensAsset = "$assetDir/tokens.txt"

        // Verify assets exist
        try {
            context.assets.open(modelAsset).close()
            context.assets.open(tokensAsset).close()
        } catch (e: Throwable) {
            Log.w(TAG, "Model assets not found for ${language.code}: ${e.message}")
            return
        }

        try {
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

            recognizer = OfflineRecognizer(
                assetManager = context.assets,
                config = config,
            )
            Log.d(TAG, "Sherpa-ONNX recognizer created for ${language.code} from assets")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create recognizer for ${language.code}: ${e.javaClass.simpleName}: ${e.message}", e)
            recognizer = null
        }
    }

    override suspend fun inferPcm(pcm: ShortArray, language: SttLanguage): SttResult {
        val rec = recognizer ?: return SttResult.empty(language)

        return try {
            // Convert ShortArray (16-bit PCM) to FloatArray (normalized to [-1, 1])
            val floatPcm = FloatArray(pcm.size) { pcm[it].toFloat() / Short.MAX_VALUE }

            val stream = rec.createStream()
            stream.acceptWaveform(floatPcm, language.sampleRate)
            rec.decode(stream)

            val result = rec.getResult(stream)
            stream.release()

            val text = result.text.trim()
            if (text.isNotBlank()) {
                SttResult(
                    text = text,
                    type = SttResultType.FINAL,
                    language = language,
                    confidence = 0.95f,
                )
            } else {
                SttResult.empty(language)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Sherpa-ONNX inference error for ${language.code}: ${e.javaClass.simpleName}: ${e.message}", e)
            SttResult.empty(language)
        }
    }

    /**
     * Streaming transcription: processes PCM chunks and emits intermediate results.
     * Uses the same recognizer but creates new streams for each chunk.
     */
    override fun transcribeStream(
        pcm: ShortArray,
        language: SttLanguage,
    ): Flow<SttResult> = flow {
        val rec = recognizer
        if (rec == null) {
            emit(SttResult.empty(language))
            return@flow
        }

        val chunkSize = language.sampleRate / 2 // 500ms chunks
        val totalChunks = (pcm.size + chunkSize - 1) / chunkSize
        val accumulated = StringBuilder()

        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, pcm.size)
            val chunk = pcm.copyOfRange(start, end)

            // Convert to float
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
    }.flowOn(Dispatchers.Default)

    override suspend fun releaseModel() {
        mutex.withLock {
            try {
                recognizer?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing recognizer: ${e.message}")
            }
            recognizer = null
        }
        super.releaseModel()
    }

    companion object {
        private const val TAG = "SherpaOnnxEngine"
    }
}
