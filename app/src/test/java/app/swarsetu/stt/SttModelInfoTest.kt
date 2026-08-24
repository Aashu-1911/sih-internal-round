package app.swarsetu.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [SttModelInfo] and [SttException]. */
class SttModelInfoTest {

    // --- SttModelInfo ---

    @Test
    fun `available model reports size correctly`() {
        val info = SttModelInfo(
            language = SttLanguage.ENGLISH,
            modelPath = "stt-en",
            sizeBytes = 50_000_000,
            available = true,
        )
        assertEquals(50_000_000L, info.sizeBytes)
        assertTrue(info.available)
        assertEquals(50_000_000.0 / (1024.0 * 1024.0), info.sizeMb, 0.1)
    }

    @Test
    fun `unavailable model has negative size`() {
        val info = SttModelInfo(
            language = SttLanguage.HINDI,
            modelPath = "stt-hi",
            available = false,
        )
        assertEquals(-1L, info.sizeBytes)
        assertFalse(info.available)
        assertEquals(-1.0, info.sizeMb, 0.001)
    }

    @Test
    fun `model info equality by data class`() {
        val a = SttModelInfo(SttLanguage.ENGLISH, "stt-en")
        val b = SttModelInfo(SttLanguage.ENGLISH, "stt-en")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `model info copy works`() {
        val original = SttModelInfo(
            language = SttLanguage.ENGLISH,
            modelPath = "stt-en",
            available = true,
        )
        val modified = original.copy(available = false)
        assertFalse(modified.available)
        assertEquals(original.language, modified.language)
    }

    // --- SttException ---

    @Test
    fun `ModelLoadError contains model path`() {
        val e = SttException.ModelLoadError("stt-en/model.tflite")
        assertTrue(e.message!!.contains("stt-en/model.tflite"))
        assertEquals("stt-en/model.tflite", e.modelPath)
    }

    @Test
    fun `UnsupportedLanguage contains language code`() {
        val e = SttException.UnsupportedLanguage(SttLanguage.HINDI)
        assertTrue(e.message!!.contains("hi"))
        assertEquals(SttLanguage.HINDI, e.language)
    }

    @Test
    fun `InferenceError has default message`() {
        val e = SttException.InferenceError()
        assertTrue(e.message!!.contains("inference"))
    }

    @Test
    fun `EngineNotReady has default message`() {
        val e = SttException.EngineNotReady()
        assertTrue(e.message!!.contains("not ready"))
    }

    @Test
    fun `AudioInputError can wrap cause`() {
        val cause = IllegalArgumentException("bad format")
        val e = SttException.AudioInputError(cause = cause)
        assertEquals(cause, e.cause)
    }

    @Test
    fun `OutOfMemory wraps cause`() {
        val cause = OutOfMemoryError("heap")
        val e = SttException.OutOfMemory(cause = cause)
        assertEquals(cause, e.cause)
    }

    @Test
    fun `all exceptions are SttException subclasses`() {
        val exceptions: List<SttException> = listOf(
            SttException.ModelLoadError("x"),
            SttException.UnsupportedLanguage(SttLanguage.ENGLISH),
            SttException.InferenceError(),
            SttException.EngineNotReady(),
            SttException.AudioInputError(),
            SttException.OutOfMemory(),
        )
        exceptions.forEach { e ->
            assertTrue("All should be SttException", e is SttException)
            assertTrue("All should be Exception", e is Exception)
        }
    }
}
