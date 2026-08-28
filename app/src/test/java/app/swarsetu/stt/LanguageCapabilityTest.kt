package app.swarsetu.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prompt 5: Language capability validation for all 10 PS 26173 target languages.
 *
 * This test verifies:
 * 1. All 10 languages are declared in [SttLanguage]
 * 2. Each language has correct BCP-47 code and display name
 * 3. Each language has an asset directory path
 * 4. Each language uses the correct sample rate
 * 5. The language enum covers all required languages
 * 6. [fromCode] resolves all language codes correctly
 * 7. Language switching via [SttConfig] works for all languages
 *
 * **Model availability testing** (whether a model file actually loads and produces inference)
 * requires Android Context and real model files. Those tests belong in androidTest, not here.
 * This test suite validates the STRUCTURAL contract — that the code correctly represents all
 * 10 languages. Model loading and inference are validated by the instrumented test suite.
 *
 * Language Capability Matrix (structural validation):
 *
 * | Language   | Code | DisplayName     | AssetDir | SampleRate | fromCode | Status   |
 * |------------|------|-----------------|----------|------------|----------|----------|
 * | Hindi      | hi   | हिन्दी           | stt-hi   | 16000      | ✓        | DECLARED |
 * | Gujarati   | gu   | ગુજરાતી          | stt-gu   | 16000      | ✓        | DECLARED |
 * | Marathi    | mr   | मराठी            | stt-mr   | 16000      | ✓        | DECLARED |
 * | Kannada    | kn   | ಕನ್ನಡ            | stt-kn   | 16000      | ✓        | DECLARED |
 * | Malayalam  | ml   | മലയാളം          | stt-ml   | 16000      | ✓        | DECLARED |
 * | Tamil      | ta   | தமிழ்            | stt-ta   | 16000      | ✓        | DECLARED |
 * | Telugu     | te   | తెలుగు           | stt-te   | 16000      | ✓        | DECLARED |
 * | Odia       | or   | ଓଡ଼ିଆ           | stt-or   | 16000      | ✓        | DECLARED |
 * | Bengali    | bn   | বাংলা            | stt-bn   | 16000      | ✓        | DECLARED |
 * | English    | en   | English         | stt-en   | 16000      | ✓        | DECLARED |
 *
 * Note: "DECLARED" means the language is structurally defined and has an asset directory.
 * "AVAILABLE" (model file present) and "INFERENCE" (model produces output) are tested in androidTest.
 */
class LanguageCapabilityTest {
    // --- 1. All 10 target languages are declared ---

    @Test
    fun `exactly 10 languages are declared`() {
        assertEquals(
            "PS 26173 requires exactly 10 languages",
            10,
            SttLanguage.entries.size,
        )
    }

    // --- 2. Each language has correct code and display name ---

    @Test
    fun `Hindi has correct metadata`() {
        val lang = SttLanguage.HINDI
        assertEquals("hi", lang.code)
        assertEquals("हिन्दी", lang.displayName)
        assertEquals("stt-hi", lang.assetDir)
    }

    @Test
    fun `Gujarati has correct metadata`() {
        val lang = SttLanguage.GUJARATI
        assertEquals("gu", lang.code)
        assertEquals("ગુજરાતી", lang.displayName)
        assertEquals("stt-gu", lang.assetDir)
    }

    @Test
    fun `Marathi has correct metadata`() {
        val lang = SttLanguage.MARATHI
        assertEquals("mr", lang.code)
        assertEquals("मराठी", lang.displayName)
        assertEquals("stt-mr", lang.assetDir)
    }

    @Test
    fun `Kannada has correct metadata`() {
        val lang = SttLanguage.KANNADA
        assertEquals("kn", lang.code)
        assertEquals("ಕನ್ನಡ", lang.displayName)
        assertEquals("stt-kn", lang.assetDir)
    }

    @Test
    fun `Malayalam has correct metadata`() {
        val lang = SttLanguage.MALAYALAM
        assertEquals("ml", lang.code)
        assertEquals("മലയാളം", lang.displayName)
        assertEquals("stt-ml", lang.assetDir)
    }

    @Test
    fun `Tamil has correct metadata`() {
        val lang = SttLanguage.TAMIL
        assertEquals("ta", lang.code)
        assertEquals("தமிழ்", lang.displayName)
        assertEquals("stt-ta", lang.assetDir)
    }

    @Test
    fun `Telugu has correct metadata`() {
        val lang = SttLanguage.TELUGU
        assertEquals("te", lang.code)
        assertEquals("తెలుగు", lang.displayName)
        assertEquals("stt-te", lang.assetDir)
    }

