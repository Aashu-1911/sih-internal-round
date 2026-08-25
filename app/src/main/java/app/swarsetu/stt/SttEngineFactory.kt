package app.swarsetu.stt

import android.content.Context
import android.util.Log

/**
 * Factory for creating [SttEngine] instances. Abstracts the concrete engine implementation so the
 * application layer (Koin DI) can swap engines without changing consumers.
 *
 * Default implementation returns a [SherpaEngine] (sherpa-onnx with IndicConformer models).
 * If sherpa-onnx's native dependencies are unavailable, fall back to [DefaultSttEngine]
 * which gracefully degrades to empty results.
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
     * The factory tries [SherpaEngine] first; if the native library is not available, it
     * falls back to [DefaultSttEngine] (which returns empty results but doesn't crash).
     */
    fun create(): SttEngine {
        return try {
            // Probe for sherpa-onnx's native library — if it's not on the classpath, this will throw
            Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
            SherpaEngine(context, modelManager)
        } catch (_: ClassNotFoundException) {
            Log.w(TAG, "sherpa-onnx not available — using DefaultSttEngine (empty results)")
            DefaultSttEngine(context, modelManager)
        }
    }

    private companion object {
        const val TAG = "SttEngineFactory"
    }
}
