package app.swarsetu.tts

/**
 * Priorities for TTS playback execution.
 */
enum class TtsPriority {
    /**
     * Normal message. Plays in the order received.
     */
    NORMAL,

    /**
     * Alert message. Preempts normal playback immediately.
     */
    ALERT,
}

/**
 * A request to synthesize or play text via the TTS engine.
 */
data class TtsRequest(
    val requestId: String,
    val text: String,
    val language: TtsLanguage,
    val priority: TtsPriority = TtsPriority.NORMAL,
    val createdAtMs: Long = System.currentTimeMillis(),
    val isAlert: Boolean = priority == TtsPriority.ALERT,
    /** Optional identifier passed to the Android TTS engine. Defaults to requestId if null. */
    val utteranceId: String? = null,
)
