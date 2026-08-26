package app.swarsetu.voice

import app.swarsetu.stt.SttLanguage
import app.swarsetu.stt.SttPipeline
import app.swarsetu.stt.SttResult
import app.swarsetu.stt.SttTraceLogger
import app.swarsetu.tts.TtsLanguage
import app.swarsetu.tts.TtsManager
import app.swarsetu.tts.TtsPriority
import app.swarsetu.tts.TtsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * High-level conversational state mapping the STT and TTS lifecycle.
 */
enum class VoiceState {
    IDLE,
    LISTENING,
    RECOGNIZING,
    FINALIZING,
    SPEAKING,
    ERROR
}

data class VoicePipelineMetrics(
    val t0SpeechDetected: Long = 0,
    val t1SttFinal: Long = 0,
    val t2MessageConstructed: Long = 0,
    val t3MessageSendRequested: Long = 0,
    val t4RemoteMessageReceived: Long = 0,
    val t5TtsRequest: Long = 0,
    val t6FirstTtsAudio: Long = 0,
    val t7PlaybackStarted: Long = 0,
    val payloadSizeBytes: Int = 0,
    val messageId: String? = null
)

/**
 * Application-level contract to orchestrate STT and TTS without network dependencies.
 */
interface VoiceConversationController {
    val state: StateFlow<VoiceState>
    val voiceMetrics: StateFlow<VoicePipelineMetrics>
    var isLoopEnabled: Boolean
    var isMeshEnabled: Boolean

    fun startListening(language: SttLanguage)
    fun stopListening()
    fun stopSpeaking()
    fun shutdown()

    fun reportSttLatency(t0: Long, t1: Long)
    fun reportOutboundMessageMetrics(messageId: String, payloadSizeBytes: Int, t2: Long, t3: Long)
    fun reportInboundMessageMetrics(messageId: String, t4: Long, t5: Long)
}

