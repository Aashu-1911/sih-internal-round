package app.swarsetu.voice

import app.swarsetu.data.message.MessageEntity
import app.swarsetu.tts.TtsLanguage
import app.swarsetu.tts.TtsManager
import app.swarsetu.tts.TtsPriority
import app.swarsetu.tts.TtsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Intercepts incoming voice-text messages from the mesh layer (InboundPipeline) and routes them
 * to the TTS engine for playback.
 */
class VoiceMessageReceiver(
    private val ttsManager: TtsManager,
    private val scope: CoroutineScope,
    private val voiceController: VoiceConversationController,
) {
    fun onVoiceMessageReceived(entity: MessageEntity) {
        val t4 = System.currentTimeMillis()
        val languageString = entity.voiceTextLanguage ?: return
        val language = parseLanguage(languageString) ?: return

        val request = TtsRequest(
            requestId = entity.id,
            text = entity.body,
            language = language,
            priority = if (entity.isAlert) TtsPriority.ALERT else TtsPriority.NORMAL,
        )

        val t5 = System.currentTimeMillis()
        voiceController.reportInboundMessageMetrics(entity.id, t4, t5)

        scope.launch {
            ttsManager.speak(request)
        }
    }

    private fun parseLanguage(languageName: String): TtsLanguage? {
        return try {
            TtsLanguage.valueOf(languageName.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
