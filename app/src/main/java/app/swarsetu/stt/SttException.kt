package app.swarsetu.stt

/**
 * Base exception for all STT subsystem errors. Implementations throw concrete subtypes; callers catch
 * [SttException] to handle any STT failure uniformly.
 *
 * Mirrors the project's moderation pattern: [org.tensorflow.lite.Interpreter] and asset reads can
 * throw [Exception], [Error], or [OutOfMemoryError]; the STT engine absorbs them behind [runCatching]
 * and maps them to these types so the application layer never sees raw JNI / OOM failures.
 */
sealed class SttException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** The model asset could not be read from assets, or the flatbuffer is corrupt/incompatible. */
    class ModelLoadError(
        val modelPath: String,
        cause: Throwable? = null,
    ) : SttException("Failed to load STT model: $modelPath", cause)

    /** The requested language is not supported by this engine or has no model bundled. */
    class UnsupportedLanguage(
        val language: SttLanguage,
    ) : SttException("Unsupported STT language: ${language.code}")

    /** An error occurred during speech-to-text inference. */
    class InferenceError(
        message: String = "STT inference failed",
        cause: Throwable? = null,
    ) : SttException(message, cause)

    /** The engine has not been initialized (model not loaded) or has been released. */
    class EngineNotReady(
        message: String = "STT engine is not ready",
    ) : SttException(message)

    /** Audio input is invalid: wrong sample rate, too short, empty, or malformed. */
    class AudioInputError(
        message: String = "Invalid audio input",
        cause: Throwable? = null,
    ) : SttException(message, cause)

    /** Out of memory while allocating model buffers or inference tensors. */
    class OutOfMemory(
        message: String = "Out of memory in STT engine",
        cause: Throwable? = null,
    ) : SttException(message, cause)
}
