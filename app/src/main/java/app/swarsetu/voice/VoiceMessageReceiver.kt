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
        
        val isVoice = entity.messageType == MessageEntity.TYPE_TRANSLATED_VOICE ||
            entity.messageType == MessageEntity.TYPE_VOICE_NOTE ||
            entity.sourceText != null ||
            entity.voiceTextLanguage != null

        return scope.launch {
            val originalLanguage = (entity.sourceLanguage ?: entity.voiceTextLanguage ?: "en").lowercase()
            val preferredLanguage = settingsStore.sttLanguageCode.first().lowercase()

            var textToSpeak = entity.translatedText ?: entity.body
            var languageToSpeak = entity.targetLanguage ?: originalLanguage

            // If the message has not been translated to receiver's preferred language, translate now
            if (originalLanguage != preferredLanguage && (languageToSpeak != preferredLanguage || entity.translatedText == null)) {
                val inputToTranslate = entity.sourceText ?: entity.body
                if (inputToTranslate.isNotBlank()) {
                    val myName = try { settingsStore.displayName.first() } catch (_: Throwable) { null }
                    val translated = if (!myName.isNullOrBlank()) {
                        translatorEngine.translate(
                            text = inputToTranslate,
                            sourceLang = originalLanguage,
                            targetLang = preferredLanguage,
                            protectedNouns = listOf(myName),
                        )
                    } else {
                        translatorEngine.translate(
                            text = inputToTranslate,
                            sourceLang = originalLanguage,
                            targetLang = preferredLanguage,
                        )
                    }
                    if (translated.isNotBlank() && translated != inputToTranslate) {
                        textToSpeak = translated
                        languageToSpeak = preferredLanguage

                        // Update entity in DB with translated text for local display
                        val updatedEntity = entity.copy(
                            translatedText = textToSpeak,
                            targetLanguage = languageToSpeak,
                        )
                        messageRepository.save(updatedEntity)
                    } else {
                        textToSpeak = inputToTranslate
                        languageToSpeak = originalLanguage
                    }
                }
            }

            // Only speak aloud automatically if it was sent as a voice message/note
            if (isVoice) {
                val finalLanguage = parseLanguage(languageToSpeak)
                android.util.Log.d("VoiceMessageReceiver", "Auto-playing inbound voice message ${entity.id} in $finalLanguage: \"$textToSpeak\"")

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
    }

    private fun parseLanguage(languageName: String): TtsLanguage {
        return TtsLanguage.fromLanguageCode(languageName) ?: TtsLanguage.HINDI
    }
}
