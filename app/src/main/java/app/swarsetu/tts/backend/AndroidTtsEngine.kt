package app.swarsetu.tts.backend

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.swarsetu.tts.TtsEngine
import app.swarsetu.tts.TtsLanguage
import app.swarsetu.tts.TtsLanguageCapability
import app.swarsetu.tts.TtsRequest
import app.swarsetu.tts.TtsResult
import app.swarsetu.tts.metrics.TtsMetricsCollector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidTtsEngine(
    private val context: Context,
    private val metricsCollector: TtsMetricsCollector
) : TtsEngine, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var initDeferred: CompletableDeferred<Boolean>? = null
    private var isReady = false

    override suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        if (isReady) return@withContext true
        
        val deferred = CompletableDeferred<Boolean>()
        initDeferred = deferred
        
        try {
            tts = TextToSpeech(context, this@AndroidTtsEngine)
            deferred.await()
        } catch (e: Exception) {
            isReady = false
            false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            setupListeners()
            initDeferred?.complete(true)
        } else {
            isReady = false
            initDeferred?.complete(false)
        }
        initDeferred = null
    }

    private fun setupListeners() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { metricsCollector.onPlaybackStart(it) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { metricsCollector.onCompleted(it) }
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, TextToSpeech.ERROR)"))
            override fun onError(utteranceId: String?) {
                utteranceId?.let { metricsCollector.onError(it, "Unknown TTS Error") }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { metricsCollector.onError(it, "TTS Error code: $errorCode") }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (interrupted) {
                    utteranceId?.let { metricsCollector.onInterrupted(it) }
                } else {
                    utteranceId?.let { metricsCollector.onCompleted(it) }
                }
            }

            override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                super.onBeginSynthesis(utteranceId, sampleRateInHz, audioFormat, channelCount)
                // We use the metrics collector's synthesis begin to mark this, but the actual tracking starts before speak()
            }

            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                super.onAudioAvailable(utteranceId, audio)
                utteranceId?.let { metricsCollector.onFirstAudioChunk(it) }
            }
        })
    }

    override fun isLanguageAvailable(language: TtsLanguage): TtsLanguageCapability {
        if (!isReady || tts == null) return TtsLanguageCapability.Error("TTS Engine not ready")
        
        val result = tts?.isLanguageAvailable(language.locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        val engineName = tts?.defaultEngine ?: "Unknown"

        return when (result) {
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                // Determine voice if possible
                val voice = tts?.voices?.firstOrNull { it.locale == language.locale }
                TtsLanguageCapability.Supported(engineName, voice?.name)
            }
            TextToSpeech.LANG_MISSING_DATA -> TtsLanguageCapability.MissingData(engineName)
            else -> TtsLanguageCapability.Unsupported("Language not supported by engine $engineName")
        }
    }

    override suspend fun speak(request: TtsRequest): TtsResult = withContext(Dispatchers.Main) {
        if (!isReady || tts == null) return@withContext TtsResult.Error("Engine not ready")
        
        val capability = isLanguageAvailable(request.language)
        if (capability is TtsLanguageCapability.Unsupported || capability is TtsLanguageCapability.MissingData) {
            return@withContext TtsResult.Error("Language unavailable: $capability")
        }
        
        tts?.language = request.language.locale
        
        val utteranceId = request.utteranceId ?: request.requestId
        val voiceName = (capability as? TtsLanguageCapability.Supported)?.voiceName
        
        metricsCollector.onSynthesisBegin(utteranceId, request.language, voiceName, request.text.length)

        val bundle = Bundle().apply {
            // Can place stream type or other parameters here if required.
        }

        // We use ADD to queue normally, or FLUSH if it's an alert (scheduler can also manage this).
        // For Phase 1 standalone testing, we just use flush since the scheduler orchestrates.
        val result = tts?.speak(request.text, TextToSpeech.QUEUE_FLUSH, bundle, utteranceId)
        
        if (result == TextToSpeech.SUCCESS) {
            TtsResult.Success
        } else {
            TtsResult.Error("Speak failed with code $result")
        }
    }

    override suspend fun synthesizeToFile(request: TtsRequest, outputFile: File): TtsResult = withContext(Dispatchers.Main) {
        if (!isReady || tts == null) return@withContext TtsResult.Error("Engine not ready")

        val capability = isLanguageAvailable(request.language)
        if (capability is TtsLanguageCapability.Unsupported || capability is TtsLanguageCapability.MissingData) {
            return@withContext TtsResult.Error("Language unavailable: $capability")
        }

        tts?.language = request.language.locale
        val utteranceId = request.utteranceId ?: request.requestId
        val voiceName = (capability as? TtsLanguageCapability.Supported)?.voiceName
        
        metricsCollector.onSynthesisBegin(utteranceId, request.language, voiceName, request.text.length)

        val result = tts?.synthesizeToFile(request.text, null, outputFile, utteranceId)
        
        if (result == TextToSpeech.SUCCESS) {
            TtsResult.Success
        } else {
            TtsResult.Error("SynthesizeToFile failed with code $result")
        }
    }

    override fun stop() {
        if (isReady) {
            tts?.stop()
        }
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        initDeferred?.cancel()
        initDeferred = null
    }
}
