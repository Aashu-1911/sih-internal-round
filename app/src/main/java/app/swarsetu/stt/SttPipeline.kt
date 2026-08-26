package app.swarsetu.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * End-to-end STT pipeline that ties together [PcmCapture], [VoiceActivityDetector], and
 * [SttEngine] into a single, easy-to-use API for the application layer.
 *
 * This is the primary integration point for Member 3 (UI) and Member 4 (Audio). The pipeline
 * handles:
 * - Microphone permission gating (via [PcmCapture.hasPermission])
 * - Live capture with VAD-based silence detection
 * - Automatic transcription of captured audio
 * - Partial result streaming during capture
 * - Language switching
 * - Model lifecycle
 *
 * **Usage:**
 * ```
 * // In ViewModel init
 * val pipeline = SttPipeline(context, engine)
 *
 * // Start capture → auto-transcribe when silence detected
 * pipeline.startCapture(language = SttLanguage.HINDI)
 * pipeline.results.collect { result ->
 *     // Update UI with transcription
 * }
 *
 * // Stop capture manually (or it stops on silence)
 * pipeline.stopCapture()
 * ```
 *
 * **Thread safety:** All public methods are safe to call from any coroutine.
 *
 * **No Knit/networking/TTS dependencies.** Pure audio→text pipeline.
 */
