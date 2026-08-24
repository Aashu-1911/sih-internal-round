package app.swarsetu.stt

/**
 * Supported STT languages. Each entry maps a BCP-47 code to the language's display name, the expected
 * PCM sample rate (most Indic STT models expect 16 kHz), and a hint for the model manager about which
 * model asset to load.
 *
 * The 10 target languages match PS 26173. The order is deliberate: English is last because it is the
 * fallback when no Indic model is available, not because it is less important.
 *
 * A future version may add more languages here without changing any public API — the enum is the single
 * source of truth, and [SttLanguage.supported] exposes only the ones with bundled models.
 */
enum class SttLanguage(
    /** BCP-47 code (e.g. "hi", "en-IN"). */
    val code: String,

    /** Human-readable name for UI display. */
    val displayName: String,

    /**
     * Expected PCM sample rate in Hz. All target-language models use 16 kHz. Changing this per-language
     * would require a resampler; 16 kHz is the universal floor for speech recognition.
     */
    val sampleRate: Int = SAMPLE_RATE,

    /**
     * Asset directory name under `assets/stt/` where this language's model files live. A null value
     * means the language is declared but no model is bundled yet — [SttModelManager.isAvailable] will
     * return false for it.
     */
    val assetDir: String? = null,
) {
    HINDI("hi", "हिन्दी", assetDir = "stt-hi"),
    GUJARATI("gu", "ગુજરાતી", assetDir = "stt-gu"),
    MARATHI("mr", "मराठी", assetDir = "stt-mr"),
    KANNADA("kn", "ಕನ್ನಡ", assetDir = "stt-kn"),
    MALAYALAM("ml", "മലയാളം", assetDir = "stt-ml"),
    TAMIL("ta", "தமிழ்", assetDir = "stt-ta"),
    TELUGU("te", "తెలుగు", assetDir = "stt-te"),
    ODIA("or", "ଓଡ଼ିଆ", assetDir = "stt-or"),
    BENGALI("bn", "বাংলা", assetDir = "stt-bn"),
    ENGLISH("en", "English", assetDir = "stt-en"),

    ;

    companion object {
        /** Universal PCM sample rate for all STT models in this project. */
        const val SAMPLE_RATE = 16_000

        /**
         * Languages that have a bundled model (non-null [assetDir]). As models are added, this set
         * grows; the UI should present only these.
         */
        val supported: Set<SttLanguage> =
            entries.filter { it.assetDir != null }.toSet()

        /**
         * Resolve a language from its BCP-47 code, or null when the code is unknown.
         * Case-insensitive: "HI", "hi", "Hi" all map to [HINDI].
         */
        fun fromCode(code: String): SttLanguage? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
