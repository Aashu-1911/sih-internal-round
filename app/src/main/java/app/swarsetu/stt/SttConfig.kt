package app.swarsetu.stt

/**
 * Configuration for an [SttEngine] instance. Immutable — create a new config to change settings.
 *
 * sensible defaults match the PS 26173 requirements: 16 kHz mono PCM, streaming-capable, all
 * supported languages.
 */
data class SttConfig(
    /**
     * The language to transcribe. Can be changed between transcriptions without reloading the model
     * when the engine supports it (a multilingual model). For single-language engines, changing this
     * triggers a model swap.
     */
    val language: SttLanguage = SttLanguage.ENGLISH,

    /** PCM sample rate in Hz. Must match the model's expected rate (16 kHz for all bundled models). */
    val sampleRate: Int = SttLanguage.SAMPLE_RATE,

    /**
     * Whether to emit partial (intermediate) transcription results as audio arrives. Useful for live
     * preview but costs slightly more CPU. Set to false for batch transcription of already-recorded
     * audio.
     */
    val enablePartialResults: Boolean = true,

    /**
     * Maximum number of partial results before the engine auto-finalizes. Prevents unbounded partial
     * output on very long recordings. 0 means no limit.
     */
    val maxPartialResults: Int = 0,

    /**
     * Silence duration in milliseconds after which the engine auto-finalizes the current utterance.
     * Only meaningful when [enablePartialResults] is true. 0 disables auto-finalization.
     */
    val silenceTimeoutMs: Long = 2_000L,

    /**
     * Maximum audio duration in milliseconds for a single transcription call. Protects against
     * runaway inference on very long inputs. 0 means no limit.
     */
    val maxAudioDurationMs: Long = 5 * 60 * 1_000L, // 5 minutes, matching VoiceRecorder.MAX_DURATION_MS

    /**
     * Whether the engine should attempt to detect the language automatically from the audio content.
     * When false, [language] is used exclusively. Only meaningful for multilingual models.
     */
    val autoDetectLanguage: Boolean = false,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(maxAudioDurationMs >= 0) { "maxAudioDurationMs must be non-negative" }
        require(silenceTimeoutMs >= 0) { "silenceTimeoutMs must be non-negative" }
    }
}