class SttPipeline(
    private val context: Context,
    private val engine: SttEngine,
) {

    /** Pipeline state. */
    enum class PipelineState {
        /** Idle — no capture in progress. */
        IDLE,

        /** Capturing audio from the microphone. */
        CAPTURING,

        /** Processing captured audio (transcription in progress). */
        PROCESSING,

        /** Transcription complete. Result available in [latestResult]. */
        COMPLETE,
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t ->
            Log.e(TAG, "Uncaught exception in STT scope: ${t.javaClass.simpleName}: ${t.message}", t)
            _state.value = PipelineState.IDLE
            _partialText.value = ""
            _amplitude.value = 0f
            runCatching { capture.stop() }
        }
    )
    private val mutex = Mutex()
    private val capture = PcmCapture(context)
    private val vad = VoiceActivityDetector()

    private var captureJob: Job? = null

    private val _state = MutableStateFlow(PipelineState.IDLE)
    /** Current pipeline state. */
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    /** Live partial transcription text during capture. Updated in real-time. */
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    /** Live microphone amplitude (0..1) during capture. For UI level meter. */
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _latestResult = MutableStateFlow<SttResult?>(null)
    /** The most recent completed transcription result. */
    val latestResult: StateFlow<SttResult?> = _latestResult.asStateFlow()

    /** Whether the microphone permission is granted. */
    val hasPermission: Boolean get() = capture.hasPermission()

    /** Whether capture can start. */
    val canCapture: Boolean get() = capture.canCapture()

    /**
     * Start capturing audio. The pipeline will:
     * 1. Open the microphone
     * 2. Stream PCM to the STT engine
     * 3. Emit partial results via [partialText]
     * 4. Auto-stop on silence detection
     * 5. Emit final result via [latestResult]
     *
     * @param language Language to transcribe.
     * @param silenceTimeoutMs How long silence persists before auto-stopping. 0 = manual stop only.
     */
    fun startCapture(
        language: SttLanguage,
        silenceTimeoutMs: Long = 2_000L,
    ) {
        if (_state.value != PipelineState.IDLE) {
            Log.w(TAG, "Already capturing/processing")
            return
        }
        if (!capture.canCapture()) {
            Log.w(TAG, "Cannot capture (permission: ${capture.hasPermission()})")
            return
        }

        _state.value = PipelineState.CAPTURING
        _partialText.value = ""
        _latestResult.value = null
        vad.reset()

        captureJob = scope.launch {
            try {
                mutex.withLock {
                    val started = withContext(Dispatchers.IO) { capture.start() }
                    if (!started) {
                        _state.value = PipelineState.IDLE
                        return@withLock
                    }

                    // Ensure engine is ready for this language
                    if (!engine.isReady || engine.currentLanguage != language) {
                        try {
                            engine.initialize(SttConfig(language = language))
                        } catch (e: Throwable) {
                            Log.e(TAG, "Engine init failed: ${e.javaClass.simpleName}: ${e.message}")
                            withContext(Dispatchers.IO) { capture.stop() }
                            _state.value = PipelineState.IDLE
                            return@withLock
                        }
                    }

                    captureAudioWithVad(language, silenceTimeoutMs)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Capture coroutine failed: ${e.javaClass.simpleName}: ${e.message}")
                runCatching { capture.stop() }
                _state.value = PipelineState.IDLE
                _partialText.value = ""
                _amplitude.value = 0f
            }
        }
    }

    /**
     * Stop capture manually. Transcription of the captured audio will proceed.
     */
    fun stopCapture() {
        if (_state.value != PipelineState.CAPTURING) return
        _state.value = PipelineState.IDLE
        capture.stop()
        captureJob?.cancel()
        captureJob = null
        _partialText.value = ""
        _amplitude.value = 0f
    }

    /**
     * Cancel capture and discard all audio. No transcription result will be produced.
     */
    fun cancelCapture() {
        capture.cancel()
        captureJob?.cancel()
        captureJob = null
        _state.value = PipelineState.IDLE
        _partialText.value = ""
        _amplitude.value = 0f
    }

    /**
     * Release all resources (engine, capture). Call from ViewModel.onCleared or app shutdown.
     */
    suspend fun release() {
        cancelCapture()
        scope.cancel()
        engine.release()
    }

    // --- Internal ---

    private suspend fun captureAudioWithVad(
        language: SttLanguage,
        silenceTimeoutMs: Long,
    ) {
        val allSamples = mutableListOf<Short>()
        var lastSpeechSample = 0
        val samplesPerSilenceTimeout = (language.sampleRate * silenceTimeoutMs / 1000).toInt()
        val frameSize = language.sampleRate / 33 // ~30ms frames for VAD

        // Emit a partial transcription every ~1 second of accumulated audio
        val partialIntervalSamples = language.sampleRate // 1 second worth of samples
        var samplesSinceLastPartial = 0

        while (_state.value == PipelineState.CAPTURING) {
            val chunk = withContext(Dispatchers.IO) {
                capture.readChunk(maxSamples = frameSize)
            }
            if (chunk == null) break

            // Update amplitude for UI
            var maxAmp = 0
            for (s in chunk) {
                val a = kotlin.math.abs(s.toInt())
                if (a > maxAmp) maxAmp = a
            }
            _amplitude.value = (maxAmp.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f)

            // VAD processing
            val vadState = vad.processFrame(chunk)
            if (vadState == VoiceActivityDetector.State.SPEECH) {
                lastSpeechSample = allSamples.size + chunk.size
            }

            // Accumulate
            for (s in chunk) allSamples.add(s)
            samplesSinceLastPartial += chunk.size

            // Emit partial transcription every ~1 second so the user sees live text
            if (samplesSinceLastPartial >= partialIntervalSamples && allSamples.size >= partialIntervalSamples) {
                samplesSinceLastPartial = 0
                try {
                    val partialPcm = ShortArray(allSamples.size) { allSamples[it] }
                    val partialResult = engine.transcribe(partialPcm, language)
                    if (partialResult.text.isNotBlank()) {
                        _partialText.value = partialResult.text
                        Log.d(TAG, "Partial: \"${partialResult.text}\"")
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Partial transcription failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }

            // Check if silence timeout reached after speech
            if (vadState == VoiceActivityDetector.State.SILENT && lastSpeechSample > 0) {
                val silenceSamples = allSamples.size - lastSpeechSample
                if (silenceSamples >= samplesPerSilenceTimeout) {
                    Log.d(TAG, "VAD silence timeout after ${allSamples.size * 1000L / language.sampleRate}ms")
                    break
                }
            }
        }

        // Capture complete — process the audio
        if (allSamples.isEmpty()) {
            _state.value = PipelineState.IDLE
            return
        }

        _state.value = PipelineState.PROCESSING
        withContext(Dispatchers.IO) { capture.stop() }

        val pcm = ShortArray(allSamples.size) { allSamples[it] }
        val result = try {
            engine.transcribe(pcm, language)
        } catch (e: Throwable) {
            Log.e(TAG, "Transcription failed: ${e.javaClass.simpleName}: ${e.message}")
            SttResult.empty(language)
        }

        _latestResult.value = result
        _partialText.value = result.text
        _state.value = PipelineState.COMPLETE
        Log.d(TAG, "Transcription: \"${result.text}\" (${result.type}, ${result.durationMs}ms)")

        // Auto-reset to IDLE after a brief delay so the pipeline can be reused.
        kotlinx.coroutines.delay(500)
        if (_state.value == PipelineState.COMPLETE) {
            _state.value = PipelineState.IDLE
        }
    }

    private companion object {
        const val TAG = "SttPipeline"
    }
}
