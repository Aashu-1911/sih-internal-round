package app.swarsetu.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [SttLanguage] — the language enum and its helpers. */
class SttLanguageTest {

    @Test
    fun `all ten target languages are declared`() {
        assertEquals(10, SttLanguage.entries.size)
    }

    @Test
    fun `all target languages have asset directories`() {
        // Every language must have a non-null assetDir — models are bundled per-language
        SttLanguage.entries.forEach { lang ->
            assertNotNull(
                "Language ${lang.code} must have an assetDir",
                lang.assetDir,
            )
        }
    }

    @Test
    fun `all languages use 16 kHz sample rate`() {
        SttLanguage.entries.forEach { lang ->
            assertEquals(
                "Language ${lang.code} must use 16 kHz",
                16_000,
                lang.sampleRate,
            )
        }
    }

    @Test
    fun `fromCode is case-insensitive`() {
        assertEquals(SttLanguage.HINDI, SttLanguage.fromCode("hi"))
        assertEquals(SttLanguage.HINDI, SttLanguage.fromCode("HI"))
        assertEquals(SttLanguage.HINDI, SttLanguage.fromCode("Hi"))
        assertEquals(SttLanguage.ENGLISH, SttLanguage.fromCode("en"))
        assertEquals(SttLanguage.ENGLISH, SttLanguage.fromCode("EN"))
    }

    @Test
    fun `fromCode returns null for unknown code`() {
        assertNull(SttLanguage.fromCode("xx"))
        assertNull(SttLanguage.fromCode(""))
        assertNull(SttLanguage.fromCode("fr"))
    }

    @Test
    fun `supported set is non-empty`() {
        assertTrue(SttLanguage.supported.isNotEmpty())
    }

    @Test
    fun `displayName is non-blank for all languages`() {
        SttLanguage.entries.forEach { lang ->
            assertTrue(
                "Language ${lang.code} must have a non-blank displayName",
                lang.displayName.isNotBlank(),
            )
        }
    }

    @Test
    fun `sample rate constant is 16000`() {
        assertEquals(16_000, SttLanguage.SAMPLE_RATE)
    }

    @Test
    fun `English is included in entries`() {
        assertNotNull(SttLanguage.fromCode("en"))
        assertEquals("English", SttLanguage.ENGLISH.displayName)
    }

    @Test
    fun `Hindi is included in entries`() {
        assertNotNull(SttLanguage.fromCode("hi"))
        assertEquals("हिन्दी", SttLanguage.HINDI.displayName)
    }
}
