package app.swarsetu.stt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SttEngine] interface contract and behavioral expectations.
 *
 * Uses an anonymous [SttEngine] implementation that mirrors [DefaultSttEngine]'s contract without
 * requiring Android Context or TFLite. Tests verify:
 * - Initial state (not ready, no config)
 * - Graceful degradation (empty results when no model)
 * - PCM validation (empty buffers)
 * - Result types and language propagation
 * - Release and re-initialize lifecycle
 * - Streaming result contract
 *
 * The real [DefaultSttEngine] is tested in androidTest with actual assets/Context.
 */
class DefaultSttEngineTest {

    /**
     * Minimal [SttEngine] implementation for testing the contract. Mirrors [DefaultSttEngine]'s
     * state machine without any Android dependencies.
     */
    private class MockSttEngine : SttEngine {
        private var _config: SttConfig? = null
        private var _language: SttLanguage? = null

        override val config: SttConfig? get() = _config
        override val isReady: Boolean get() = _config != null
        override val currentLanguage: SttLanguage? get() = _language

        override suspend fun initialize(config: SttConfig) {
            _config = config
            _language = config.language
        }

        override suspend fun setLanguage(language: SttLanguage) {
            _language = language
            _config = _config?.copy(language = language)
        }

        override suspend fun transcribe(pcm: ShortArray, language: SttLanguage): SttResult {
            if (_config == null) return SttResult.empty(language)
            if (pcm.isEmpty()) return SttResult.empty(language)
            return SttResult.empty(language)
        }

        override fun transcribeStream(pcm: ShortArray, language: SttLanguage): Flow<SttResult> =
            flow {
                emit(transcribe(pcm, language).copy(type = SttResultType.FINAL))
            }

        override suspend fun release() {
            _config = null
            _language = null
        }
    }

    @Test
    fun `engine starts not ready`() {
        val engine = MockSttEngine()
        assertFalse(engine.isReady)
        assertNull(engine.config)
        assertNull(engine.currentLanguage)
    }

    @Test
    fun `initialize makes engine ready`() = runTest {
        val engine = MockSttEngine()
        engine.initialize(SttConfig(language = SttLanguage.ENGLISH))
        assertTrue(engine.isReady)
        assertNotNull(engine.config)
        assertEquals(SttLanguage.ENGLISH, engine.currentLanguage)
    }

    @Test
    fun `initialize with different language switches language`() = runTest {
        val engine = MockSttEngine()
        engine.initialize(SttConfig(language = SttLanguage.ENGLISH))
        assertEquals(SttLanguage.ENGLISH, engine.currentLanguage)

        engine.setLanguage(SttLanguage.HINDI)
        assertEquals(SttLanguage.HINDI, engine.currentLanguage)
    }

    @Test
    fun `transcribe on uninitialized engine returns empty`() = runTest {
        val engine = MockSttEngine()
        val result = engine.transcribe(
            pcm = ShortArray(16000),
            language = SttLanguage.ENGLISH,
        )
        assertEquals("", result.text)
        assertEquals(SttResultType.FINAL, result.type)
    }

    @Test
    fun `transcribe with empty PCM returns empty`() = runTest {
        val engine = MockSttEngine()
        engine.initialize(SttConfig(language = SttLanguage.ENGLISH))

        val result = engine.transcribe(
            pcm = ShortArray(0),
            language = SttLanguage.ENGLISH,
        )
        assertEquals("", result.text)
    }

    @Test
    fun `transcribe propagates language to result`() = runTest {
        val engine = MockSttEngine()
        engine.initialize(SttConfig(language = SttLanguage.HINDI))

        val result = engine.transcribe(
            pcm = ShortArray(16000),
            language = SttLanguage.HINDI,
        )
        assertEquals(SttLanguage.HINDI, result.language)
    }

    @Test
    fun `release makes engine not ready`() = runTest {
        val engine = MockSttEngine()
        engine.initialize(SttConfig(language = SttLanguage.ENGLISH))
        assertTrue(engine.isReady)

        engine.release()
        assertFalse(engine.isReady)
        assertNull(engine.config)
        assertNull(engine.currentLanguage)
    }

    @Test
    fun `release is idempotent`() = runTest {
        val engine = MockSttEngine()
        engine.initialize(SttConfig(language = SttLanguage.ENGLISH))
        engine.release()
        engine.release() // Should not throw
        assertFalse(engine.isReady)
    }

    @Test
    fun `initialize after release re-initializes`() = runTest {
        val engine = MockSttEngine()
        engine.initialize(SttConfig(language = SttLanguage.ENGLISH))
        engine.release()
        assertFalse(engine.isReady)

        engine.initialize(SttConfig(language = SttLanguage.HINDI))
        assertTrue(engine.isReady)
        assertEquals(SttLanguage.HINDI, engine.currentLanguage)
    }

    @Test
    fun `transcribeStream emits at least one final result`() = runTest {
        val engine = MockSttEngine()
        engine.initialize(SttConfig(language = SttLanguage.ENGLISH))

        val results = mutableListOf<SttResult>()
        engine.transcribeStream(
            pcm = ShortArray(16000),
            language = SttLanguage.ENGLISH,
        ).collect { results.add(it) }

        assertTrue("Should emit at least one result", results.isNotEmpty())
        assertEquals(
            "Last result should be FINAL",
            SttResultType.FINAL,
            results.last().type,
        )
    }

    @Test
    fun `transcribeStream on uninitialized engine emits empty final`() = runTest {
        val engine = MockSttEngine()

        val results = mutableListOf<SttResult>()
        engine.transcribeStream(
            pcm = ShortArray(16000),
            language = SttLanguage.ENGLISH,
        ).collect { results.add(it) }

        assertEquals(1, results.size)
        assertEquals(SttResultType.FINAL, results[0].type)
        assertEquals("", results[0].text)
    }

    @Test
    fun `setLanguage without initialize sets language`() = runTest {
        val engine = MockSttEngine()
        // setLanguage before initialize — the mock handles it
        engine.setLanguage(SttLanguage.TAMIL)
        assertEquals(SttLanguage.TAMIL, engine.currentLanguage)
    }
}
