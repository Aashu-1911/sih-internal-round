package app.swarsetu.tts

import android.content.Context
import app.swarsetu.tts.audio.TtsAudioFocusManager
import app.swarsetu.tts.scheduler.TtsScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The primary facade for text-to-speech functionality in SwarSetu.
 * Responsible for orchestrating engine initialization, scheduling requests,
 * and managing audio focus.
 */
class TtsManager(
    private val context: Context,
    private val engine: TtsEngine,
    scope: CoroutineScope,
) {
    private val scheduler = TtsScheduler(engine, scope)

    private val audioFocusManager =
        TtsAudioFocusManager(context) {
            // Stop all speech if we lose audio focus
            stopAll()
        }

    init {
        scope.launch {
            try {
                initialize()
            } catch (e: Exception) {
                android.util.Log.w("TtsManager", "Eager TTS initialization failed: ${e.message}")
            }
        }
    }

    /**
     * Prepares the TTS engine for use. Should be called early.
     */
    suspend fun initialize(): Boolean = engine.initialize()

    /**
     * Submits a text [request] to be spoken.
     */
    suspend fun speak(request: TtsRequest) {
        runCatching { audioFocusManager.requestFocus() }
        scheduler.submit(request)
    }

    /**
     * Queries the offline capability of a specific language.
     */
    fun isLanguageAvailable(language: TtsLanguage): TtsLanguageCapability = engine.isLanguageAvailable(language)

    /**
     * Interrupts all ongoing speech.
     */
    fun stopAll() {
        scheduler.stopAll()
        audioFocusManager.abandonFocus()
    }

    /**
     * Cleans up resources. Should be called when the application or service is destroyed.
     */
    fun shutdown() {
        stopAll()
        engine.release()
    }
}
