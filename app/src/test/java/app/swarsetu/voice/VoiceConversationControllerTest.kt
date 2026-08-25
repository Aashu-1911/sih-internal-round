package app.swarsetu.voice

import app.swarsetu.stt.SttLanguage
import app.swarsetu.stt.SttPipeline
import app.swarsetu.stt.SttResult
import app.swarsetu.stt.SttResultType
import app.swarsetu.tts.TtsLanguage
import app.swarsetu.tts.TtsManager
import app.swarsetu.tts.TtsPriority
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceConversationControllerTest {

    private lateinit var sttPipeline: SttPipeline
    private lateinit var ttsManager: TtsManager
    private val testScope = TestScope(UnconfinedTestDispatcher())

    private val latestResultFlow = MutableStateFlow<SttResult?>(null)
    private val pipelineStateFlow = MutableStateFlow(SttPipeline.PipelineState.IDLE)

    @Before
    fun setup() {
        sttPipeline = mockk(relaxed = true) {
            every { latestResult } returns latestResultFlow
            every { state } returns pipelineStateFlow
            every { canCapture } returns true
        }
        ttsManager = mockk(relaxed = true)
    }

    private fun createController(scope: kotlinx.coroutines.CoroutineScope): VoiceConversationController {
        return DefaultVoiceConversationController(
            scope = scope,
            sttPipeline = sttPipeline,
            ttsManager = ttsManager
        ).apply { isLoopEnabled = true }
    }

    @Test
    fun `final STT result triggers TTS`() = testScope.runTest {
        val controller = createController(backgroundScope)
        val result = SttResult("hello", SttResultType.FINAL, SttLanguage.ENGLISH)
        latestResultFlow.value = result

        coVerify { 
            ttsManager.speak(match { 
                it.text == "hello" && it.language == TtsLanguage.ENGLISH && it.priority == TtsPriority.NORMAL 
            })
        }
    }

    @Test
    fun `partial STT result does not trigger TTS`() = testScope.runTest {
        val controller = createController(backgroundScope)
        val result = SttResult("hello", SttResultType.PARTIAL, SttLanguage.ENGLISH)
        latestResultFlow.value = result

        coVerify(exactly = 0) { ttsManager.speak(any()) }
    }

    @Test
    fun `empty final text does not trigger TTS`() = testScope.runTest {
        val controller = createController(backgroundScope)
        val result = SttResult("   ", SttResultType.FINAL, SttLanguage.ENGLISH)
        latestResultFlow.value = result

        coVerify(exactly = 0) { ttsManager.speak(any()) }
    }

    @Test
    fun `correct language mapping`() = testScope.runTest {
        val controller = createController(backgroundScope)
        val result = SttResult("नमस्ते", SttResultType.FINAL, SttLanguage.HINDI)
        latestResultFlow.value = result

        coVerify { 
            ttsManager.speak(match { 
                it.language == TtsLanguage.HINDI
            })
        }
    }

    @Test
    fun `duplicate final result instance does not speak twice`() = testScope.runTest {
        val controller = createController(backgroundScope)
        val result = SttResult("hello", SttResultType.FINAL, SttLanguage.ENGLISH)
        
        // First emission
        latestResultFlow.value = result
        
        // Second emission of the exact same instance
        latestResultFlow.value = null // reset flow to force re-emission
        latestResultFlow.value = result

        coVerify(exactly = 1) { ttsManager.speak(any()) }
    }

    @Test
    fun `different final result with same text speaks twice`() = testScope.runTest {
        val controller = createController(backgroundScope)
        val result1 = SttResult("Help", SttResultType.FINAL, SttLanguage.ENGLISH, durationMs = 1000)
        val result2 = SttResult("Help", SttResultType.FINAL, SttLanguage.ENGLISH, durationMs = 1200)
        
        latestResultFlow.value = result1
        latestResultFlow.value = result2

        coVerify(exactly = 2) { ttsManager.speak(any()) }
    }

    @Test
    fun `stop prevents pending TTS`() = testScope.runTest {
        val controller = createController(backgroundScope)
        controller.stopSpeaking()
        verify { ttsManager.stopAll() }
        assertEquals(VoiceState.IDLE, controller.state.value)
    }
}
