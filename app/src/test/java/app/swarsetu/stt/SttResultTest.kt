package app.swarsetu.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [SttResult] and [SttConfig]. */
class SttResultTest {
    // --- SttResult ---

    @Test
    fun `empty result is final with blank text`() {
        val result = SttResult.empty(SttLanguage.ENGLISH)
        assertEquals("", result.text)
        assertEquals(SttResultType.FINAL, result.type)
        assertEquals(SttLanguage.ENGLISH, result.language)
    }

    @Test
    fun `empty result is not usable`() {
        assertFalse(SttResult.empty(SttLanguage.ENGLISH).isUsable)
    }

    @Test
    fun `final result with text is usable`() {
        val result =
            SttResult(
                text = "hello world",
                type = SttResultType.FINAL,
                language = SttLanguage.ENGLISH,
            )
        assertTrue(result.isUsable)
    }

    @Test
    fun `partial result with text is not usable`() {
        val result =
            SttResult(
                text = "hello",
                type = SttResultType.PARTIAL,
                language = SttLanguage.ENGLISH,
            )
        assertFalse(result.isUsable)
    }

    @Test
    fun `final result with blank text is not usable`() {
        val result =
            SttResult(
                text = "  ",
                type = SttResultType.FINAL,
                language = SttLanguage.ENGLISH,
            )
        assertFalse(result.isUsable)
    }

    @Test
    fun `result confidence defaults to minus one`() {
        val result = SttResult.empty(SttLanguage.HINDI)
        assertEquals(-1f, result.confidence, 0.001f)
    }

    @Test
    fun `result duration defaults to zero`() {
        val result = SttResult.empty(SttLanguage.HINDI)
        assertEquals(0L, result.durationMs)
    }

    @Test
    fun `result copy preserves fields`() {
        val original =
            SttResult(
                text = "test",
                type = SttResultType.PARTIAL,
                language = SttLanguage.HINDI,
                confidence = 0.85f,
                durationMs = 100L,
            )
        val updated = original.copy(type = SttResultType.FINAL)
        assertEquals("test", updated.text)
        assertEquals(SttResultType.FINAL, updated.type)
        assertEquals(SttLanguage.HINDI, updated.language)
        assertEquals(0.85f, updated.confidence, 0.001f)
        assertEquals(100L, updated.durationMs)
    }

    // --- SttConfig ---

    @Test
    fun `default config has English and 16kHz`() {
        val config = SttConfig()
        assertEquals(SttLanguage.ENGLISH, config.language)
        assertEquals(16_000, config.sampleRate)
    }

    @Test
    fun `default config enables partial results`() {
        assertTrue(SttConfig().enablePartialResults)
    }

    @Test
    fun `default config has 2 second silence timeout`() {
        assertEquals(2_000L, SttConfig().silenceTimeoutMs)
    }

    @Test
    fun `default config has 5 minute max duration`() {
        assertEquals(5 * 60 * 1_000L, SttConfig().maxAudioDurationMs)
    }

    @Test
    fun `config with custom language`() {
        val config = SttConfig(language = SttLanguage.HINDI)
        assertEquals(SttLanguage.HINDI, config.language)
    }

    @Test
    fun `config copy works`() {
        val original = SttConfig()
        val modified =
            original.copy(
                language = SttLanguage.TAMIL,
                enablePartialResults = false,
            )
        assertEquals(SttLanguage.TAMIL, modified.language)
        assertFalse(modified.enablePartialResults)
        // Other fields unchanged
        assertEquals(original.sampleRate, modified.sampleRate)
        assertEquals(original.silenceTimeoutMs, modified.silenceTimeoutMs)
    }

    // --- SttResultType ---

    @Test
    fun `result type has partial and final`() {
        assertEquals(2, SttResultType.entries.size)
        assertTrue(SttResultType.entries.contains(SttResultType.PARTIAL))
        assertTrue(SttResultType.entries.contains(SttResultType.FINAL))
    }
}
