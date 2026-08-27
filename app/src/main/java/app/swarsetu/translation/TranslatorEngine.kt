package app.swarsetu.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitTask(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { exception -> cont.resumeWithException(exception) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * Handles on-device machine translation using Google ML Kit.
 */
class TranslatorEngine {
    /**
     * Translates text from [sourceLang] to [targetLang].
     * Automatically downloads the required ML Kit translation model if it is not present on the device.
     */
    suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        if (sourceLang == targetLang || text.isBlank()) return text

        // ML Kit expects standard language tags (e.g., "en", "hi")
        val source = TranslateLanguage.fromLanguageTag(sourceLang) ?: return text
        val target = TranslateLanguage.fromLanguageTag(targetLang) ?: return text

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()

        val translator = Translation.getClient(options)

        return try {
            // Download the required language model if needed. 
            // In a Walkie-Talkie distress scenario, we fetch over cellular if Wi-Fi is unavailable.
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).awaitTask()
            
            translator.translate(text).awaitTask() ?: text
        } catch (e: Exception) {
            Log.e("TranslatorEngine", "Translation failed: ${e.message}", e)
            // Fallback to the original text if translation fails (e.g. no network for model download)
            text
        } finally {
            translator.close()
        }
    }

    /**
     * Downloads all supported Indian language models for 100% offline usage.
     * Note: ML Kit does not natively support Odia or Malayalam yet.
     */
    suspend fun downloadAllRequiredModels(onProgress: (Int, Int, String) -> Unit) {
        val supportedLanguages = listOf(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.HINDI,
            TranslateLanguage.GUJARATI,
            TranslateLanguage.MARATHI,
            TranslateLanguage.KANNADA,
            TranslateLanguage.TAMIL,
            TranslateLanguage.TELUGU,
            TranslateLanguage.BENGALI
        )

        val modelManager = RemoteModelManager.getInstance()
        val conditions = DownloadConditions.Builder().build()
        val total = supportedLanguages.size
        var completed = 0

        // Download all models concurrently for better performance and to prevent Android's DownloadManager from throttling sequential requests
        coroutineScope {
            val deferredDownloads = supportedLanguages.map { lang ->
                async {
                    onProgress(completed, total, lang)
                    try {
                        val model = TranslateRemoteModel.Builder(lang).build()
                        val isDownloaded = modelManager.isModelDownloaded(model).awaitTask() == true
                        if (!isDownloaded) {
                            modelManager.download(model, conditions).awaitTask()
                        }
                    } catch (e: Exception) {
                        Log.e("TranslatorEngine", "Failed to download model for $lang: ${e.message}", e)
                        throw e
                    } finally {
                        completed++
                        onProgress(completed, total, lang)
                    }
                }
            }
            deferredDownloads.forEach { it.await() }
        }
    }
}
