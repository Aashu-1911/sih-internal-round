package app.swarsetu.voice

import app.swarsetu.data.MessageRepository
import app.swarsetu.data.message.MessageEntity
import app.swarsetu.data.settings.SettingsStore
import app.swarsetu.translation.TranslatorEngine
import app.swarsetu.tts.TtsLanguage
import app.swarsetu.tts.TtsManager
import app.swarsetu.tts.TtsPriority
import app.swarsetu.tts.TtsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Intercepts incoming voice-text messages from the mesh layer (InboundPipeline) and routes them
 * to the TTS engine for playback.
 */
class VoiceMessageReceiver(
    private val ttsManager: TtsManager,
    private val scope: CoroutineScope,
    private val voiceController: VoiceConversationController,
    private val settingsStore: SettingsStore,
    private val translatorEngine: TranslatorEngine,
    private val messageRepository: MessageRepository,
) {
    fun onVoiceMessageReceived(entity: MessageEntity) {
        val t4 = System.currentTimeMillis()
        val originalLanguageString = entity.voiceTextLanguage ?: return
        val originalLanguage = parseLanguage(originalLanguageString) ?: return

        scope.launch {
            // Determine the receiving user's preferred language.
            val preferredLanguageString = settingsStore.sttLanguageCode.first().uppercase()
            val preferredLanguage = parseLanguage(preferredLanguageString) ?: originalLanguage

            // If the incoming language differs from our preferred language, translate it.
            val (finalText, finalLanguage) = if (originalLanguage != preferredLanguage) {
                val translated = translatorEngine.translate(
                    text = entity.body,
                    sourceLang = originalLanguageString.lowercase(),
                    targetLang = preferredLanguageString.lowercase()
                )
                Pair(translated, preferredLanguage)
            } else {
                Pair(entity.body, originalLanguage)
            }

            // Update the message in the database with the translated text so the UI reflects it
            if (finalText != entity.body) {
                val updatedEntity = entity.copy(body = finalText)
                messageRepository.save(updatedEntity)
            }

            val request = TtsRequest(
                requestId = entity.id,
                text = finalText,
                language = finalLanguage,
                priority = if (entity.isAlert) TtsPriority.ALERT else TtsPriority.NORMAL,
            )

            val t5 = System.currentTimeMillis()
            voiceController.reportInboundMessageMetrics(entity.id, t4, t5)

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