    @Test
    fun `Odia has correct metadata`() {
        val lang = SttLanguage.ODIA
        assertEquals("or", lang.code)
        assertEquals("ଓଡ଼ିଆ", lang.displayName)
        assertEquals("stt-or", lang.assetDir)
    }

    @Test
    fun `Bengali has correct metadata`() {
        val lang = SttLanguage.BENGALI
        assertEquals("bn", lang.code)
        assertEquals("বাংলা", lang.displayName)
        assertEquals("stt-bn", lang.assetDir)
    }

    @Test
    fun `English has correct metadata`() {
        val lang = SttLanguage.ENGLISH
        assertEquals("en", lang.code)
        assertEquals("English", lang.displayName)
        assertEquals("stt-en", lang.assetDir)
    }

    // --- 3. All languages have asset directories ---

    @Test
    fun `all languages have non-null asset directories`() {
        SttLanguage.entries.forEach { lang ->
            assertNotNull(
                "Language ${lang.code} (${lang.displayName}) must have an assetDir",
                lang.assetDir,
            )
            assertTrue(
                "Asset dir for ${lang.code} must not be blank",
                lang.assetDir!!.isNotBlank(),
            )
        }
    }

    // --- 4. All languages use 16 kHz ---

    @Test
    fun `all languages use 16kHz sample rate`() {
        SttLanguage.entries.forEach { lang ->
            assertEquals(
                "Language ${lang.code} must use 16kHz",
                16_000,
                lang.sampleRate,
            )
        }
    }

    // --- 5. fromCode resolves all language codes ---

    @Test
    fun `fromCode resolves all 10 language codes`() {
        val codes = listOf("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")
        codes.forEach { code ->
            val lang = SttLanguage.fromCode(code)
            assertNotNull("fromCode($code) must return a language", lang)
            assertEquals(code, lang!!.code)
        }
    }

    @Test
    fun `fromCode is case-insensitive for all codes`() {
        val testCases =
            listOf(
                "HI" to SttLanguage.HINDI,
                "Gu" to SttLanguage.GUJARATI,
                "MR" to SttLanguage.MARATHI,
                "Kn" to SttLanguage.KANNADA,
                "ML" to SttLanguage.MALAYALAM,
                "Ta" to SttLanguage.TAMIL,
                "TE" to SttLanguage.TELUGU,
                "Or" to SttLanguage.ODIA,
                "BN" to SttLanguage.BENGALI,
                "En" to SttLanguage.ENGLISH,
            )
        testCases.forEach { (code, expected) ->
            assertEquals("fromCode($code) should be $expected", expected, SttLanguage.fromCode(code))
        }
    }

    // --- 6. Language switching via SttConfig ---

    @Test
    fun `SttConfig supports all 10 languages`() {
        SttLanguage.entries.forEach { lang ->
            val config = SttConfig(language = lang)
            assertEquals("Config for ${lang.code}", lang, config.language)
        }
    }

    @Test
    fun `SttConfig language can be switched via copy`() {
        val config = SttConfig(language = SttLanguage.ENGLISH)
        SttLanguage.entries.forEach { lang ->
            val switched = config.copy(language = lang)
            assertEquals(lang, switched.language)
        }
    }

    // --- 7. SttResult carries language ---

    @Test
    fun `SttResult carries language for all 10 languages`() {
        SttLanguage.entries.forEach { lang ->
            val result = SttResult.empty(lang)
            assertEquals("Result for ${lang.code}", lang, result.language)
        }
    }

    // --- 8. Asset directory naming convention ---

    @Test
    fun `all asset directories follow stt- convention`() {
        SttLanguage.entries.forEach { lang ->
            assertTrue(
                "Asset dir for ${lang.code} must start with 'stt-'",
                lang.assetDir!!.startsWith("stt-"),
            )
            assertEquals(
                "Asset dir for ${lang.code} must be 'stt-{code}'",
                "stt-${lang.code}",
                lang.assetDir,
            )
        }
    }

    // --- 9. Unique language codes ---

    @Test
    fun `all language codes are unique`() {
        val codes = SttLanguage.entries.map { it.code }
        assertEquals("Language codes must be unique", codes.size, codes.toSet().size)
    }

    // --- 10. Supported set includes all declared languages ---

    @Test
    fun `supported set includes all declared languages`() {
        assertEquals(
            "All declared languages should be in supported set (all have assetDir)",
            SttLanguage.entries.toSet(),
            SttLanguage.supported,
        )
    }
}
