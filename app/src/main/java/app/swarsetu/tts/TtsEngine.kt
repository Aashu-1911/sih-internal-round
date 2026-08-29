package app.swarsetu.tts

import java.io.File

/**
 * An abstraction over the underlying text-to-speech mechanism.
 * This guarantees [TtsManager] is completely decoupled from Android's specific `TextToSpeech` API.
 */
interface TtsEngine {
    /**
     * Attempts to asynchronously initialize the TTS engine.
     * @return True if initialized successfully, False otherwise.
     */
    suspend fun initialize(): Boolean

    /**
     * Queries the offline availability of the specified [language].
     */
    fun isLanguageAvailable(language: TtsLanguage): TtsLanguageCapability

    /**
     * Executes the text-to-speech playback of [request] through the device speaker.
     */
    suspend fun speak(request: TtsRequest): TtsResult

    /**
     * Synthesizes the text-to-speech [request] and writes the PCM audio to the provided [outputFile].
     * Used for developer metrics, offline benchmarks, and test exports.
     */
    suspend fun synthesizeToFile(
        request: TtsRequest,
        outputFile: File,
    ): TtsResult

    /**
     * Interrupts and halts any ongoing synthesis or playback immediately.
     */
    fun stop()

    /**
     * Releases system resources, listeners, and the underlying engine.
     */
    fun release()
}
