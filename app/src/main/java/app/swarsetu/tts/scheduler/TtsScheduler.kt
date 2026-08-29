package app.swarsetu.tts.scheduler

import android.util.Log
import app.swarsetu.tts.TtsEngine
import app.swarsetu.tts.TtsPriority
import app.swarsetu.tts.TtsRequest
import app.swarsetu.tts.TtsResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages TTS synthesis concurrency.
 * - NORMAL requests are queued sequentially.
 * - ALERT requests immediately interrupt playing NORMAL requests, or preempt a playing ALERT.
 */
class TtsScheduler(
    private val engine: TtsEngine,
    private val scope: CoroutineScope,
) {
    private val normalQueue = Channel<TtsRequest>(Channel.UNLIMITED)
    private var activeJob: Job? = null
    private val mutex = Mutex()

    init {
        // Start consuming NORMAL messages sequentially
        scope.launch {
            normalQueue.consumeAsFlow().collect { request ->
                processRequest(request)
            }
        }
    }

    /**
     * Submits a request to the scheduler.
     */
    suspend fun submit(request: TtsRequest) {
        if (request.priority == TtsPriority.ALERT) {
            handleAlert(request)
        } else {
            normalQueue.send(request)
        }
    }

    private suspend fun handleAlert(request: TtsRequest) {
        mutex.withLock {
            // Cancel any ongoing job (either NORMAL or previous ALERT)
            activeJob?.cancel()
            engine.stop()

            // Launch the new ALERT request immediately
            activeJob =
                scope.launch {
                    try {
                        engine.speak(request)
                    } catch (e: Exception) {
                        Log.e("TtsScheduler", "Error executing alert", e)
                    }
                }
        }
    }

    private suspend fun processRequest(request: TtsRequest) {
        mutex.withLock {
            activeJob =
                scope.launch {
                    try {
                        engine.speak(request)
                    } catch (e: Exception) {
                        Log.e("TtsScheduler", "Error executing normal request", e)
                    }
                }
        }
        // Wait for the active job to finish before pulling the next normal request.
        activeJob?.join()
    }

    fun stopAll() {
        scope.launch {
            mutex.withLock {
                activeJob?.cancel()
                activeJob = null
                engine.stop()
            }
        }
    }
}
