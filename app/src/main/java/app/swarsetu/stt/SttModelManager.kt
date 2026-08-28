package app.swarsetu.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages STT model availability, loading, and lifecycle. Responsible for:
 * - Querying which languages have bundled models
 * - Loading model assets into memory (delegated to the engine)
 * - Tracking loaded models and evicting idle ones to bound memory
 * - Providing model metadata for diagnostics
 *
 * Mirrors the lazy-load + Mutex + runCatching pattern established by
 * [app.swarsetu.moderation.MlTextModerator] and [app.swarsetu.moderation.NsfwImageModerator].
 *
 * This class is Android-aware (reads assets) but otherwise pure — it knows nothing about Knit,
 * the mesh, or TTS.
 */
class SttModelManager(
    private val context: Context,
) {
    /** Mutex guarding model load/evict state. */
    private val mutex = Mutex()

    /** Currently loaded model info by language, or null if not loaded. */
    private val loadedModels = mutableMapOf<SttLanguage, SttModelInfo>()

    /**
     * Returns the set of languages that have bundled model assets available for loading.
     * Does not load anything — purely a file-existence check cached at first call.
     */
    suspend fun availableLanguages(): Set<SttLanguage> =
        withContext(Dispatchers.IO) {
            SttLanguage.entries
                .filter { lang ->
                    lang.assetDir != null && modelAssetExists(lang)
                }.toSet()
        }

    /**
     * True when [language] has a model that can be loaded. Checks both:
     * 1. Asset directory (models bundled in APK)
     * 2. Internal storage (models extracted/downloaded at runtime to filesDir/stt/)
     */
    suspend fun isAvailable(language: SttLanguage): Boolean =
        language.assetDir != null && (modelAssetExists(language) || modelFilesExist(language))

    /**
     * Returns metadata for all bundled models. Reads asset directories once and caches the result.
     * Useful for diagnostics and the language-selector UI.
     */
    suspend fun modelInfo(): List<SttModelInfo> =
        withContext(Dispatchers.IO) {
            SttLanguage.entries.mapNotNull { lang ->
                if (lang.assetDir == null) return@mapNotNull null
                val exists = modelAssetExists(lang)
                SttModelInfo(
                    language = lang,
                    modelPath = lang.assetDir,
                    available = exists,
                    description = "STT model for ${lang.displayName}",
                )
            }
        }

    /**
     * Returns the cached model info for a loaded language, or null if not loaded.
     */
    fun loadedInfo(language: SttLanguage): SttModelInfo? = loadedModels[language]

    /**
     * Record that a model was successfully loaded. Called by the engine after construction.
     */
    suspend fun markLoaded(info: SttModelInfo) =
        mutex.withLock {
            loadedModels[info.language] = info
            Log.d(TAG, "Model loaded: ${info.language.code} (${info.sizeMb} MB)")
        }

    /**
     * Record that a model was released. Called by the engine on [SttEngine.release].
     */
    suspend fun markReleased(language: SttLanguage) =
        mutex.withLock {
            val removed = loadedModels.remove(language)
            if (removed != null) {
                Log.d(TAG, "Model released: ${language.code}")
            }
        }

    /**
     * Release all loaded models. Called on application shutdown or when memory pressure demands it.
     */
    suspend fun releaseAll() =
        mutex.withLock {
            val count = loadedModels.size
            loadedModels.clear()
            Log.d(TAG, "All models released ($count)")
        }

    /**
     * Number of models currently loaded in memory.
     */
    fun loadedCount(): Int = loadedModels.size

    private fun modelAssetExists(language: SttLanguage): Boolean =
        try {
            val dir = language.assetDir ?: return false
            // Check for at least one file in the asset directory.
            context.assets.list(dir)?.isNotEmpty() == true
        } catch (_: Exception) {
            false
        }

    /**
     * Check if this language's model has already been extracted to internal storage.
     * VoskEngine extracts assets to [Context.filesDir]/stt/<assetDir> on first use.
     * Models can also be downloaded directly into this path at runtime.
     */
    private fun modelFilesExist(language: SttLanguage): Boolean =
        try {
            val dir = language.assetDir ?: return false
            val modelDir = java.io.File(context.filesDir, "stt/$dir")
            modelDir.exists() && modelDir.isDirectory && modelDir.list()?.isNotEmpty() == true
        } catch (_: Exception) {
            false
        }

    /**
     * Downloads and extracts the Vosk STT model for [language] into [context.filesDir]/stt/<assetDir>.
     * Returns true on success, false on network or extraction failure.
     */
    suspend fun downloadSttModel(
        language: SttLanguage,
        onProgress: ((Float) -> Unit)? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val assetDir = language.assetDir ?: return@withContext false
            val targetDir = java.io.File(context.filesDir, "stt/$assetDir")
            if (modelFilesExist(language)) {
                Log.d(TAG, "STT model for ${language.code} already exists")
                onProgress?.invoke(1f)
                return@withContext true
            }

            val urlString = MODEL_URLS[language] ?: return@withContext false
            Log.d(TAG, "Downloading STT model for ${language.code} from $urlString")

            var connection: java.net.HttpURLConnection? = null
            var tempZip: java.io.File? = null
            try {
                tempZip = java.io.File(context.cacheDir, "stt_${language.code}_temp.zip")
                val url = java.net.URL(urlString)
                connection = url.openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    Log.e(TAG, "Failed to download STT model for ${language.code}: HTTP $responseCode")
                    return@withContext false
                }

                val totalLength = connection.contentLength.toLong()
                var downloaded = 0L

                connection.inputStream.use { input ->
                    tempZip.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalLength > 0) {
                                onProgress?.invoke((downloaded.toFloat() / totalLength).coerceIn(0f, 0.9f))
                            }
                        }
                    }
                }

                Log.d(TAG, "Downloaded STT model zip for ${language.code} ($downloaded bytes). Extracting...")
                targetDir.mkdirs()

                // Extract zip into targetDir
                unzipToDir(tempZip, targetDir)
                onProgress?.invoke(1f)
                Log.d(TAG, "Extracted STT model for ${language.code} into ${targetDir.absolutePath}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading/extracting STT model for ${language.code}: ${e.message}", e)
                false
            } finally {
                connection?.disconnect()
                tempZip?.delete()
            }
        }

    private fun unzipToDir(zipFile: java.io.File, destDir: java.io.File) {
        destDir.mkdirs()
        java.util.zip.ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry: java.util.zip.ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                val normalizedPath = stripTopLevelDir(entryName)
                if (normalizedPath.isNotBlank()) {
                    val target = java.io.File(destDir, normalizedPath)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun stripTopLevelDir(path: String): String {
        val slashIndex = path.indexOf('/')
        return if (slashIndex != -1 && slashIndex < path.length - 1) {
            path.substring(slashIndex + 1)
        } else if (slashIndex == path.length - 1) {
            "" // Directory entry of top-level dir
        } else {
            path
        }
    }

    private companion object {
        const val TAG = "SttModelManager"

        private val MODEL_URLS =
            mapOf(
                SttLanguage.ENGLISH to "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
                SttLanguage.HINDI to "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
                SttLanguage.GUJARATI to "https://alphacephei.com/vosk/models/vosk-model-small-gu-0.42.zip",
                SttLanguage.MARATHI to "https://alphacephei.com/vosk/models/vosk-model-small-mr-0.22.zip",
                SttLanguage.BENGALI to "https://alphacephei.com/vosk/models/vosk-model-small-bn-0.22.zip",
                SttLanguage.TAMIL to "https://alphacephei.com/vosk/models/vosk-model-small-ta-0.22.zip",
                SttLanguage.TELUGU to "https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip",
            )
    }
}
