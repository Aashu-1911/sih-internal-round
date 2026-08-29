package app.swarsetu.tts.metrics

import app.swarsetu.tts.TtsLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsMetricsCollectorTest {
    @Test
    fun `test metrics collected correctly for successful playback`() {
        val collector = TtsMetricsCollector()
        val reqId = "req_123"

        collector.onSynthesisBegin(reqId, TtsLanguage.HINDI, "hi-in-local", 50)

        // Ensure state is updated immediately
        val initial = collector.latestMetrics.value
        assertNotNull(initial)
        assertEquals(reqId, initial?.requestId)
        assertNotNull(initial?.synthesisBeginTimestampMs)
        assertNull(initial?.firstAudioChunkTimestampMs)

        // Simulate audio chunk delay (e.g. TTFA is ~100ms)
        Thread.sleep(100)
        collector.onFirstAudioChunk(reqId)

        val chunked = collector.latestMetrics.value
        assertNotNull(chunked?.firstAudioChunkTimestampMs)
        assertTrue((chunked?.ttfaMs ?: 0) >= 100)

        // Simulate complete (audio was 1000ms long)
        collector.onCompleted(reqId, 1000L)

        val completed = collector.latestMetrics.value
        assertNotNull(completed?.completionTimestampMs)
        assertFalse(completed?.error ?: true)
        assertFalse(completed?.interrupted ?: true)

        // RTF should be synthesisTime (around 100ms) / 1000ms = ~0.1
        val rtf = completed?.rtf
        assertNotNull(rtf)
        assertTrue("RTF $rtf should be around 0.1", rtf!! < 0.5f)
    }

    @Test
    fun `test interrupted flag is set`() {
        val collector = TtsMetricsCollector()
        val reqId = "req_int"

        collector.onSynthesisBegin(reqId, TtsLanguage.ENGLISH, null, 10)
        collector.onInterrupted(reqId)

        val metrics = collector.latestMetrics.value
        assertTrue(metrics?.interrupted ?: false)
    }

    @Test
    fun `test error flag and message are set`() {
        val collector = TtsMetricsCollector()
        val reqId = "req_err"

        collector.onSynthesisBegin(reqId, TtsLanguage.ENGLISH, null, 10)
        collector.onError(reqId, "Language data missing")

        val metrics = collector.latestMetrics.value
        assertTrue(metrics?.error ?: false)
        assertEquals("Language data missing", metrics?.errorMessage)
    }
}
