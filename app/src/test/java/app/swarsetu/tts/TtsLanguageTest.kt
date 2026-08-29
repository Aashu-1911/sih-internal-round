package app.swarsetu.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsLanguageTest {
    @Test
    fun `test all required ISRO languages are defined`() {
        assertEquals(10, TtsLanguage.values().size)
    }

    @Test
    fun `test fromLanguageCode returns correct enum`() {
        assertEquals(TtsLanguage.HINDI, TtsLanguage.fromLanguageCode("hi"))
        assertEquals(TtsLanguage.TAMIL, TtsLanguage.fromLanguageCode("ta"))
        assertEquals(TtsLanguage.ENGLISH, TtsLanguage.fromLanguageCode("en"))
    }

    @Test
    fun `test fromLanguageCode returns null for unknown code`() {
        assertNull(TtsLanguage.fromLanguageCode("unknown"))
        assertNull(TtsLanguage.fromLanguageCode("fr"))
    }
}
