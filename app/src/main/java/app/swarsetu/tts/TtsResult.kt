package app.swarsetu.tts

/**
 * Result of a capability check for a specific TTS language.
 */
sealed class TtsLanguageCapability {
    /** The language and its voice data are fully available and ready to use offline. */
    data class Supported(
        val engineName: String,
        val voiceName: String?,
    ) : TtsLanguageCapability()

    /** The language is supported by the engine, but the voice data is missing and must be downloaded. */
    data class MissingData(
        val engineName: String,
    ) : TtsLanguageCapability()

    /** The language is not supported by the engine. */
    data class Unsupported(
        val reason: String,
    ) : TtsLanguageCapability()

    /** An error occurred checking the language state. */
    data class Error(
        val errorMessage: String,
    ) : TtsLanguageCapability()
}

/**
 * Result of a playback/synthesis operation.
 */
sealed class TtsResult {
    object Success : TtsResult()

    data class Interrupted(
        val reason: String = "Interrupted by another request or alert",
    ) : TtsResult()

    data class Error(
        val message: String,
    ) : TtsResult()
}
