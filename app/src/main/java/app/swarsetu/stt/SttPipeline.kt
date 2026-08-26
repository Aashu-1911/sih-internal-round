package app.swarsetu.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * End-to-end STT pipeline: microphone → PCM → VAD → engine transcription → result.
 *
 * **Lifecycle states:**
 * ```
 * IDLE → CAPTURING → PROCESSING → COMPLETE → IDLE
 * ```
 *
 * **startCapture** is a **suspend** function that opens the microphone before returning.
 * Callers can trust that if it returns [StartResult.STARTED], the mic is live.
 *
 * **stopCapture** signals the capture loop to finish gracefully and produce a final transcription.
 *
 * **cancelCapture** discards all captured audio and resets to IDLE.
 *
 * **Thread safety:** All public methods are safe to call from any coroutine.
 */
class SttPipeline(
    private val context: Context,
    private val engine: SttEngine,
) {
    init {
        SttTraceLogger.log("BOOT-STT-100", "SttPipeline ctor enter engine=${engine::class.qualifiedName}")
    }

    /** Result of [startCapture]. */
    enum class StartResult {
        /** Capture started successfully; the microphone is live. */
        STARTED,
        /** Microphone permission is not granted. */
        FAILED_PERMISSION,
        /** AudioRecord could not be created or started. */
        FAILED_AUDIO_RECORD,
        /** STT engine could not be initialized for the requested language. */
        FAILED_ENGINE,
        /** Pipeline is already capturing or processing — no action taken. */
        ALREADY_ACTIVE,
    }

    enum class PipelineState {
        IDLE,
        CAPTURING,
        PROCESSING,
        COMPLETE,
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t ->
            if (t is CancellationException) return@CoroutineExceptionHandler
            Log.e(TAG, "Uncaught exception in STT scope: ${t.javaClass.simpleName}: ${t.message}", t)
            SttTraceLogger.error("STT-100E", "uncaught exception in STT scope", t)
            _state.value = PipelineState.IDLE
            _partialText.value = ""
            _amplitude.value = 0f
            runCatching { capture.stop() }
        }
    )
    private val capture = PcmCapture(context)
    private val vad = VoiceActivityDetector()

    private var captureJob: Job? = null

    /** Flag set by [stopCapture] to signal the capture loop to finish gracefully. */
    @Volatile
    private var stopRequested = false

    private val _state = MutableStateFlow(PipelineState.IDLE)
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _latestResult = MutableStateFlow<SttResult?>(null)
    val latestResult: StateFlow<SttResult?> = _latestResult.asStateFlow()

    init {
        SttTraceLogger.log("BOOT-STT-101", "SttPipeline ctor after members capture=${capture::class.qualifiedName} vad=${vad::class.qualifiedName}")
        SttTraceLogger.log("BOOT-STT-102", "SttPipeline ctor exit")
    }

    val hasPermission: Boolean get() = capture.hasPermission()
    val canCapture: Boolean get() = capture.canCapture()

    /**
     * Start capturing audio. This is a **suspend** function that opens the microphone
     * before returning, so callers can trust the result.
     *
     * Returns [StartResult.STARTED] if the microphone is live and the capture loop is running.
     * Returns a failure code otherwise, with no state change.
     */
    suspend fun startCapture(
        language: SttLanguage,
        silenceTimeoutMs: Long = 2_000L,
    ): StartResult {
        Log.i(TAG, "[STT-DIAG-013] startCapture() called: lang=${language.code} (${language.displayName}), sampleRate=${language.sampleRate}")
        SttTraceLogger.log("STT-010", "startCapture lang=${language.code} name=${language.displayName} sampleRate=${language.sampleRate}")
        Log.i(TAG, "[STT-DIAG-013b] device ABI snapshot: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        Log.i(TAG, "[STT-DIAG-014] Pipeline state: ${_state.value}")
        Log.i(TAG, "[STT-DIAG-015] canCapture=${capture.canCapture()}, hasPermission=${capture.hasPermission()}, captureState=${capture.state.value}")
        Log.i(TAG, "[STT-DIAG-016] Engine: class=${engine::class.qualifiedName}, isReady=${engine.isReady}, currentLang=${engine.currentLanguage?.code}")
        SttTraceLogger.log("STT-011", "pipeline state=${_state.value} canCapture=${capture.canCapture()} hasPermission=${capture.hasPermission()} captureState=${capture.state.value} engine=${engine::class.qualifiedName}")

        if (_state.value != PipelineState.IDLE) {
            // Safety net: if the pipeline is stuck in a non-IDLE state from a previous
            // session (e.g. coroutine was cancelled without cleanup), force-reset it so
            // the user can record again without restarting the app.
            if (captureJob?.isActive != true) {
                Log.w(TAG, "[STT-DIAG-017a] Pipeline stuck in ${_state.value} but no active job — force-resetting")
                SttTraceLogger.log("STT-011A", "force reset from stuck state=${_state.value}")
                _state.value = PipelineState.IDLE
                _partialText.value = ""
                _amplitude.value = 0f
                runCatching { capture.stop() }
            } else {
                Log.w(TAG, "[STT-DIAG-017] Already active — returning ALREADY_ACTIVE")
                return StartResult.ALREADY_ACTIVE
            }
        }
        if (!capture.canCapture()) {
            Log.w(TAG, "[STT-DIAG-018] canCapture=false — hasPermission=${capture.hasPermission()}, captureState=${capture.state.value}")
            SttTraceLogger.log("STT-011B", "canCapture=false hasPerm=${capture.hasPermission()} captureState=${capture.state.value}")
            // If permission is granted but capture state is stuck, force-reset the capture
            if (capture.hasPermission() && capture.state.value != PcmCapture.State.IDLE) {
                Log.w(TAG, "[STT-DIAG-018a] Capture state stuck at ${capture.state.value} — force-stopping")
                runCatching { capture.stop() }
            }
            return StartResult.FAILED_PERMISSION
        }

        // Step 1: Open microphone
        Log.i(TAG, "[STT-DIAG-019] Calling PcmCapture.start() on IO dispatcher...")
        SttTraceLogger.log("STT-012", "calling PcmCapture.start()")
        val micStarted = withContext(Dispatchers.IO) { capture.start() }
        Log.i(TAG, "[STT-DIAG-090] PcmCapture.start() returned: $micStarted")
        SttTraceLogger.log("STT-013", "PcmCapture.start() returned=$micStarted")
        if (!micStarted) {
            Log.e(TAG, "[STT-DIAG-091] AudioRecord failed to start — returning FAILED_AUDIO_RECORD")
            SttTraceLogger.log("STT-013E", "AudioRecord failed to start")
            return StartResult.FAILED_AUDIO_RECORD
        }

        // Step 2: Initialize STT engine
        Log.i(TAG, "[STT-DIAG-092] Engine needs init? isReady=${engine.isReady}, currentLang=${engine.currentLanguage?.code}, requested=${language.code}")
        if (!engine.isReady || engine.currentLanguage != language) {
            Log.i(TAG, "[STT-DIAG-093] Calling engine.initialize(SttConfig(language=${language.code}))...")
            SttTraceLogger.log("STT-014", "engine.initialize language=${language.code}")
            try {
                engine.initialize(SttConfig(language = language))
                Log.i(TAG, "[STT-DIAG-094] engine.initialize() SUCCEEDED: isReady=${engine.isReady}")
                SttTraceLogger.log("STT-015", "engine.initialize ok ready=${engine.isReady}")
            } catch (e: Throwable) {
                Log.e(TAG, "[STT-DIAG-095] engine.initialize() FAILED: ${e.javaClass.simpleName}: ${e.message}")
                SttTraceLogger.error("STT-015E", "engine.initialize failed", e)
                withContext(Dispatchers.IO) { capture.stop() }
                return StartResult.FAILED_ENGINE
            }
        } else {
            Log.i(TAG, "[STT-DIAG-096] Engine already ready for ${language.code} — skipping init")
            SttTraceLogger.log("STT-016", "engine already ready for ${language.code}")
        }

        // Step 3: Launch capture loop
        Log.i(TAG, "[STT-DIAG-097] Launching captureAudioWithVad coroutine...")
        SttTraceLogger.log("STT-017", "launch captureAudioWithVad")
        stopRequested = false
        _state.value = PipelineState.CAPTURING
        _partialText.value = ""
        _latestResult.value = null
        _amplitude.value = 0f
        vad.reset()

        captureJob = scope.launch {
            try {
                captureAudioWithVad(language, silenceTimeoutMs)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    Log.d(TAG, "[STT-DIAG-098] Capture coroutine cancelled")
                    _state.value = PipelineState.IDLE
                    _partialText.value = ""
                    _amplitude.value = 0f
                    runCatching { capture.stop() }
                    return@launch
                }
                Log.e(TAG, "[STT-DIAG-099] Capture coroutine FAILED: ${e.javaClass.simpleName}: ${e.message}")
                SttTraceLogger.error("STT-018E", "capture coroutine failed", e)
                runCatching { capture.stop() }
                _state.value = PipelineState.IDLE
                _partialText.value = ""
                _amplitude.value = 0f
            }
        }

        Log.i(TAG, "[STT-DIAG-100] startCapture() COMPLETE — returning STARTED for ${language.code}")
        SttTraceLogger.log("STT-019", "startCapture complete -> STARTED")
        return StartResult.STARTED
    }

    /**
     * Stop capture gracefully. Signals the capture loop to finish, which will process
     * the final transcription and publish the result before going back to IDLE.
     *
     * Idempotent: safe to call multiple times or when already stopped.
     */
    fun stopCapture() {
        val currentState = _state.value
        if (currentState != PipelineState.CAPTURING) {
            Log.d(TAG, "stopCapture: not capturing (state=$currentState)")
            return
        }
        stopRequested = true
        capture.stop()
        Log.d(TAG, "stopCapture: stop requested")
    }

    /**
     * Cancel capture and discard all audio. No transcription result will be produced.
     * Idempotent.
     */
    fun cancelCapture() {
        stopRequested = true
        runCatching { capture.cancel() }
        captureJob?.cancel()
        captureJob = null
        _state.value = PipelineState.IDLE
        _partialText.value = ""
        _amplitude.value = 0f
    }

    /**
     * Release all resources. Call from ViewModel.onCleared or app shutdown.
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
        try {
        val allSamples = mutableListOf<Short>()
        var lastSpeechSample = 0
        val samplesPerSilenceTimeout = (language.sampleRate * silenceTimeoutMs / 1000).toInt()
        val frameSize = language.sampleRate / 33 // ~30ms frames for VAD

        val partialIntervalSamples = language.sampleRate
        var samplesSinceLastPartial = 0

        while (!stopRequested && _state.value == PipelineState.CAPTURING) {
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

            // Emit partial transcription every ~1 second
            if (samplesSinceLastPartial >= partialIntervalSamples && allSamples.size >= partialIntervalSamples) {
                samplesSinceLastPartial = 0
                try {
                    SttTraceLogger.log("STT-018", "partial transcribe start samples=${allSamples.size}")
                    val partialPcm = ShortArray(allSamples.size) { allSamples[it] }
                    val partialResult = engine.transcribe(partialPcm, language)
                    SttTraceLogger.log("STT-018A", "partial transcribe done textLen=${partialResult.text.length}")
                    if (partialResult.text.isNotBlank()) {
                        _partialText.value = partialResult.text
                        Log.d(TAG, "Partial: \"${partialResult.text}\"")
                    }
                } catch (e: Throwable) {
                    SttTraceLogger.error("STT-018E", "partial transcribe failed", e)
                    Log.w(TAG, "Partial transcription failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }

            // Auto-stop on silence after speech
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
            Log.d(TAG, "No audio captured")
            _state.value = PipelineState.IDLE
            _amplitude.value = 0f
            return
        }

        _state.value = PipelineState.PROCESSING
        withContext(Dispatchers.IO) { capture.stop() }

        val pcm = ShortArray(allSamples.size) { allSamples[it] }
        val result = try {
            SttTraceLogger.log("STT-019A", "final transcribe start samples=${pcm.size}")
            engine.transcribe(pcm, language)
        } catch (e: Throwable) {
            SttTraceLogger.error("STT-019E", "final transcribe failed", e)
            Log.e(TAG, "Transcription failed: ${e.javaClass.simpleName}: ${e.message}")
            SttResult.empty(language)
        }
        SttTraceLogger.log("STT-019B", "final transcribe done textLen=${result.text.length}")

        _latestResult.value = result
        _partialText.value = result.text
        _amplitude.value = 0f
        _state.value = PipelineState.COMPLETE
        Log.d(TAG, "Transcription: \"${result.text}\" (${result.type}, ${result.durationMs}ms)")

        // Auto-reset to IDLE so the pipeline can be reused
        delay(500)
        if (_state.value == PipelineState.COMPLETE) {
            _state.value = PipelineState.IDLE
        }
        } catch (t: Throwable) {
            SttTraceLogger.error("STT-020E", "captureAudioWithVad failed", t)
            runCatching { capture.stop() }
            _state.value = PipelineState.IDLE
            _partialText.value = ""
            _latestResult.value = SttResult.empty(language)
            _amplitude.value = 0f
        }
    }

    private companion object {
        const val TAG = "SttPipeline"
    }
}
