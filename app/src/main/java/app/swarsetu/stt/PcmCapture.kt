package app.swarsetu.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Captures raw PCM audio from the microphone for STT input. Uses [AudioRecord] to produce
 * 16-bit signed PCM at 16 kHz mono — the format [SttEngine.transcribe] expects.
 *
 * **Microphone ownership:** This class is the sole owner of the [AudioRecord] for STT.
 * The existing [app.swarsetu.ui.voice.VoiceRecorder] uses [MediaRecorder] for voice-note
 * encoding — they operate on different Android subsystems and must not both hold the
 * microphone simultaneously.
 *
 * **Lifecycle:**
 * 1. Check [hasPermission] / [canCapture] before calling [start].
 * 2. Call [start] to open the microphone — returns false on any failure, never throws.
 * 3. Call [readChunk] repeatedly to get PCM buffers.
 * 4. Call [stop] to release the microphone (idempotent, exception-safe).
 *
 * **Thread safety:** Drive from a single coroutine scope (the SttPipeline scope).
 *
 * **No Knit/networking/TTS dependencies.** Pure Android audio capture.
 */
class PcmCapture(
    private val context: Context,
) {
    /** Current state of the capture. */
    enum class State {
        /** No capture in progress. Ready to [start]. */
        IDLE,
        /** Actively capturing audio from the microphone. */
        CAPTURING,
    }

    private var recorder: AudioRecord? = null
    private var _state = MutableStateFlow(State.IDLE)

    /** Live capture state. Observable by the UI for status indicators. */
    val state: StateFlow<State> = _state.asStateFlow()

    /** Number of samples captured so far during the current [start]→[stop] cycle. */
    var capturedSamples: Int = 0
        private set

    /** Duration of captured audio in milliseconds. */
    val capturedDurationMs: Long get() = capturedSamples * 1000L / SAMPLE_RATE

    /**
     * Whether the device has a microphone and the app has [Manifest.permission.RECORD_AUDIO].
     */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether capture can start (permission granted + not already capturing).
     */
    fun canCapture(): Boolean =
        hasPermission() && _state.value == State.IDLE

    /**
     * Start capturing PCM audio. Opens the microphone.
     *
     * This method is **fully defensive**: every step is exception-safe, resources are released
     * on any failure path, and state is only advanced after recording is confirmed.
     *
     * @return true if capture started successfully, false if the microphone couldn't be opened.
     *         Never throws.
     */
    fun start(): Boolean {
        Log.i(TAG, "[STT-DIAG-060] PcmCapture.start() called")
        SttTraceLogger.log("STT-020", "PcmCapture.start called")
        Log.i(TAG, "[STT-DIAG-061] canCapture=${canCapture()}, hasPermission=${hasPermission()}, state=${_state.value}")
        Log.i(TAG, "[STT-DIAG-062] Config: SAMPLE_RATE=$SAMPLE_RATE, CHANNEL_CONFIG=$CHANNEL_CONFIG, AUDIO_FORMAT=$AUDIO_FORMAT")

        if (!canCapture()) {
            Log.w(TAG, "[STT-DIAG-063] Cannot start: canCapture=false")
            return false
        }

        // 1. Validate buffer size
        Log.i(TAG, "[STT-DIAG-064] AudioRecord.getMinBufferSize($SAMPLE_RATE, $CHANNEL_CONFIG, $AUDIO_FORMAT)...")
        SttTraceLogger.log("STT-021", "AudioRecord.getMinBufferSize sampleRate=$SAMPLE_RATE channel=$CHANNEL_CONFIG format=$AUDIO_FORMAT")
        val bufferSize = try {
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        } catch (e: Throwable) {
            Log.e(TAG, "[STT-DIAG-065] getMinBufferSize FAILED: ${e.javaClass.simpleName}: ${e.message}")
            return false
        }
        Log.i(TAG, "[STT-DIAG-066] getMinBufferSize returned: $bufferSize")
        SttTraceLogger.log("STT-022", "getMinBufferSize returned=$bufferSize")
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "[STT-DIAG-067] Invalid buffer size: $bufferSize")
            return false
        }

        val actualBufferSize = bufferSize * 2
        Log.i(TAG, "[STT-DIAG-068] actualBufferSize=$actualBufferSize (2x min)\n")

        // 2. Create AudioRecord
        val sources = intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        var newRecorder: AudioRecord? = null
        var chosenSource = -1
        for (source in sources) {
            Log.i(TAG, "[STT-DIAG-069] new AudioRecord(source=$source, rate=$SAMPLE_RATE, channels=$CHANNEL_CONFIG, format=$AUDIO_FORMAT, buffer=$actualBufferSize)...")
            SttTraceLogger.log("STT-023", "AudioRecord create source=$source buffer=$actualBufferSize")
            try {
                newRecorder =
                    AudioRecord(
                        source,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        actualBufferSize,
                    )
                chosenSource = source
                Log.i(TAG, "[STT-DIAG-070] AudioRecord constructor returned OK for source=$source")
                SttTraceLogger.log("STT-024", "AudioRecord ctor ok source=$source")
                break
            } catch (e: SecurityException) {
                Log.e(TAG, "[STT-DIAG-071] AudioRecord CONSTRUCTOR SecurityException on source=$source: ${e.message}")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "[STT-DIAG-072] AudioRecord CONSTRUCTOR IllegalArgumentException on source=$source: ${e.message}")
            } catch (e: RuntimeException) {
                Log.e(TAG, "[STT-DIAG-073] AudioRecord CONSTRUCTOR RuntimeException on source=$source: ${e.javaClass.simpleName}: ${e.message}")
            } catch (e: Throwable) {
                Log.e(TAG, "[STT-DIAG-074] AudioRecord CONSTRUCTOR Throwable on source=$source: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        if (newRecorder == null) {
            Log.e(TAG, "[STT-DIAG-074b] AudioRecord could not be created with any source on ${Build.MANUFACTURER} ${Build.MODEL}")
            SttTraceLogger.log("STT-024E", "AudioRecord creation failed on all sources")
            return false
        }
        Log.i(TAG, "[STT-DIAG-074c] Using audio source=$chosenSource")

        // 3. Verify initialization
        val initState = newRecorder.state
        Log.i(TAG, "[STT-DIAG-075] AudioRecord.state = $initState (期望 ${AudioRecord.STATE_INITIALIZED})")
        SttTraceLogger.log("STT-025", "AudioRecord state=$initState")
        if (initState != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "[STT-DIAG-076] AudioRecord NOT initialized (state=$initState) — releasing and returning false")
            runCatching { newRecorder.release() }
            return false
        }
        Log.i(TAG, "[STT-DIAG-077] AudioRecord STATE_INITIALIZED confirmed")

        // 4. Start recording
        Log.i(TAG, "[STT-DIAG-078] AudioRecord.startRecording()...")
        SttTraceLogger.log("STT-026", "AudioRecord.startRecording")
        try {
            newRecorder.startRecording()
            Log.i(TAG, "[STT-DIAG-079] startRecording() returned OK")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "[STT-DIAG-080] startRecording() IllegalStateException: ${e.message}")
            runCatching { newRecorder.release() }
            return false
        } catch (e: RuntimeException) {
            Log.e(TAG, "[STT-DIAG-081] startRecording() RuntimeException: ${e.javaClass.simpleName}: ${e.message}")
            runCatching { newRecorder.release() }
            return false
        } catch (e: Throwable) {
            Log.e(TAG, "[STT-DIAG-082] startRecording() Throwable: ${e.javaClass.simpleName}: ${e.message}")
            runCatching { newRecorder.release() }
            return false
        }

        // 5. Verify recording actually started
        val recState = newRecorder.recordingState
        Log.i(TAG, "[STT-DIAG-083] AudioRecord.recordingState = $recState (期望 ${AudioRecord.RECORDSTATE_RECORDING})")
        SttTraceLogger.log("STT-027", "AudioRecord recordingState=$recState")
        if (recState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG, "[STT-DIAG-084] AudioRecord NOT recording (state=$recState) — releasing and returning false")
            runCatching { newRecorder.release() }
            return false
        }

        // 6. All checks passed
        recorder = newRecorder
        _state.value = State.CAPTURING
        capturedSamples = 0
        Log.i(TAG, "[STT-DIAG-085] PcmCapture.start() COMPLETE — mic is LIVE: ${SAMPLE_RATE}Hz, buffer=${actualBufferSize}B")
        SttTraceLogger.log("STT-028", "PcmCapture.start complete source=$chosenSource buffer=$actualBufferSize")
        return true
    }

    /**
     * Read a chunk of PCM samples from the microphone. Suspends until samples are available.
     *
     * @param maxSamples Maximum number of samples to read. The actual number read may be fewer.
     * @return PCM samples as a ShortArray, or null when capture is not active or read failed.
     */
    suspend fun readChunk(maxSamples: Int = SAMPLE_RATE / 10): ShortArray? =
        withContext(Dispatchers.IO) {
            val rec = recorder ?: return@withContext null
            if (_state.value != State.CAPTURING) return@withContext null

            val buffer = ShortArray(maxSamples)
            val read = try {
                rec.read(buffer, 0, maxSamples)
            } catch (e: Throwable) {
                Log.w(TAG, "AudioRecord read failed: ${e.javaClass.simpleName}: ${e.message}")
                return@withContext null
            }
            if (read <= 0) {
                Log.w(TAG, "AudioRecord read returned $read")
                return@withContext null
            }

            capturedSamples += read
            if (read == maxSamples) buffer else buffer.copyOf(read)
        }

    /**
     * Capture audio until silence is detected. Returns all captured PCM samples, or null on failure.
     *
     * @param silenceMs How long silence must persist before stopping. Default 1500ms.
     * @param maxDurationMs Maximum capture duration. Default 5 minutes.
     * @param onAmplitude Callback for live amplitude updates. Called on the capture thread.
     */
    suspend fun captureUntilSilence(
        silenceMs: Long = 1_500L,
        maxDurationMs: Long = 5 * 60 * 1_000L,
        onAmplitude: ((Float) -> Unit)? = null,
    ): ShortArray? = withContext(Dispatchers.IO) {
        val rec = recorder ?: return@withContext null
        if (_state.value != State.CAPTURING) return@withContext null

        val allSamples = mutableListOf<Short>()
        val silenceThreshold = SILENCE_THRESHOLD
        val samplesPerSilence = (SAMPLE_RATE * silenceMs / 1000).toInt()
        val samplesPerMax = (SAMPLE_RATE * maxDurationMs / 1000).toInt()
        val chunkSize = SAMPLE_RATE / 10

        var consecutiveSilentSamples = 0
        var totalSamples = 0

        while (totalSamples < samplesPerMax) {
            ensureActive()

            val buffer = ShortArray(chunkSize)
            val read = rec.read(buffer, 0, chunkSize)
            if (read <= 0) break

            var maxAmplitude = 0
            for (i in 0 until read) {
                val sample = kotlin.math.abs(buffer[i].toInt())
                if (sample > maxAmplitude) maxAmplitude = sample
            }
            val amplitude = (maxAmplitude.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f)
            onAmplitude?.invoke(amplitude)

            if (maxAmplitude < silenceThreshold) {
                consecutiveSilentSamples += read
                if (consecutiveSilentSamples >= samplesPerSilence && allSamples.isNotEmpty()) {
                    Log.d(TAG, "Silence detected after ${totalSamples * 1000L / SAMPLE_RATE}ms")
                    break
                }
            } else {
                consecutiveSilentSamples = 0
            }

            for (i in 0 until read) {
                allSamples.add(buffer[i])
            }
            totalSamples += read
        }

        if (allSamples.isEmpty()) {
            null
        } else {
            ShortArray(allSamples.size) { allSamples[it] }
        }
    }

    /**
     * Stop capturing and release the microphone. Idempotent and exception-safe.
     * Safe to call even if [start] was never called or already stopped.
     */
    fun stop() {
        if (_state.value == State.IDLE) return
        _state.value = State.IDLE
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        Log.d(TAG, "Capture stopped: $capturedSamples samples (${capturedDurationMs}ms)")
    }

    /**
     * Cancel an in-progress capture and discard all captured audio.
     * Idempotent and exception-safe.
     */
    fun cancel() {
        _state.value = State.IDLE
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        capturedSamples = 0
    }

    private companion object {
        const val TAG = "PcmCapture"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        /** Minimum amplitude to consider as speech. */
        const val SILENCE_THRESHOLD = 300
    }
}
