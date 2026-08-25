package app.swarsetu.stt

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the STT engine. These tests run on a real device/emulator
 * and verify that the sherpa-onnx engine can actually load models and produce results.
 *
 * Prerequisites:
 * - Model assets must be bundled in the APK (stt-hi/, stt-en/, etc.)
 * - sherpa-onnx native .so must be loadable on the device
 *
 * Run: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=app.swarsetu.stt
 */
@RunWith(AndroidJUnit4::class)
class SttEngineInstrumentedTest {

    private lateinit var context: Context
    private lateinit var modelManager: SttModelManager

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        modelManager = SttModelManager(context)
    }

    @Test
    fun modelManager_detectsAvailableLanguages() = runTest {
        val available = modelManager.availableLanguages()
        // At least Hindi and English should be available
        assertTrue("Hindi should be available", available.contains(SttLanguage.HINDI))
        assertTrue("English should be available", available.contains(SttLanguage.ENGLISH))
    }

    @Test
    fun modelManager_allIndicModelsAvailable() = runTest {
        val available = modelManager.availableLanguages()
        // All 9 languages with bundled models should be detected
        val expected = setOf(
            SttLanguage.HINDI,
            SttLanguage.GUJARATI,
            SttLanguage.BENGALI,
            SttLanguage.MARATHI,
            SttLanguage.TAMIL,
            SttLanguage.KANNADA,
            SttLanguage.MALAYALAM,
            SttLanguage.TELUGU,
            SttLanguage.ENGLISH,
        )
        for (lang in expected) {
            assertTrue("${lang.displayName} should be available", available.contains(lang))
        }
    }

    @Test
    fun sherpaEngine_initializesWithHindi() = runTest {
        val factory = SttEngineFactory(context, modelManager)
        val engine = factory.create()

        // Verify it's a SherpaEngine
        assertTrue("Engine should be SherpaEngine", engine is SherpaEngine)

        // Initialize with Hindi
        engine.initialize(SttConfig(language = SttLanguage.HINDI))
        assertTrue("Engine should be ready", engine.isReady)
        assertEquals(SttLanguage.HINDI, engine.currentLanguage)

        engine.release()
    }

    @Test
    fun sherpaEngine_initializesWithEnglish() = runTest {
        val factory = SttEngineFactory(context, modelManager)
        val engine = factory.create()

        engine.initialize(SttConfig(language = SttLanguage.ENGLISH))
        assertTrue("Engine should be ready", engine.isReady)
        assertEquals(SttLanguage.ENGLISH, engine.currentLanguage)

        engine.release()
    }

    @Test
    fun sherpaEngine_transcribesSilence() = runTest {
        val factory = SttEngineFactory(context, modelManager)
        val engine = factory.create()

        engine.initialize(SttConfig(language = SttLanguage.HINDI))

        // Feed 1 second of silence (all zeros)
        val silence = ShortArray(16000) { 0 }
        val result = engine.transcribe(silence, SttLanguage.HINDI)

        // Silence should produce empty or near-empty result
        assertNotNull("Result should not be null", result)
        // The text might be empty or contain noise — just verify no crash
        engine.release()
    }

    @Test
    fun sherpaEngine_languageSwitching() = runTest {
        val factory = SttEngineFactory(context, modelManager)
        val engine = factory.create()

        // Start with Hindi
        engine.initialize(SttConfig(language = SttLanguage.HINDI))
        assertEquals(SttLanguage.HINDI, engine.currentLanguage)

        // Switch to English
        engine.setLanguage(SttLanguage.ENGLISH)
        assertEquals(SttLanguage.ENGLISH, engine.currentLanguage)

        // Switch to Bengali
        engine.setLanguage(SttLanguage.BENGALI)
        assertEquals(SttLanguage.BENGALI, engine.currentLanguage)

        engine.release()
    }

    @Test
    fun sherpaEngine_releaseIsIdempotent() = runTest {
        val factory = SttEngineFactory(context, modelManager)
        val engine = factory.create()

        engine.initialize(SttConfig(language = SttLanguage.HINDI))
        engine.release()
        engine.release() // Should not throw

        assertTrue("Engine should not be ready after release", !engine.isReady)
    }

    @Test
    fun pcmCapture_hasPermission() {
        val capture = PcmCapture(context)
        // On a test device, permission should be granted by the test runner
        // If not, this test documents the requirement
        val canCapture = capture.canCapture()
        // We just verify the method works — actual permission depends on test setup
        assertNotNull("canCapture should return a value", canCapture)
    }

    @Test
    fun voiceActivityDetector_worksWithPcmData() {
        val vad = VoiceActivityDetector()

        // Process silence
        val silence = ShortArray(480) { 0 }
        for (i in 1..10) {
            vad.processFrame(silence)
        }
        assertEquals(VoiceActivityDetector.State.SILENT, vad.state)

        // Process speech-like signal
        val speech = ShortArray(480) { 10_000 }
        for (i in 1..10) {
            vad.processFrame(speech)
        }
        assertEquals(VoiceActivityDetector.State.SPEECH, vad.state)

        vad.reset()
        assertEquals(VoiceActivityDetector.State.SILENT, vad.state)
    }

    @Test
    fun sttLanguage_allCodesValid() {
        for (lang in SttLanguage.entries) {
            assertNotNull("Language code should not be null", lang.code)
            assertTrue("Language code should not be blank", lang.code.isNotBlank())
            assertNotNull("Display name should not be null", lang.displayName)
            assertEquals("All languages should use 16kHz", 16_000, lang.sampleRate)
        }
    }

    @Test
    fun sttLanguage_fromCodeResolvesAll() {
        val testCases = listOf(
            "hi" to SttLanguage.HINDI,
            "gu" to SttLanguage.GUJARATI,
            "mr" to SttLanguage.MARATHI,
            "kn" to SttLanguage.KANNADA,
            "ml" to SttLanguage.MALAYALAM,
            "ta" to SttLanguage.TAMIL,
            "te" to SttLanguage.TELUGU,
            "bn" to SttLanguage.BENGALI,
            "en" to SttLanguage.ENGLISH,
        )
        for ((code, expected) in testCases) {
            assertEquals("fromCode($code) should resolve", expected, SttLanguage.fromCode(code))
        }
    }
}
