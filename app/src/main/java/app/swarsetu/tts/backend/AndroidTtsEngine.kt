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
    private val metricsCollector: TtsMetricsCollector,
) : TtsEngine,
    TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var initDeferred: CompletableDeferred<Boolean>? = null
    private var isReady = false

    override suspend fun initialize(): Boolean =
        withContext(Dispatchers.Main) {
            if (isReady && tts != null) return@withContext true

            val existing = initDeferred
            if (existing != null && existing.isActive) {
                return@withContext existing.await()
            }

            val deferred = CompletableDeferred<Boolean>()
            initDeferred = deferred

            try {
                // Prefer Google TTS engine if available for full 10-language Indic support
                var chosenEngine: String? = null
                val tempTts = TextToSpeech(context.applicationContext, null)
                val engines = tempTts.engines.orEmpty()
                try { tempTts.shutdown() } catch (_: Exception) {}

                if (engines.any { it.name == "com.google.android.tts" }) {
                    chosenEngine = "com.google.android.tts"
                }

                if (chosenEngine != null) {
                    android.util.Log.d("AndroidTtsEngine", "Initializing with Google TTS engine ($chosenEngine)")
                    tts = TextToSpeech(context.applicationContext, this@AndroidTtsEngine, chosenEngine)
                } else {
                    android.util.Log.d("AndroidTtsEngine", "Initializing with default TTS engine")
                    tts = TextToSpeech(context.applicationContext, this@AndroidTtsEngine)
                }
                deferred.await()
            } catch (e: Exception) {
                isReady = false
                initDeferred?.complete(false)
                initDeferred = null
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
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
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

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) {
                    utteranceId?.let { metricsCollector.onError(it, "TTS Error code: $errorCode") }
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean,
                ) {
                    if (interrupted) {
                        utteranceId?.let { metricsCollector.onInterrupted(it) }
                    } else {
                        utteranceId?.let { metricsCollector.onCompleted(it) }
                    }
                }

                override fun onBeginSynthesis(
                    utteranceId: String?,
                    sampleRateInHz: Int,
                    audioFormat: Int,
                    channelCount: Int,
                ) {
                    super.onBeginSynthesis(utteranceId, sampleRateInHz, audioFormat, channelCount)
                    // We use the metrics collector's synthesis begin to mark this, but the actual tracking starts before speak()
                }

                override fun onAudioAvailable(
                    utteranceId: String?,
                    audio: ByteArray?,
                ) {
                    super.onAudioAvailable(utteranceId, audio)
                    utteranceId?.let { metricsCollector.onFirstAudioChunk(it) }
                }
            },
        )
    }

    override fun isLanguageAvailable(language: TtsLanguage): TtsLanguageCapability {
        if (!isReady || tts == null) return TtsLanguageCapability.Error("TTS Engine not ready")

        val availableVoices = tts?.voices.orEmpty()
        val matchingVoice = availableVoices.firstOrNull { v ->
            v.locale.language.equals(language.locale.language, ignoreCase = true)
        }

        var result = tts?.isLanguageAvailable(language.locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA) {
            result = tts?.isLanguageAvailable(java.util.Locale.forLanguageTag(language.locale.language)) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if ((result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA) && language.locale.language == "en") {
                result = tts?.isLanguageAvailable(java.util.Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        val engineName = tts?.defaultEngine ?: "Unknown"

        if (matchingVoice != null) {
            return TtsLanguageCapability.Supported(engineName, matchingVoice.name)
        }

        return when (result) {
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                val voice = availableVoices.firstOrNull { it.locale.language.equals(language.locale.language, ignoreCase = true) }
                TtsLanguageCapability.Supported(engineName, voice?.name)
            }

            TextToSpeech.LANG_MISSING_DATA -> {
                TtsLanguageCapability.MissingData(engineName)
            }

            else -> {
                TtsLanguageCapability.Unsupported("Language not supported by engine $engineName")
            }
        }
    }

    override suspend fun speak(request: TtsRequest): TtsResult =
        withContext(Dispatchers.Main) {
            if (!isReady || tts == null) {
                initialize()
            }

            if (!isReady || tts == null) {
                return@withContext TtsResult.Error("TTS Engine initialization failed or not ready")
            }

            val capability = isLanguageAvailable(request.language)
            try {
                val locale = request.language.locale
                var setRes = tts?.setLanguage(locale)
                if (setRes == TextToSpeech.LANG_MISSING_DATA || setRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val genericLocale = java.util.Locale.forLanguageTag(locale.language)
                    setRes = tts?.setLanguage(genericLocale)
                    if ((setRes == TextToSpeech.LANG_MISSING_DATA || setRes == TextToSpeech.LANG_NOT_SUPPORTED) && locale.language == "en") {
                        tts?.setLanguage(java.util.Locale.US)
                    }
                }

                val availableVoices = tts?.voices.orEmpty()
                val matchingVoice = availableVoices.firstOrNull { v ->
                    v.locale.language.equals(locale.language, ignoreCase = true) &&
                        (v.locale.country.isBlank() || v.locale.country.equals(locale.country, ignoreCase = true))
                } ?: availableVoices.firstOrNull { v ->
                    v.locale.language.equals(locale.language, ignoreCase = true)
                }

                if (matchingVoice != null) {
                    tts?.voice = matchingVoice
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
            } catch (e: Exception) {
                android.util.Log.w("AndroidTtsEngine", "Failed setting TTS language/attributes: ${e.message}")
            }

            val utteranceId = request.utteranceId ?: request.requestId
            val voiceName = (capability as? TtsLanguageCapability.Supported)?.voiceName

            metricsCollector.onSynthesisBegin(utteranceId, request.language, voiceName, request.text.length)

            val bundle =
                Bundle().apply {
                    // Stream type or audio attributes if required
                }

            val result =
                try {
                    tts?.speak(request.text, TextToSpeech.QUEUE_FLUSH, bundle, utteranceId)
                } catch (e: Exception) {
                    android.util.Log.e("AndroidTtsEngine", "Exception during tts.speak: ${e.message}", e)
                    TextToSpeech.ERROR
                }

            if (result == TextToSpeech.SUCCESS) {
                TtsResult.Success
            } else {
                TtsResult.Error("Speak failed with code $result")
            }
        }

    override suspend fun synthesizeToFile(
        request: TtsRequest,
        outputFile: File,
    ): TtsResult =
        withContext(Dispatchers.Main) {
            if (!isReady || tts == null) {
                initialize()
            }

            if (!isReady || tts == null) {
                return@withContext TtsResult.Error("TTS Engine initialization failed or not ready")
            }

            val capability = isLanguageAvailable(request.language)
            try {
                val locale = request.language.locale
                val availableVoices = tts?.voices.orEmpty()
                val matchingVoice = availableVoices.firstOrNull { v ->
                    v.locale.language.equals(locale.language, ignoreCase = true)
                }
                if (matchingVoice != null) {
                    tts?.voice = matchingVoice
                }
                var setRes = tts?.setLanguage(locale)
                if (setRes == TextToSpeech.LANG_MISSING_DATA || setRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val genericLocale = java.util.Locale.forLanguageTag(locale.language)
                    setRes = tts?.setLanguage(genericLocale)
                    if ((setRes == TextToSpeech.LANG_MISSING_DATA || setRes == TextToSpeech.LANG_NOT_SUPPORTED) && locale.language == "en") {
                        tts?.setLanguage(java.util.Locale.US)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AndroidTtsEngine", "Failed setting TTS language: ${e.message}")
            }

            val utteranceId = request.utteranceId ?: request.requestId
            val voiceName = (capability as? TtsLanguageCapability.Supported)?.voiceName

            metricsCollector.onSynthesisBegin(utteranceId, request.language, voiceName, request.text.length)

            val result =
                try {
                    tts?.synthesizeToFile(request.text, null, outputFile, utteranceId)
                } catch (e: Exception) {
                    android.util.Log.e("AndroidTtsEngine", "Exception during synthesizeToFile: ${e.message}", e)
                    TextToSpeech.ERROR
                }

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
