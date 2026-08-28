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
import kotlinx.coroutines.Job
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
    fun onVoiceMessageReceived(entity: MessageEntity): Job {
        val t4 = System.currentTimeMillis()
        
        if (entity.messageType != MessageEntity.TYPE_TRANSLATED_VOICE) {
            return scope.launch { }
        }

        return scope.launch {
            // Determine the receiving user's preferred language.
            val preferredLanguageString = settingsStore.sttLanguageCode.first().lowercase()
            val normalizedPreferred = translatorEngine.normalizeToLanguageTag(preferredLanguageString) ?: preferredLanguageString

            var textToSpeak = entity.translatedText ?: entity.body
            var languageToSpeak = entity.targetLanguage ?: entity.sourceLanguage ?: "en"
            val normalizedTarget = translatorEngine.normalizeToLanguageTag(languageToSpeak) ?: languageToSpeak

            // If the message has not been translated to receiver's preferred language, translate now
            if (normalizedTarget != normalizedPreferred || entity.translatedText == null) {
                val sourceLang = translatorEngine.normalizeToLanguageTag(entity.sourceLanguage ?: "en") ?: "en"
                if (sourceLang != normalizedPreferred) {
                    val inputToTranslate = entity.sourceText ?: entity.body
                    if (inputToTranslate.isNotBlank()) {
                        val translated = translatorEngine.translate(
                            text = inputToTranslate,
                            sourceLang = sourceLang,
                            targetLang = normalizedPreferred,
                        )
                        if (translated.isNotBlank()) {
                            textToSpeak = translated
                            languageToSpeak = normalizedPreferred

                            // Update entity in DB with translated text for local display
                            val updatedEntity = entity.copy(
                                translatedText = textToSpeak,
                                targetLanguage = languageToSpeak,
                            )
                            messageRepository.save(updatedEntity)
                        }
                    }
                }
            }

            val finalLanguage = parseLanguage(languageToSpeak)
            android.util.Log.d("VoiceMessageReceiver", "TTS playing inbound voice message ${entity.id} in $finalLanguage: \"$textToSpeak\"")

            val request =
                TtsRequest(
                    requestId = entity.id,
                    text = textToSpeak,
                    language = finalLanguage,
                    priority = if (entity.isAlert) TtsPriority.ALERT else TtsPriority.NORMAL,
                )

            val t5 = System.currentTimeMillis()
            voiceController.reportInboundMessageMetrics(entity.id, t4, t5)

            ttsManager.speak(request)
        }
    }

    private fun parseLanguage(languageName: String): TtsLanguage {
        return TtsLanguage.fromLanguageCode(languageName) ?: TtsLanguage.HINDI
    }
}
