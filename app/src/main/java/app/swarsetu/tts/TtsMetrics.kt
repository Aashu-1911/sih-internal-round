package app.swarsetu.tts

/**
 * Core developer metrics collected during a TTS request.
 * Times are based on System.currentTimeMillis() or relative measurements.
 */
data class TtsMetrics(
    val requestId: String,
    val language: TtsLanguage,
    val voiceName: String?,
    val textLength: Int,
    /** Time when the synthesis officially began. */
    val synthesisBeginTimestampMs: Long? = null,
    /** Time to First Audio (TTFA): when the engine delivered the first synthesized audio chunk. */
    val firstAudioChunkTimestampMs: Long? = null,
    /** Time when playback through the speaker actually started. */
    val playbackStartTimestampMs: Long? = null,
    /** Time when the utterance successfully completed. */
    val completionTimestampMs: Long? = null,
    /** Total length of the generated audio in milliseconds (if measurable). */
    val totalAudioDurationMs: Long? = null,
    val interrupted: Boolean = false,
    val error: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Time-To-First-Audio (from synthesis begin). */
    val ttfaMs: Long?
        get() =
            if (firstAudioChunkTimestampMs != null && synthesisBeginTimestampMs != null) {
                firstAudioChunkTimestampMs - synthesisBeginTimestampMs
            } else {
                null
            }

    /** Playback Start Latency (from synthesis begin). */
    val playbackStartLatencyMs: Long?
        get() =
            if (playbackStartTimestampMs != null && synthesisBeginTimestampMs != null) {
                playbackStartTimestampMs - synthesisBeginTimestampMs
            } else {
                null
            }

    /** Real-Time Factor: Synthesis time / Audio duration. (Values < 1.0 mean faster than real-time). */
    val rtf: Float?
        get() =
            if (completionTimestampMs != null && synthesisBeginTimestampMs != null && totalAudioDurationMs != null &&
                totalAudioDurationMs > 0
            ) {
                val synthesisTime = completionTimestampMs - synthesisBeginTimestampMs
                synthesisTime.toFloat() / totalAudioDurationMs.toFloat()
            } else {
                null
            }
}
