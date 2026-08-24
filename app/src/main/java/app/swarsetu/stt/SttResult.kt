package app.swarsetu.stt

/**
 * Whether a transcription result is partial (still being refined as audio arrives) or final (the engine
 * has committed to this transcription for the processed audio segment).
 *
 * Partial results are useful for live preview in a recording UI; final results are what gets stored on
 * a [app.swarsetu.data.message.MessageEntity] and sent over the mesh.
 */
enum class SttResultType {
    /** Intermediate transcription, subject to change as more audio is processed. */
    PARTIAL,

    /** Committed transcription for a completed audio segment. */
    FINAL,
}

/**
 * Result of a speech-to-text transcription. Immutable value class following the project's data-class
 * conventions ([app.swarsetu.moderation.TextVerdict], [app.swarsetu.moderation.ImageVerdict]).
 *
 * [text] is the transcribed speech. For [SttResultType.PARTIAL] it may be incomplete or change;
 * for [SttResultType.FINAL] it is the engine's committed transcription.
 *
 * [confidence] is the engine's overall confidence in `[0, 1]` — 1.0 when the engine is certain, 0.0
 * when it is guessing. Some engines may not provide this; they return -1f (unknown).
 *
 * [language] records which language the engine used for this transcription, which is important when
 * the user can switch languages mid-session.
 *
 * [durationMs] is the duration of audio (in milliseconds) that produced this result. Useful for
 * computing real-time factor (RTF) in benchmarks.
 */
data class SttResult(
    val text: String,
    val type: SttResultType,
    val language: SttLanguage,
    val confidence: Float = -1f,
    val durationMs: Long = 0L,
) {
    /** True when this is a final result with non-empty text. */
    val isUsable: Boolean get() = type == SttResultType.FINAL && text.isNotBlank()

    companion object {
        /** An empty result — returned on silence or when the engine produces nothing. */
        fun empty(language: SttLanguage) = SttResult(
            text = "",
            type = SttResultType.FINAL,
            language = language,
        )
    }
}
