package app.swarsetu.tts

import android.content.Context
import app.swarsetu.tts.audio.TtsAudioFocusManager
import app.swarsetu.tts.scheduler.TtsScheduler
import kotlinx.coroutines.CoroutineScope

/**
 * The primary facade for text-to-speech functionality in SwarSetu.
 * Responsible for orchestrating engine initialization, scheduling requests,
 * and managing audio focus.
 */
class TtsManager(
    private val context: Context,
    private val engine: TtsEngine,
    scope: CoroutineScope
) {
    private val scheduler = TtsScheduler(engine, scope)
    
    private val audioFocusManager = TtsAudioFocusManager(context) {
        // Stop all speech if we lose audio focus
        stopAll()
    }

    /**
     * Prepares the TTS engine for use. Should be called early.
     */
    suspend fun initialize(): Boolean {
        return engine.initialize()
    }

    /**
     * Submits a text [request] to be spoken.
     */
    suspend fun speak(request: TtsRequest) {
        // Try to gain audio focus before starting speech.
        // Alert requests use alarm-level focus (highest priority, non-interruptible).
        if (audioFocusManager.requestFocus(isAlert = request.isAlert)) {
            scheduler.submit(request)
        }
    }

    /**
     * Queries the offline capability of a specific language.
     */
    fun isLanguageAvailable(language: TtsLanguage): TtsLanguageCapability {
        return engine.isLanguageAvailable(language)
    }

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
