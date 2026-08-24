package app.swarsetu.stt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Benchmarking framework for measuring STT engine performance. Produces quantitative metrics
 * for accuracy, latency, memory, and resource usage.
 *
 * **Usage:**
 * ```
 * val benchmark = SttBenchmark(engine)
 * val report = benchmark.runFullBenchmark()
 * Log.d("STT", report.toSummary())
 * ```
 *
 * **What it measures:**
 * - Model initialization time
 * - First inference latency
 * - Steady-state transcription latency
 * - Real-time factor (RTF)
 * - Result quality (text non-emptiness)
 *
 * **What it does NOT measure** (requires hardware/OS-level tools):
 * - Peak RAM (use `Debug.getNativeHeapSize()` externally)
 * - CPU utilization (use `/proc/stat` or systrace externally)
 * - WER (requires reference transcripts — use external evaluation)
 *
 * **Thread safety:** Call from a single coroutine. Not thread-safe.
 *
 * No Knit/networking/TTS dependencies.
 */
class SttBenchmark(
    private val engine: SttEngine,
) {

    data class BenchmarkResult(
        val language: SttLanguage,
        val modelInitMs: Long,
        val firstInferenceMs: Long,
        val avgInferenceMs: Long,
        val realTimeFactor: Double,
        val totalSamplesProcessed: Int,
        val totalInferences: Int,
        val successfulInferences: Int,
        val emptyResults: Int,
    ) {
        val accuracy: Double
            get() = if (totalInferences > 0) {
                successfulInferences.toDouble() / totalInferences
            } else 0.0

        fun toSummary(): String = buildString {
            appendLine("=== STT Benchmark: ${language.displayName} (${language.code}) ===")
            appendLine("Model init:       ${modelInitMs}ms")
            appendLine("First inference:  ${firstInferenceMs}ms")
            appendLine("Avg inference:    ${avgInferenceMs}ms")
            appendLine("Real-time factor: %.3f".format(realTimeFactor))
            appendLine("Samples processed: $totalSamplesProcessed")
            appendLine("Total inferences: $totalInferences")
            appendLine("Successful:       $successfulInferences")
            appendLine("Empty results:    $emptyResults")
            appendLine("Non-empty rate:   %.1f%%".format(accuracy * 100))
        }
    }

    /**
     * Run a full benchmark for a single language. Generates synthetic PCM audio (sine wave at
     * speech-like amplitude) and measures inference performance.
     *
     * @param language Language to benchmark.
     * @param warmupRuns Number of warm-up inferences before measurement. Default 2.
     * @param measurementRuns Number of measured inferences. Default 5.
     * @param samplesPerRun Number of PCM samples per inference (default: 1 second at 16kHz).
     */
    suspend fun runBenchmark(
        language: SttLanguage,
        warmupRuns: Int = 2,
        measurementRuns: Int = 5,
        samplesPerRun: Int = SttLanguage.SAMPLE_RATE, // 1 second
    ): BenchmarkResult = withContext(Dispatchers.Default) {
        Log.d(TAG, "Starting benchmark for ${language.code}")

        // 1. Model initialization time
        val initStart = System.currentTimeMillis()
        try {
            engine.initialize(SttConfig(language = language))
        } catch (e: Exception) {
            Log.e(TAG, "Init failed for ${language.code}: ${e.message}")
            return@withContext BenchmarkResult(
                language = language,
                modelInitMs = System.currentTimeMillis() - initStart,
                firstInferenceMs = -1,
                avgInferenceMs = -1,
                realTimeFactor = -1.0,
                totalSamplesProcessed = 0,
                totalInferences = 0,
                successfulInferences = 0,
                emptyResults = 0,
            )
        }
        val initMs = System.currentTimeMillis() - initStart

        // Generate synthetic PCM (440 Hz sine wave, ~speech amplitude)
        val pcm = generateTestPcm(samplesPerRun, frequency = 440, amplitude = 5000)

        // 2. Warm-up runs
        var firstInferenceMs = -1L
        for (i in 0 until warmupRuns) {
            val start = System.currentTimeMillis()
            engine.transcribe(pcm, language)
            val elapsed = System.currentTimeMillis() - start
            if (i == 0) firstInferenceMs = elapsed
        }

        // 3. Measurement runs
        val inferenceTimes = mutableListOf<Long>()
        var successful = 0
        var empty = 0

        for (i in 0 until measurementRuns) {
            val start = System.currentTimeMillis()
            val result = engine.transcribe(pcm, language)
            val elapsed = System.currentTimeMillis() - start

            inferenceTimes.add(elapsed)
            if (result.text.isNotBlank()) successful++ else empty++
        }

        val avgMs = if (inferenceTimes.isNotEmpty()) {
            inferenceTimes.average().toLong()
        } else 0

        // Real-time factor: processing time / audio duration
        val audioDurationMs = samplesPerRun * 1000L / language.sampleRate
        val rtf = if (audioDurationMs > 0) avgMs.toDouble() / audioDurationMs else 0.0

        val result = BenchmarkResult(
            language = language,
            modelInitMs = initMs,
            firstInferenceMs = firstInferenceMs,
            avgInferenceMs = avgMs,
            realTimeFactor = rtf,
            totalSamplesProcessed = samplesPerRun * (warmupRuns + measurementRuns),
            totalInferences = warmupRuns + measurementRuns,
            successfulInferences = successful,
            emptyResults = empty,
        )

        Log.d(TAG, "Benchmark complete for ${language.code}: avg=${avgMs}ms, RTF=${"%.3f".format(rtf)}")
        result
    }

    /**
     * Run benchmarks for all available languages and return a comparative report.
     */
    suspend fun runFullBenchmark(
        languages: Set<SttLanguage> = SttLanguage.supported,
    ): List<BenchmarkResult> {
        val results = mutableListOf<BenchmarkResult>()
        for (lang in languages) {
            val result = runBenchmark(lang)
            results.add(result)
            // Release between languages to get clean measurements
            engine.release()
        }
        return results
    }

    /**
     * Generate synthetic PCM test audio (sine wave). Useful for benchmarking when real speech
     * samples are not available.
     *
     * @param samples Number of PCM samples to generate.
     * @param frequency Sine wave frequency in Hz (440 = A4, typical speech fundamental).
     * @param amplitude Peak amplitude (0–32767).
     */
    private fun generateTestPcm(
        samples: Int,
        frequency: Int = 440,
        amplitude: Int = 5000,
    ): ShortArray {
        return ShortArray(samples) { i ->
            val t = i.toDouble() / SttLanguage.SAMPLE_RATE
            val value = (amplitude * kotlin.math.sin(2.0 * Math.PI * frequency * t)).toInt()
            value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private companion object {
        const val TAG = "SttBenchmark"
    }
}
