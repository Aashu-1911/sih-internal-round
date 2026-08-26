package app.swarsetu.stt

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Factory for creating [SttEngine] instances.
 */
class SttEngineFactory(
    private val context: Context,
    private val modelManager: SttModelManager,
) {
    fun create(): SttEngine {
        Log.i(TAG, "[STT-DIAG-001] create() called")
        SttTraceLogger.log("STT-001", "factory create()")
        Log.i(TAG, "[STT-DIAG-002] Device ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
        Log.i(TAG, "[STT-DIAG-002b] Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        Log.i(TAG, "[STT-DIAG-003] Device: ${Build.MANUFACTURER} ${Build.MODEL}, SDK ${Build.VERSION.SDK_INT}")
        SttTraceLogger.log("STT-002", "device abi=${Build.SUPPORTED_ABIS.joinToString()} model=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")

        // Try Vosk FIRST — proven working, safe native libs, no SIGSEGV risk.
        try {
            Log.i(TAG, "[STT-DIAG-008] Probing org.vosk.Model via Class.forName...")
            val clazz = Class.forName("org.vosk.Model", false, context.classLoader)
            Log.i(TAG, "[STT-DIAG-009] Vosk Class.forName SUCCEEDED: ${clazz.name}")
            SttTraceLogger.log("STT-005", "vosk class probe ok: ${clazz.name}")
            val engine = VoskEngine(context, modelManager)
            Log.i(TAG, "[STT-DIAG-010] VoskEngine created: ${engine::class.simpleName}")
            SttTraceLogger.log("STT-006", "engine selected=${engine::class.qualifiedName}")
            return engine
        } catch (e: Throwable) {
            Log.w(TAG, "[STT-DIAG-011] Vosk probe FAILED: ${e.javaClass.simpleName}: ${e.message}")
            SttTraceLogger.error("STT-005E", "vosk class probe failed", e)
        }

        // Try Sherpa-ONNX as fallback — load native lib to verify it works.
        try {
            Log.i(TAG, "[STT-DIAG-004] Probing Sherpa-ONNX native library...")
            SttTraceLogger.log("STT-003", "probing sherpa native lib")
            // Force class init which triggers System.loadLibrary("sherpa-onnx-jni")
            val clazz = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
            Log.i(TAG, "[STT-DIAG-005] Sherpa native lib loaded: ${clazz.name}")
            SttTraceLogger.log("STT-003B", "sherpa native lib ok: ${clazz.name}")
            val engine = SherpaOnnxEngine(context, modelManager)
            Log.i(TAG, "[STT-DIAG-006] SherpaOnnxEngine created: ${engine::class.simpleName}")
            SttTraceLogger.log("STT-004", "engine selected=SherpaOnnxEngine")
            return engine
        } catch (e: Throwable) {
            Log.w(TAG, "[STT-DIAG-007] Sherpa-ONNX FAILED: ${e.javaClass.simpleName}: ${e.message}")
            SttTraceLogger.error("STT-003E", "sherpa native lib failed", e)
        }

        // Fallback
        Log.w(TAG, "[STT-DIAG-012] Both Vosk and Sherpa-ONNX unavailable — using DefaultSttEngine")
        SttTraceLogger.log("STT-007", "fallback DefaultSttEngine")
        return DefaultSttEngine(context, modelManager).also {
            Log.i(TAG, "[STT-DIAG-012b] Concrete engine class: ${it::class.qualifiedName}")
        }
    }

    private companion object {
        const val TAG = "SttEngineFactory"
    }
}
