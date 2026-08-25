package app.swarsetu.tts

import java.util.Locale

/**
 * The specific ten Indian languages required for ISRO PS 26173 Phase 1 TTS.
 */
enum class TtsLanguage(val locale: Locale, val displayName: String) {
    HINDI(Locale("hi", "IN"), "Hindi"),
    GUJARATI(Locale("gu", "IN"), "Gujarati"),
    MARATHI(Locale("mr", "IN"), "Marathi"),
    KANNADA(Locale("kn", "IN"), "Kannada"),
    MALAYALAM(Locale("ml", "IN"), "Malayalam"),
    TAMIL(Locale("ta", "IN"), "Tamil"),
    TELUGU(Locale("te", "IN"), "Telugu"),
    ODIA(Locale("or", "IN"), "Odia"),
    BENGALI(Locale("bn", "IN"), "Bengali"),
    ENGLISH(Locale("en", "IN"), "English");

    companion object {
        /**
         * Resolves a [TtsLanguage] from a language code (e.g., "hi").
         */
        fun fromLanguageCode(code: String): TtsLanguage? =
            values().firstOrNull { it.locale.language == code }
    }
}
