package app.swarsetu.tts.metrics

import app.swarsetu.tts.TtsLanguage
import app.swarsetu.tts.TtsMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks TTS performance metrics, including initialization latency, TTFA, and Real-Time Factor (RTF).
 */
class TtsMetricsCollector {
    private val _latestMetrics = MutableStateFlow<TtsMetrics?>(null)
    val latestMetrics: StateFlow<TtsMetrics?> = _latestMetrics.asStateFlow()

    private val ongoingRequests = ConcurrentHashMap<String, TtsMetrics>()

    fun onSynthesisBegin(
        requestId: String,
        language: TtsLanguage,
        voiceName: String?,
        textLength: Int,
    ) {
        val metric =
            TtsMetrics(
                requestId = requestId,
                language = language,
                voiceName = voiceName,
                textLength = textLength,
                synthesisBeginTimestampMs = System.currentTimeMillis(),
            )
        ongoingRequests[requestId] = metric
        _latestMetrics.value = metric
    }

    fun onFirstAudioChunk(requestId: String) {
        ongoingRequests
            .computeIfPresent(requestId) { _, metric ->
                if (metric.firstAudioChunkTimestampMs == null) {
                    metric.copy(firstAudioChunkTimestampMs = System.currentTimeMillis())
                } else {
                    metric
                }
            }?.let { _latestMetrics.value = it }
    }

    fun onPlaybackStart(requestId: String) {
        ongoingRequests
            .computeIfPresent(requestId) { _, metric ->
                if (metric.playbackStartTimestampMs == null) {
                    metric.copy(playbackStartTimestampMs = System.currentTimeMillis())
                } else {
                    metric
                }
            }?.let { _latestMetrics.value = it }
    }

    fun onCompleted(
        requestId: String,
        audioDurationMs: Long? = null,
    ) {
        ongoingRequests
            .computeIfPresent(requestId) { _, metric ->
                metric.copy(
                    completionTimestampMs = System.currentTimeMillis(),
                    totalAudioDurationMs = audioDurationMs,
                )
            }?.let {
                _latestMetrics.value = it
                ongoingRequests.remove(requestId)
            }
    }

    fun onInterrupted(requestId: String) {
        ongoingRequests
            .computeIfPresent(requestId) { _, metric ->
                metric.copy(
                    interrupted = true,
                    completionTimestampMs = System.currentTimeMillis(),
                )
            }?.let {
                _latestMetrics.value = it
                ongoingRequests.remove(requestId)
            }
    }

    fun onError(
        requestId: String,
        errorReason: String,
    ) {
        ongoingRequests
            .computeIfPresent(requestId) { _, metric ->
                metric.copy(
                    error = true,
                    errorMessage = errorReason,
                    completionTimestampMs = System.currentTimeMillis(),
                )
            }?.let {
                _latestMetrics.value = it
                ongoingRequests.remove(requestId)
            }
    }
}
