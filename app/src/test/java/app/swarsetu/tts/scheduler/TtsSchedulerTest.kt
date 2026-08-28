package app.swarsetu.tts.scheduler

import app.swarsetu.tts.TtsEngine
import app.swarsetu.tts.TtsLanguage
import app.swarsetu.tts.TtsLanguageCapability
import app.swarsetu.tts.TtsPriority
import app.swarsetu.tts.TtsRequest
import app.swarsetu.tts.TtsResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TtsSchedulerTest {
    class FakeTtsEngine : TtsEngine {
        val executedRequests = mutableListOf<String>()
        var isStopped = false
        var activeDelayMs = 0L

        override suspend fun initialize() = true

        override fun isLanguageAvailable(language: TtsLanguage) = TtsLanguageCapability.Supported("Fake", null)

        override suspend fun speak(request: TtsRequest): TtsResult {
            executedRequests.add(request.text)
            if (activeDelayMs > 0) {
                delay(activeDelayMs)
            }
            return TtsResult.Success
        }

        override suspend fun synthesizeToFile(
            request: TtsRequest,
            outputFile: File,
        ) = TtsResult.Success

        override fun stop() {
            isStopped = true
        }

        override fun release() {}
    }

    @Test
    fun `normal requests are processed sequentially`() =
        runTest {
            val engine = FakeTtsEngine()
            engine.activeDelayMs = 100 // Simulate 100ms synthesis time
            val scheduler = TtsScheduler(engine, backgroundScope)

            scheduler.submit(TtsRequest("1", "First", TtsLanguage.ENGLISH, TtsPriority.NORMAL))
            scheduler.submit(TtsRequest("2", "Second", TtsLanguage.ENGLISH, TtsPriority.NORMAL))

            advanceTimeBy(50)
            assertEquals(listOf("First"), engine.executedRequests)

            advanceTimeBy(100)
            assertEquals(listOf("First", "Second"), engine.executedRequests)
        }

    @Test
    fun `alert request preempts normal request`() =
        runTest {
            val engine = FakeTtsEngine()
            engine.activeDelayMs = 5000 // Long task
            val scheduler = TtsScheduler(engine, backgroundScope)

            // Submit a long normal task
            scheduler.submit(TtsRequest("1", "Normal", TtsLanguage.ENGLISH, TtsPriority.NORMAL))

            advanceTimeBy(100)
            assertEquals(listOf("Normal"), engine.executedRequests)

            // Submit an alert task
            scheduler.submit(TtsRequest("2", "Alert", TtsLanguage.ENGLISH, TtsPriority.ALERT))

            // The scheduler should have called stop() and immediately started the alert
            advanceTimeBy(10)
            assertTrue(engine.isStopped)
            assertEquals(listOf("Normal", "Alert"), engine.executedRequests)
        }
}