class DefaultVoiceConversationController(
    private val scope: CoroutineScope,
    private val sttPipeline: SttPipeline,
    private val ttsManager: TtsManager
) : VoiceConversationController {

    private val _state = MutableStateFlow(VoiceState.IDLE)
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _voiceMetrics = MutableStateFlow(VoicePipelineMetrics())
    override val voiceMetrics: StateFlow<VoicePipelineMetrics> = _voiceMetrics.asStateFlow()

    override var isLoopEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) {
                stopSpeaking()
            }
        }

    override var isMeshEnabled: Boolean = false

    // Keep track of the last spoken result instance to deduplicate double-emissions
    private var lastSpokenResult: SttResult? = null

    init {
        // Observe STT pipeline state to update our higher-level VoiceState
        scope.launch {
            sttPipeline.state.collect { sttState ->
                val currentState = _state.value
                // Only update from STT if we are not currently SPEAKING
                if (currentState != VoiceState.SPEAKING) {
                    _state.value = when (sttState) {
                        SttPipeline.PipelineState.IDLE -> VoiceState.IDLE
                        SttPipeline.PipelineState.CAPTURING -> VoiceState.LISTENING
                        SttPipeline.PipelineState.PROCESSING -> VoiceState.RECOGNIZING
                        SttPipeline.PipelineState.COMPLETE -> VoiceState.FINALIZING
                    }
                }
            }
        }

        // Observe STT final results (Local loop only)
        scope.launch {
            sttPipeline.latestResult.collect { result ->
                if (result != null && isLoopEnabled) {
                    handleSttResult(result)
                }
            }
        }
    }

    private suspend fun handleSttResult(result: SttResult) {
        if (!result.isUsable) return
        
        // Deduplication: prevent speaking the exact same result object instance
        if (result === lastSpokenResult) return
        lastSpokenResult = result

        try {
            val ttsLang = result.language.toTtsLanguage()
            if (ttsLang == null) {
                _state.value = VoiceState.ERROR
                return
            }

            _state.value = VoiceState.SPEAKING
            
            val request = TtsRequest(
                requestId = UUID.randomUUID().toString(),
                text = result.text,
                language = ttsLang,
                priority = TtsPriority.NORMAL
            )
            
            ttsManager.speak(request)
            // Transition back to IDLE after queueing.
            // TODO: Listen to TtsMetricsCollector completion for accurate SPEAKING→IDLE transition.
            _state.value = VoiceState.IDLE
        } catch (e: Throwable) {
            android.util.Log.e("VoiceController", "Failed to handle STT result: ${e.javaClass.simpleName}: ${e.message}", e)
            _state.value = VoiceState.IDLE
        }
    }

    override fun startListening(language: SttLanguage) {
        SttTraceLogger.log("STT-090", "VoiceConversationController.startListening language=${language.code}")
        scope.launch {
            try {
                if (!sttPipeline.canCapture) {
                    SttTraceLogger.log("STT-091E", "VoiceConversationController canCapture=false language=${language.code}")
                    _state.value = VoiceState.ERROR
                    return@launch
                }
                SttTraceLogger.log("STT-091", "VoiceConversationController calling startCapture language=${language.code}")
                val result = sttPipeline.startCapture(language)
                SttTraceLogger.log("STT-092", "VoiceConversationController startCapture returned=$result language=${language.code}")
                if (result != SttPipeline.StartResult.STARTED) {
                    SttTraceLogger.log("STT-092E", "VoiceConversationController startCapture failed result=$result")
                    _state.value = VoiceState.ERROR
                }
            } catch (e: Throwable) {
                SttTraceLogger.error("STT-092E", "VoiceConversationController.startListening failed", e)
                _state.value = VoiceState.ERROR
            }
        }
    }

    override fun stopListening() {
        sttPipeline.stopCapture()
    }

    override fun stopSpeaking() {
        ttsManager.stopAll()
        if (_state.value == VoiceState.SPEAKING) {
            _state.value = VoiceState.IDLE
        }
    }

    override fun shutdown() {
        stopListening()
        stopSpeaking()
    }

    override fun reportSttLatency(t0: Long, t1: Long) {
        _voiceMetrics.update { it.copy(t0SpeechDetected = t0, t1SttFinal = t1) }
    }

    override fun reportOutboundMessageMetrics(messageId: String, payloadSizeBytes: Int, t2: Long, t3: Long) {
        _voiceMetrics.update { it.copy(
            messageId = messageId,
            payloadSizeBytes = payloadSizeBytes,
            t2MessageConstructed = t2,
            t3MessageSendRequested = t3
        )}
    }

    override fun reportInboundMessageMetrics(messageId: String, t4: Long, t5: Long) {
        _voiceMetrics.update { it.copy(
            messageId = messageId,
            t4RemoteMessageReceived = t4,
            t5TtsRequest = t5
        )}
    }
}

/**
 * Maps STT languages to their corresponding TTS language definitions.
 */
fun SttLanguage.toTtsLanguage(): TtsLanguage? {
    return when (this) {
        SttLanguage.HINDI -> TtsLanguage.HINDI
        SttLanguage.GUJARATI -> TtsLanguage.GUJARATI
        SttLanguage.MARATHI -> TtsLanguage.MARATHI
        SttLanguage.KANNADA -> TtsLanguage.KANNADA
        SttLanguage.MALAYALAM -> TtsLanguage.MALAYALAM
        SttLanguage.TAMIL -> TtsLanguage.TAMIL
        SttLanguage.TELUGU -> TtsLanguage.TELUGU
        SttLanguage.ODIA -> TtsLanguage.ODIA
        SttLanguage.BENGALI -> TtsLanguage.BENGALI
        SttLanguage.ENGLISH -> TtsLanguage.ENGLISH
    }
}

private const val SPEAKING_TIMEOUT_MS = 10_000L
