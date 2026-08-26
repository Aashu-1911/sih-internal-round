package app.swarsetu.stt

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * Persistent STT diagnostic log.
 *
 * Writes a single append-only trace file under no-backup storage so we can inspect the exact last
 * boundary reached even when adb/logcat is unavailable.
 */
object SttTraceLogger {
    private val lock = Any()
    @Volatile
    private var traceFile: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            if (traceFile != null) return
            val dir = File(context.noBackupFilesDir, DIR_NAME).apply { mkdirs() }
            val stamp = System.currentTimeMillis()
            traceFile = File(dir, fileName(stamp))
            writeLine(
                "trace opened at ${Instant.ofEpochMilli(stamp)} " +
                    "device=${Build.MANUFACTURER} ${Build.MODEL} " +
                    "abi=${Build.SUPPORTED_ABIS.joinToString()} " +
                    "sdk=${Build.VERSION.SDK_INT}",
                mirrorLog = false,
            )
        }
    }

    fun file(): File? = traceFile

    fun stageForShare(context: Context): android.net.Uri? {
        val src = file() ?: latestTraceFile(context) ?: return null
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val dest = File(dir, SHARE_NAME)
        runCatching { dest.writeText(src.readText()) }.getOrNull() ?: return null
        return runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest) }.getOrNull()
    }

    fun log(step: String, message: String) {
        writeLine("$step $message")
    }

    fun error(step: String, message: String, throwable: Throwable? = null) {
        val extra = throwable?.let { "${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        writeLine("$step $message${if (extra.isBlank()) "" else " :: $extra"}")
        if (throwable != null) {
            writeLine(
                throwable.stackTraceToString()
                    .trimEnd(),
                mirrorLog = false,
            )
        }
    }

    private fun writeLine(line: String, mirrorLog: Boolean = true) {
            val text = buildString {
            append(Instant.now())
            append(' ')
            append(line)
            append('\n')
        }
        synchronized(lock) {
            val file = traceFile ?: return
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(text)
            }.onFailure {
                if (mirrorLog) Log.w(TAG, "failed to write trace: ${it.message}")
            }
        }
        if (mirrorLog) Log.i(TAG, line)
    }

    private fun fileName(stamp: Long): String = String.format(Locale.ROOT, "stt-trace-%013d.log", stamp)

    private fun latestTraceFile(context: Context): File? =
        File(context.noBackupFilesDir, DIR_NAME)
            .takeIf { it.isDirectory }
            ?.listFiles { f -> f.isFile && f.name.startsWith("stt-trace-") && f.name.endsWith(".log") }
            ?.maxByOrNull { it.name }

    private const val TAG = "SttTrace"
    private const val DIR_NAME = "stt-trace"
    private const val SHARE_DIR = "crash"
    private const val SHARE_NAME = "stt-trace.txt"
}
