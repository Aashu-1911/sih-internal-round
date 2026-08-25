package app.swarsetu.stt

import android.content.Context

/**
 * Factory for creating [SttEngine] instances. Abstracts the concrete engine implementation so the
 * application layer (Koin DI) can swap engines without changing consumers.
 *
 * Default implementation returns a [VoskEngine] (Vosk offline STT). If Vosk's native dependencies
 * are unavailable, fall back to [DefaultSttEngine] which gracefully degrades to empty results.
 *
 * Follows the project's DI convention: the factory is bound as a Koin `single` in
 * [app.swarsetu.di.SttModule], and engines are created via [create].
 */
class SttEngineFactory(
    private val context: Context,
    private val modelManager: SttModelManager,
) {
    /**
     * Create a new [SttEngine] instance. The engine is not initialized — call [SttEngine.initialize]
     * before use.
     *
     * Each call creates a fresh engine; the caller is responsible for calling [SttEngine.release]
     * when done. For a shared singleton engine, use Koin's `single { }` binding instead.
     *
     * The factory tries [VoskEngine] first; if the Vosk native library is not available, it
     * falls back to [DefaultSttEngine] (which returns empty results but doesn't crash).
     */
    fun create(): SttEngine {
        // Try Sherpa-ONNX first if available
        try {
            Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
            return SherpaOnnxEngine(context, modelManager)
        } catch (_: ClassNotFoundException) {
            // Proceed to Vosk
        }

        return try {
            // Probe for Vosk's native library — if it's not on the classpath, this will throw
            Class.forName("org.vosk.Model")
            VoskEngine(context, modelManager)
        } catch (_: ClassNotFoundException) {
            android.util.Log.w(TAG, "Vosk and Sherpa-ONNX not available — using DefaultSttEngine (empty results)")
            DefaultSttEngine(context, modelManager)
        }
    }

    private companion object {
        const val TAG = "SttEngineFactory"
    }
}
