package app.swarsetu.tts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.swarsetu.tts.TtsLanguage
import app.swarsetu.tts.TtsLanguageCapability
import app.swarsetu.tts.TtsManager
import app.swarsetu.tts.TtsPriority
import app.swarsetu.tts.TtsRequest
import app.swarsetu.tts.metrics.TtsMetricsCollector
import app.swarsetu.stt.SttLanguage
import app.swarsetu.voice.VoiceConversationController
import app.swarsetu.voice.VoiceMessageAdapter
import app.swarsetu.voice.toTtsLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class TtsTestUiState(
    val isInitialized: Boolean = false,
    val selectedLanguage: TtsLanguage = TtsLanguage.HINDI,
    val capability: TtsLanguageCapability? = null,
    val testText: String = ""
)

class TtsTestViewModel(
    private val ttsManager: TtsManager,
    val metricsCollector: TtsMetricsCollector,
    val voiceController: VoiceConversationController,
    private val voiceMessageAdapter: VoiceMessageAdapter
) : ViewModel() {

    private val _uiState = MutableStateFlow(TtsTestUiState())
    val uiState: StateFlow<TtsTestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val success = ttsManager.initialize()
            _uiState.update { it.copy(isInitialized = success) }
            checkCapability(_uiState.value.selectedLanguage)
        }
    }

    fun selectLanguage(language: TtsLanguage) {
        _uiState.update { it.copy(selectedLanguage = language) }
        checkCapability(language)
    }

    private fun checkCapability(language: TtsLanguage) {
        val capability = ttsManager.isLanguageAvailable(language)
        _uiState.update { it.copy(capability = capability) }
    }

    fun speakNormal(text: String) {
        viewModelScope.launch {
            val request = TtsRequest(
                requestId = UUID.randomUUID().toString(),
                text = text,
                language = _uiState.value.selectedLanguage,
                priority = TtsPriority.NORMAL
            )
            ttsManager.speak(request)
        }
    }

    fun speakAlert(text: String) {
        viewModelScope.launch {
            val request = TtsRequest(
                requestId = UUID.randomUUID().toString(),
                text = text,
                language = _uiState.value.selectedLanguage,
                priority = TtsPriority.ALERT
            )
            ttsManager.speak(request)
        }
    }

    fun stop() {
        ttsManager.stopAll()
        voiceController.isLoopEnabled = false
        voiceController.isMeshEnabled = false
        voiceController.stopListening()
        voiceMessageAdapter.stopVoiceMessage()
    }

    fun toggleLoop(enabled: Boolean) {
        voiceController.isLoopEnabled = enabled
        voiceController.isMeshEnabled = false // mutually exclusive for testing simplicity
        if (enabled) {
            val selectedTtsLang = _uiState.value.selectedLanguage
            val sttLang = SttLanguage.entries.firstOrNull { it.toTtsLanguage() == selectedTtsLang }
            if (sttLang != null) {
                voiceController.startListening(sttLang)
            } else {
                voiceController.isLoopEnabled = false
            }
        } else {
            voiceController.stopListening()
        }
    }

    fun startSttListening() {
        val selectedTtsLang = _uiState.value.selectedLanguage
        val sttLang = SttLanguage.entries.firstOrNull { it.toTtsLanguage() == selectedTtsLang } ?: SttLanguage.HINDI
        voiceController.startListening(sttLang)
    }

    fun stopSttListening() {
        voiceController.stopListening()
    }

    fun toggleMesh(enabled: Boolean) {
        voiceController.isMeshEnabled = enabled
        voiceController.isLoopEnabled = false // mutually exclusive
        if (enabled) {
            val selectedTtsLang = _uiState.value.selectedLanguage
            val sttLang = SttLanguage.entries.firstOrNull { it.toTtsLanguage() == selectedTtsLang }
            if (sttLang != null) {
                voiceMessageAdapter.startVoiceMessage(sttLang)
            } else {
                voiceController.isMeshEnabled = false
            }
        } else {
            voiceMessageAdapter.stopVoiceMessage()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stopAll()
        voiceController.shutdown()
        voiceMessageAdapter.stopVoiceMessage()
    }
}
