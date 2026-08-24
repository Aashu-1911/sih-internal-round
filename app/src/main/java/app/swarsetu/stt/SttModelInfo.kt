package app.swarsetu.stt

/**
 * Metadata about an STT model for a specific language. Read from the model's asset directory at
 * load time; used by [SttModelManager] to decide which models to load, cache, and evict.
 *
 * All fields are immutable — the info is snapshot at load time and does not change.
 */
data class SttModelInfo(
    /** The language this model serves. */
    val language: SttLanguage,

    /** Absolute path or asset path to the model file(s). */
    val modelPath: String,

    /** Approximate model size in bytes, or -1 if unknown. Used for memory budget decisions. */
    val sizeBytes: Long = -1L,

    /** Whether the model file was successfully read and validated. */
    val available: Boolean = true,

    /**
     * Human-readable description of the model (e.g. "Vosk small hi", "Whisper tiny.en").
     * Used in diagnostics and logging.
     */
    val description: String = "",
) {
    val sizeMb: Double get() = if (sizeBytes > 0) sizeBytes / (1024.0 * 1024.0) else -1.0
}
