package app.swarsetu.stt

/**
 * Simple energy-based Voice Activity Detector (VAD). Detects speech vs silence in PCM audio
 * by computing the short-time energy of each frame.
 *
 * Used by [PcmCapture.captureUntilSilence] and the STT pipeline to determine sentence boundaries
 * and pause points.
 *
 * **Algorithm:** Computes the root-mean-square (RMS) energy of each frame. When the energy drops
 * below [silenceThreshold] for [silenceFrames] consecutive frames, speech is considered ended.
 * When it rises above [speechThreshold] for [speechFrames] consecutive frames, speech is
 * considered started.
 *
 * **Calibration:** The thresholds are calibrated for 16-bit PCM at 16 kHz with the
 * `VOICE_RECOGNITION` audio source (no AGC). Real-world threshold tuning should be done on
 * target devices.
 *
 * **Not Knit/networking/TTS aware.** Pure signal processing.
 */
class VoiceActivityDetector(
    /** RMS energy below which a frame is considered silent. */
    private val silenceThreshold: Int = DEFAULT_SILENCE_THRESHOLD,

    /** RMS energy above which a frame is considered speech. */
    private val speechThreshold: Int = DEFAULT_SPEECH_THRESHOLD,

    /** Number of consecutive silent frames required to declare end of speech. */
    private val silenceFrames: Int = DEFAULT_SILENCE_FRAMES,

    /** Number of consecutive speech frames required to declare start of speech. */
    private val speechFrames: Int = DEFAULT_SPEECH_FRAMES,
) {

    /** Current VAD state. */
    enum class State {
        /** No speech detected. */
        SILENT,

        /** Speech is in progress. */
        SPEECH,
    }

    private var consecutiveSilent = 0
    private var consecutiveSpeech = 0
    private var _state = State.SILENT

    /** Current VAD state. */
    val state: State get() = _state

    /**
     * Process a PCM frame and update the VAD state. Returns the current state after processing.
     *
     * @param pcm 16-bit signed PCM samples (one frame, typically 10–30 ms worth).
     * @return Current VAD state after processing this frame.
     */
    fun processFrame(pcm: ShortArray): State {
        val energy = computeRmsEnergy(pcm)

        if (energy < silenceThreshold) {
            consecutiveSilent++
            consecutiveSpeech = 0
            if (consecutiveSilent >= silenceFrames && _state == State.SPEECH) {
                _state = State.SILENT
            }
        } else if (energy > speechThreshold) {
            consecutiveSpeech++
            consecutiveSilent = 0
            if (consecutiveSpeech >= speechFrames && _state == State.SILENT) {
                _state = State.SPEECH
            }
        }
        // Energy between thresholds: no state change (hysteresis)

        return _state
    }

    /**
     * Reset the VAD to its initial silent state. Call when starting a new utterance.
     */
    fun reset() {
        consecutiveSilent = 0
        consecutiveSpeech = 0
        _state = State.SILENT
    }

    /**
     * Compute the RMS energy of a PCM frame. Returns a value in [0, Short.MAX_VALUE].
     */
    private fun computeRmsEnergy(pcm: ShortArray): Int {
        if (pcm.isEmpty()) return 0
        var sumSquares = 0L
        for (sample in pcm) {
            val s = sample.toLong()
            sumSquares += s * s
        }
        return kotlin.math.sqrt(sumSquares.toDouble() / pcm.size).toInt()
    }

    companion object {
        /**
         * Default silence threshold. Calibrated for 16-bit PCM with VOICE_RECOGNITION source.
         * Below this, a frame is considered silence. Tune on target devices.
         */
        const val DEFAULT_SILENCE_THRESHOLD = 200

        /**
         * Default speech threshold. Above this, a frame is considered speech.
         * Set higher than silence threshold for hysteresis.
         */
        const val DEFAULT_SPEECH_THRESHOLD = 400

        /**
         * Number of consecutive silent frames (at 30ms/frame = ~600ms) to declare end of speech.
         */
        const val DEFAULT_SILENCE_FRAMES = 20

        /**
         * Number of consecutive speech frames to declare start of speech.
         * Prevents brief noise from triggering speech detection.
         */
        const val DEFAULT_SPEECH_FRAMES = 3
    }
}
