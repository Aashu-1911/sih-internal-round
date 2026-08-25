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
    suspend fun availableLanguages(): Set<SttLanguage> = withContext(Dispatchers.IO) {
        SttLanguage.entries.filter { lang ->
            lang.assetDir != null && modelAssetExists(lang)
        }.toSet()
    }

    /**
     * True when [language] has a bundled model that can be loaded. Checks asset existence without
     * loading the model into memory.
     */
    suspend fun isAvailable(language: SttLanguage): Boolean =
        language.assetDir != null && modelAssetExists(language)

    /**
     * Returns metadata for all bundled models. Reads asset directories once and caches the result.
     * Useful for diagnostics and the language-selector UI.
     */
    suspend fun modelInfo(): List<SttModelInfo> = withContext(Dispatchers.IO) {
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
    fun loadedInfo(language: SttLanguage): SttModelInfo? =
        loadedModels[language]

    /**
     * Record that a model was successfully loaded. Called by the engine after construction.
     */
    suspend fun markLoaded(info: SttModelInfo) = mutex.withLock {
        loadedModels[info.language] = info
        Log.d(TAG, "Model loaded: ${info.language.code} (${info.sizeMb} MB)")
    }

    /**
     * Record that a model was released. Called by the engine on [SttEngine.release].
     */
    suspend fun markReleased(language: SttLanguage) = mutex.withLock {
        val removed = loadedModels.remove(language)
        if (removed != null) {
            Log.d(TAG, "Model released: ${language.code}")
        }
    }

    /**
     * Release all loaded models. Called on application shutdown or when memory pressure demands it.
     */
    suspend fun releaseAll() = mutex.withLock {
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
            // Check for model.int8.onnx (sherpa-onnx IndicConformer format) in the asset directory.
            val files = context.assets.list(dir) ?: return false
            files.any { it.endsWith(".onnx") }
        } catch (_: Exception) {
            false
        }

    private companion object {
        const val TAG = "SttModelManager"
    }
}
