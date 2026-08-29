package app.swarsetu.translation

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitTask(): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { exception -> cont.resumeWithException(exception) }
        addOnCanceledListener { cont.cancel() }
    }

/**
 * Handles on-device machine translation using Google ML Kit.
 */
class TranslatorEngine {
    private val translatorCache = ConcurrentHashMap<String, Translator>()
    private val downloadedModels = ConcurrentHashMap<String, Boolean>()

    /**
     * Normalizes language codes/names ("english", "en", "hindi", "hi") to standard ML Kit tags.
     */
    fun normalizeToLanguageTag(lang: String?): String? {
        if (lang.isNullOrBlank()) return null
        val clean = lang.trim().lowercase()
        return when (clean) {
            "en", "english", "en-in", "en-us" -> TranslateLanguage.ENGLISH
            "hi", "hindi" -> TranslateLanguage.HINDI
            "gu", "gujarati" -> TranslateLanguage.GUJARATI
            "mr", "marathi" -> TranslateLanguage.MARATHI
            "kn", "kannada" -> TranslateLanguage.KANNADA
            "ta", "tamil" -> TranslateLanguage.TAMIL
            "te", "telugu" -> TranslateLanguage.TELUGU
            "bn", "bengali" -> TranslateLanguage.BENGALI
            else -> TranslateLanguage.fromLanguageTag(clean)
        }
    }

    private fun getOrCreateTranslator(
        source: String,
        target: String,
    ): Translator {
        val key = "$source->$target"
        return translatorCache.computeIfAbsent(key) {
            val options =
                TranslatorOptions
                    .Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
            Translation.getClient(options)
        }
    }

    /**
     * Translates text from [sourceLang] to [targetLang].
     * Uses cached on-device translators for high-speed sub-10ms execution.
     */
    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String {
        if (text.isBlank()) return text

        val source = normalizeToLanguageTag(sourceLang) ?: return text
        val target = normalizeToLanguageTag(targetLang) ?: return text
        if (source == target) return text

        val translator = getOrCreateTranslator(source, target)

        return try {
            val conditions = DownloadConditions.Builder().build()
            val modelManager = RemoteModelManager.getInstance()

            if (downloadedModels[source] != true) {
                val sourceModel = TranslateRemoteModel.Builder(source).build()
                if (modelManager.isModelDownloaded(sourceModel).awaitTask() != true) {
                    modelManager.download(sourceModel, conditions).awaitTask()
                }
                downloadedModels[source] = true
            }

            if (downloadedModels[target] != true) {
                val targetModel = TranslateRemoteModel.Builder(target).build()
                if (modelManager.isModelDownloaded(targetModel).awaitTask() != true) {
                    modelManager.download(targetModel, conditions).awaitTask()
                }
                downloadedModels[target] = true
            }

            translator.downloadModelIfNeeded(conditions).awaitTask()
            translator.translate(text).awaitTask() ?: text
        } catch (e: Exception) {
            val cause = e.message?.lowercase() ?: ""
            if (cause.contains("model") || cause.contains("download")) {
                Log.w(
                    "TranslatorEngine",
                    "Translation failed: ML Kit model for " +
                        "$sourceLang ($source)→$targetLang ($target) is not downloaded. Falling back to original text.",
                    e,
                )
            } else {
                Log.e("TranslatorEngine", "Translation failed: ${e.message}", e)
            }
            text
        }
    }

    /**
     * Downloads all supported Indian language models in parallel batches for high-speed offline readiness.
     */
    suspend fun downloadAllRequiredModels(onProgress: (Int, Int, String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val supportedLanguages =
                    listOf(
                        TranslateLanguage.ENGLISH,
                        TranslateLanguage.HINDI,
                        TranslateLanguage.MARATHI,
                        TranslateLanguage.GUJARATI,
                        TranslateLanguage.TAMIL,
                        TranslateLanguage.TELUGU,
                        TranslateLanguage.BENGALI,
                        TranslateLanguage.KANNADA,
                    )

                val modelManager = RemoteModelManager.getInstance()
                val conditions = DownloadConditions.Builder().build()
                val total = supportedLanguages.size
                val completed = java.util.concurrent.atomic.AtomicInteger(0)
                val semaphore = Semaphore(3)

                val jobs =
                    supportedLanguages.map { lang ->
                        async {
                            semaphore.withPermit {
                                val model = TranslateRemoteModel.Builder(lang).build()
                                var downloaded = false
                                try {
                                    if (modelManager.isModelDownloaded(model).awaitTask() == true) {
                                        downloaded = true
                                    }
                                } catch (_: Exception) {}

                                if (!downloaded) {
                                    var attempts = 0
                                    while (!downloaded && attempts < 2) {
                                        attempts++
                                        try {
                                            modelManager.download(model, conditions).awaitTask()
                                            downloaded = true
                                            downloadedModels[lang] = true
                                            Log.d("TranslatorEngine", "Downloaded ML Kit model for $lang")
                                        } catch (e: Exception) {
                                            Log.w("TranslatorEngine", "Attempt $attempts failed for $lang: ${e.message}")
                                            if (attempts < 2) delay(300)
                                        }
                                    }
                                } else {
                                    downloadedModels[lang] = true
                                }

                                val count = completed.incrementAndGet()
                                onProgress(count, total, lang)
                                downloaded
                            }
                        }
                    }

                val results = jobs.map { it.await() }
                val englishDownloaded = downloadedModels[TranslateLanguage.ENGLISH] == true
                val hindiDownloaded = downloadedModels[TranslateLanguage.HINDI] == true

                englishDownloaded || hindiDownloaded || results.any { it }
            }
        }
}
