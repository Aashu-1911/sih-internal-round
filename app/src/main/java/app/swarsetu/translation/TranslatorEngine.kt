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
     * Normalizes language codes/names ("english", "en", "hindi", "hi", "mr", "gu", "ta", "te", "kn", "bn", "ml", "or") to standard ML Kit tags.
     */
    fun normalizeToLanguageTag(lang: String?): String? {
        if (lang.isNullOrBlank()) return null
        val clean = lang.trim().lowercase().split("-", "_").first()
        return when (clean) {
            "en", "english" -> TranslateLanguage.ENGLISH
            "hi", "hindi" -> TranslateLanguage.HINDI
            "gu", "gujarati" -> TranslateLanguage.GUJARATI
            "mr", "marathi" -> TranslateLanguage.MARATHI
            "kn", "kannada" -> TranslateLanguage.KANNADA
            "ta", "tamil" -> TranslateLanguage.TAMIL
            "te", "telugu" -> TranslateLanguage.TELUGU
            "bn", "bengali" -> TranslateLanguage.BENGALI
            "ml", "malayalam" -> TranslateLanguage.HINDI // ML Kit on-device translation alias
            "or", "odia", "oriya" -> TranslateLanguage.HINDI // ML Kit on-device translation alias
            else -> TranslateLanguage.fromLanguageTag(clean) ?: TranslateLanguage.ENGLISH
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

    private val COMMON_ENGLISH_WORDS = setOf(
        "the", "this", "that", "there", "these", "those", "they", "them", "their",
        "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
        "please", "yes", "no", "okay", "ok", "good", "hello", "hey", "hi",
        "today", "tomorrow", "yesterday", "here", "now", "just", "very", "much",
        "help", "send", "come", "urgent", "danger", "safe", "need", "call",
        "is", "am", "are", "was", "were", "be", "been", "have", "has", "had",
        "do", "does", "did", "can", "could", "shall", "should", "will", "would", "may", "might", "must",
        "i", "you", "he", "she", "it", "we", "all", "some", "any", "every",
    )

    private val ENTITY_REGEX = Regex(
        """(@\w+)|(https?://\S+)|(\b\d{2,}\b)|(\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b)|(\bSwarSetu\b)|(\biTantra\b)|(\bISRO\b)|(\bNDRF\b)|(\bSDRF\b)|(\bSOS\b)""",
        RegexOption.IGNORE_CASE
    )

    private val NAME_INTRODUCTION_PATTERNS = listOf(
        Regex("""(?i)(?:मेरा\s+नाम|नाम\s+है|माझे\s+नाव|नाव\s+आहे)\s+([^\s,।!?]+)"""),
        Regex("""(?i)(?:मैं|मै|હું|હુ)\s+([^\s,।!?]+)\s+(?:हूँ|हु|हूं|છું)"""),
        Regex("""(?i)(?:મારું\s+નામ|મારુ\s+નામ|আমার\s+নাম)\s+([^\s,।!?]+)"""),
        Regex("""(?i)(?:என்\s+பெயர்|నా\s+పేరు|ನನ್ನ\s+ಹೆಸರು|എന്റെ\s+പേര്|ମୋର\s+ନାମ)\s+([^\s,।!?]+)"""),
        Regex("""(?i)(?:my\s+name\s+is|i\s+am|this\s+is|call\s+me)\s+([a-zA-Z]+)"""),
        Regex("""(?i)(?:Dr\.|Mr\.|Mrs\.|Ms\.|Shri|Smt|Sir|Madam|श्री|श्रीमती|डॉ|डॉक्टर|मिस्टर|मिस)\s+([^\s,।!?]+)"""),
        Regex("""(?i)([^\s,।!?]+)\s+(?:जी|भाई|साहेब|सर|मैडम|भाईजान)"""),
    )

    private data class MaskedText(
        val text: String,
        val replacements: Map<String, String>,
        val tagToOriginal: Map<String, String>,
    )

    private fun maskEntities(rawText: String, customProtectedNouns: List<String> = emptyList()): MaskedText {
        val replacements = mutableMapOf<String, String>()
        val tagToOriginal = mutableMapOf<String, String>()
        var counter = 0
        var processed = rawText

        val allNouns = customProtectedNouns.filter { it.isNotBlank() }.toMutableList()

        // 1. Extract names from conversational introduction and honorific patterns
        for (pattern in NAME_INTRODUCTION_PATTERNS) {
            val matches = pattern.findAll(rawText)
            for (match in matches) {
                if (match.groupValues.size > 1) {
                    val candidateName = match.groupValues[1].trim()
                    if (candidateName.length >= 2 && !allNouns.contains(candidateName)) {
                        allNouns.add(candidateName)
                    }
                }
            }
        }

        // 2. Extract capitalized proper nouns from English / Latin text (e.g. Ashish, Mumbai, Pune)
        val capitalizedWords = Regex("""\b[A-Z][a-zA-Z0-9_]{1,25}\b""").findAll(rawText)
        for (capMatch in capitalizedWords) {
            val word = capMatch.value
            if (word.lowercase() !in COMMON_ENGLISH_WORDS && !allNouns.contains(word)) {
                allNouns.add(word)
            }
        }

        // 3. Shield custom and dynamically extracted proper nouns
        for (noun in allNouns.distinct().sortedByDescending { it.length }) {
            val unicodeBoundaryRegex = Regex("""(?i)(?<=^|[\s\p{Punct}])${Regex.escape(noun)}(?=$|[\s\p{Punct}])""")
            if (unicodeBoundaryRegex.containsMatchIn(processed)) {
                val placeholder = "__N${counter}__"
                val match = unicodeBoundaryRegex.find(processed)
                if (match != null) {
                    replacements[placeholder] = match.value
                    tagToOriginal[counter.toString()] = match.value
                    processed = unicodeBoundaryRegex.replace(processed, placeholder)
                    counter++
                }
            } else if (processed.contains(noun, ignoreCase = true)) {
                val placeholder = "__N${counter}__"
                replacements[placeholder] = noun
                tagToOriginal[counter.toString()] = noun
                processed = processed.replace(noun, placeholder, ignoreCase = true)
                counter++
            }
        }

        // 4. Shield URLs, emails, mentions, numbers, and technical brand names
        processed = ENTITY_REGEX.replace(processed) { matchResult ->
            val placeholder = "__N${counter}__"
            replacements[placeholder] = matchResult.value
            tagToOriginal[counter.toString()] = matchResult.value
            counter++
            placeholder
        }

        return MaskedText(processed, replacements, tagToOriginal)
    }

    private fun unmaskEntities(translatedText: String, tagToOriginal: Map<String, String>): String {
        var result = translatedText
        for ((tagId, original) in tagToOriginal) {
            val lenientRegex = Regex(
                """(?:_{1,3}\s*(?:N|n|ID|id|P|p|entity|ENTITY|NOUN|noun)\s*[_–-]?\s*${Regex.escape(tagId)}\s*_{1,3})|""" +
                    """(?:⟦\s*(?:N|n|P|p|ID|id)?\s*${Regex.escape(tagId)}\s*⟧)|""" +
                    """(?:\[\s*(?:N|n|P|p|ID|id)?\s*${Regex.escape(tagId)}\s*\])|""" +
                    """(?:\(\s*(?:N|n|P|p|ID|id)?\s*${Regex.escape(tagId)}\s*\))|""" +
                    """(?:\b(?:N|n|ID|id|P|p|entity|ENTITY)_?${Regex.escape(tagId)}\b)|""" +
                    """(?:(?:एन|पि|पी|પી|பி|పి|ಪಿ|পি|ପି)\s*${Regex.escape(tagId)})""",
                RegexOption.IGNORE_CASE,
            )
            result = if (lenientRegex.containsMatchIn(result)) {
                lenientRegex.replace(result, original)
            } else {
                result.replace("__N${tagId}__", original, ignoreCase = true)
                    .replace("⟦P${tagId}⟧", original, ignoreCase = true)
                    .replace("[P${tagId}]", original, ignoreCase = true)
            }
        }
        return result
    }

    private fun preprocessText(input: String, sourceLang: String = ""): String {
        var trimmed = input.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed[0] in 'a'..'z') {
            trimmed = trimmed.replaceFirstChar { it.uppercase() }
        }
        // Ensure sentence has terminal punctuation so neural model analyzes full grammar context
        val lastChar = trimmed.last()
        if (lastChar !in listOf('.', '?', '!', '।', ',', ';')) {
            trimmed = if (sourceLang == TranslateLanguage.ENGLISH || sourceLang == "en") {
                "$trimmed."
            } else {
                "$trimmed ।"
            }
        }
        return trimmed
    }

    private fun postprocessText(input: String): String {
        return input
            .replace(Regex("""\s+([.,!?।])"""), "$1")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    /**
     * Infers ML Kit language code from Unicode script characteristics of the input text.
     */
    fun detectLanguageFromScript(text: String): String? {
        for (char in text) {
            when (char.code) {
                in 0x0A80..0x0AFF -> return TranslateLanguage.GUJARATI
                in 0x0980..0x09FF -> return TranslateLanguage.BENGALI
                in 0x0B80..0x0BFF -> return TranslateLanguage.TAMIL
                in 0x0C00..0x0C7F -> return TranslateLanguage.TELUGU
                in 0x0C80..0x0CFF -> return TranslateLanguage.KANNADA
                in 0x0D00..0x0D7F -> return TranslateLanguage.HINDI // Malayalam script -> mapped to Indic neural engine
                in 0x0B00..0x0B7F -> return TranslateLanguage.HINDI // Odia script -> mapped to Indic neural engine
                in 0x0900..0x097F -> return TranslateLanguage.HINDI // Devanagari script (Hindi / Marathi)
            }
        }
        if (text.any { it in 'a'..'z' || it in 'A'..'Z' } && text.none { it.code in 0x0900..0x0D7F }) {
            return TranslateLanguage.ENGLISH
        }
        return null
    }

    private suspend fun translateDirect(
        text: String,
        source: String,
        target: String,
    ): String {
        val translator = getOrCreateTranslator(source, target)
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
        return translator.translate(text).awaitTask() ?: text
    }

    private suspend fun translateDirectOrPivot(
        text: String,
        source: String,
        target: String,
    ): String {
        return if (source == TranslateLanguage.ENGLISH || target == TranslateLanguage.ENGLISH) {
            translateDirect(text, source, target)
        } else {
            // Pivot via English with robust intermediate check
            val englishIntermediate = translateDirect(text, source, TranslateLanguage.ENGLISH)
            if (englishIntermediate.isNotBlank() && englishIntermediate != text) {
                translateDirect(englishIntermediate, TranslateLanguage.ENGLISH, target)
            } else {
                translateDirect(text, source, target)
            }
        }
    }

    private suspend fun translatePassage(
        passage: String,
        source: String,
        target: String,
    ): String {
        val sentences = passage.split(Regex("""(?<=[।?!.\n])\s+""")).filter { it.isNotBlank() }
        if (sentences.size <= 1) {
            return translateDirectOrPivot(passage, source, target)
        }
        val translatedSentences = sentences.map { sentence ->
            translateDirectOrPivot(sentence.trim(), source, target)
        }
        return translatedSentences.joinToString(" ")
    }

    /**
     * Translates text from [sourceLang] to [targetLang].
     * Supports noun/entity preservation and English pivot translation between Indian language pairs.
     */
    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String = translate(text, sourceLang, targetLang, emptyList())

    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
        protectedNouns: List<String> = emptyList(),
    ): String {
        if (text.isBlank()) return text
        if (sourceLang == targetLang) return text

        var detectedSource = normalizeToLanguageTag(sourceLang)
        val target = normalizeToLanguageTag(targetLang) ?: return text

        // Reconcile source language against actual Unicode script to prevent cross-language distortion
        val scriptDetected = detectLanguageFromScript(text)
        if (scriptDetected != null && (detectedSource == null || (detectedSource == TranslateLanguage.ENGLISH && scriptDetected != TranslateLanguage.ENGLISH))) {
            detectedSource = scriptDetected
        }
        val source = detectedSource ?: scriptDetected ?: TranslateLanguage.ENGLISH

        if (source == target) return text

        return try {
            // 1. Mask proper nouns, mentions, URLs, numbers
            val (masked, _, tagToOriginal) = maskEntities(text, protectedNouns)

            // 2. Preprocess sentence structure
            val preprocessed = preprocessText(masked, source)

            // 3. Execute translation (Direct for English pairs; Pivot via English for Indic-to-Indic pairs)
            val translated = translatePassage(preprocessed, source, target)

            // 4. Restore preserved entities and clean up syntax
            val unmasked = unmaskEntities(translated, tagToOriginal)
            postprocessText(unmasked)
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
                Log.w("TranslatorEngine", "Translation failed for $sourceLang ($source)→$targetLang ($target): ${e.message}", e)
            }
            text
        } finally {
            // Translation cycle finished
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
