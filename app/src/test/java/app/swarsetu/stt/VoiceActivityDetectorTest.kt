package app.swarsetu.stt

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [VoiceActivityDetector] — energy-based VAD. */
class VoiceActivityDetectorTest {

    @Test
    fun `starts in silent state`() {
        val vad = VoiceActivityDetector()
        assertEquals(VoiceActivityDetector.State.SILENT, vad.state)
    }

    @Test
    fun `silence frames stay silent`() {
        val vad = VoiceActivityDetector()
        val silence = ShortArray(480) { 0 } // 30ms at 16kHz, all zeros

        for (i in 1..30) {
            vad.processFrame(silence)
        }
        assertEquals(VoiceActivityDetector.State.SILENT, vad.state)
    }

    @Test
    fun `speech frames transition to speech state`() {
        val vad = VoiceActivityDetector()
        val speech = ShortArray(480) { 10_000 } // 30ms at 16kHz, loud

        // Need enough consecutive speech frames
        for (i in 1..10) {
            vad.processFrame(speech)
        }
        assertEquals(VoiceActivityDetector.State.SPEECH, vad.state)
    }

    @Test
    fun `silence after speech transitions back to silent`() {
        val vad = VoiceActivityDetector()
        val speech = ShortArray(480) { 10_000 }
        val silence = ShortArray(480) { 0 }

        // Start with speech
        for (i in 1..10) vad.processFrame(speech)
        assertEquals(VoiceActivityDetector.State.SPEECH, vad.state)

        // Then silence
        for (i in 1..30) vad.processFrame(silence)
        assertEquals(VoiceActivityDetector.State.SILENT, vad.state)
    }

    @Test
    fun `brief noise does not trigger speech`() {
        val vad = VoiceActivityDetector()
        val silence = ShortArray(480) { 0 }
        val noise = ShortArray(480) { 500 } // Above speech threshold, but brief

        for (i in 1..2) vad.processFrame(noise)
        // Still silent — noise was too brief (requires 3 frames)
        assertEquals(VoiceActivityDetector.State.SILENT, vad.state)
    }

    @Test
    fun `reset returns to silent state`() {
        val vad = VoiceActivityDetector()
        val speech = ShortArray(480) { 10_000 }

        for (i in 1..10) vad.processFrame(speech)
        assertEquals(VoiceActivityDetector.State.SPEECH, vad.state)

        vad.reset()
        assertEquals(VoiceActivityDetector.State.SILENT, vad.state)
    }

    @Test
    fun `empty frame is handled`() {
        val vad = VoiceActivityDetector()
        vad.processFrame(ShortArray(0))
        assertEquals(VoiceActivityDetector.State.SILENT, vad.state)
    }

    @Test
    fun `custom thresholds work`() {
        val vad = VoiceActivityDetector(
            silenceThreshold = 100,
            speechThreshold = 200,
            speechFrames = 2,
            silenceFrames = 2,
        )
        val speech = ShortArray(480) { 300 } // Above custom speech threshold

        vad.processFrame(speech)
        vad.processFrame(speech)
        assertEquals(VoiceActivityDetector.State.SPEECH, vad.state)
    }
}
