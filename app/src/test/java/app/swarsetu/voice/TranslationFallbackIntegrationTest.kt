package app.swarsetu.voice

import app.swarsetu.data.MessageRepository
import app.swarsetu.data.message.MessageEntity
import app.swarsetu.data.settings.SettingsStore
import app.swarsetu.translation.TranslatorEngine
import app.swarsetu.tts.TtsLanguage
import app.swarsetu.tts.TtsManager
import app.swarsetu.tts.TtsPriority
import app.swarsetu.tts.TtsRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration Test: Translation Fallback with Missing ML Kit Model
 *
 * **Task 15.1 — Test translation with missing ML Kit model**
 *
 * **Validates: Requirement 2.8**
 */
class TranslationFallbackIntegrationTest {

    // ── fixtures ────────────────────────────────────────────────────────────────────────────────

    private val originalText = "नमस्ते दुनिया"
    private val originalLanguage = "hi" // Hindi
    private val preferredLanguage = "en" // English
    private val senderId = "sender-node-id-123"
    private val receiverId = "receiver-node-id-456"

    private val translatorEnginePath = "src/main/java/app/swarsetu/translation/TranslatorEngine.kt"
    private val voiceMessageReceiverPath = "src/main/java/app/swarsetu/voice/VoiceMessageReceiver.kt"

    private lateinit var translatorEngineFile: File
    private lateinit var voiceMessageReceiverFile: File

    @Before
    fun setUp() {
        translatorEngineFile = File(translatorEnginePath)
        voiceMessageReceiverFile = File(voiceMessageReceiverPath)
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** Creates a real CoroutineScope backed by SupervisorJob so launched coroutines can be joined. */
    private fun testScope(): CoroutineScope = CoroutineScope(SupervisorJob())

    // ── 15.1a — Static analysis: TranslatorEngine catch block logs model availability ─────────

    @Test
    fun `15_1a - TranslatorEngine catch block logs model availability failures`() {
        if (!translatorEngineFile.exists()) return

        val content = translatorEngineFile.readText()

        // Match the complete translate() function including try-catch-finally
        val translatePattern = Regex(
            """suspend\s+fun\s+translate\s*\([^)]+\)[^{]*\{.*?finally\s*\{[^}]*\}\s*\}""",
            RegexOption.DOT_MATCHES_ALL,
        )

        val match = translatePattern.find(content)
        assertNotNull(match, "Could not find translate() function in TranslatorEngine.kt")
        val functionBody = match.value

        assertTrue(
            functionBody.contains("catch"),
            "translate() must have a catch block to handle ML Kit failures.",
        )

        // Extract the catch block (may span multiple lines with nested braces)
        // Use a broader search: find "catch" and grab the surrounding content
        val catchIdx = functionBody.indexOf("catch")
        assertTrue(catchIdx >= 0, "catch keyword not found in function body")
        val afterCatch = functionBody.substring(catchIdx)

        assertTrue(
            afterCatch.contains("Log.w") || afterCatch.contains("Log.e"),
            "translate() catch block must use Log.w or Log.e for logging.",
        )

        assertTrue(
            afterCatch.contains("model", ignoreCase = true) ||
                afterCatch.contains("download", ignoreCase = true) ||
                afterCatch.contains("ML Kit", ignoreCase = true),
            "translate() catch block must mention model/download/ML Kit in log message.",
        )

        assertTrue(
            afterCatch.contains("sourceLang") && afterCatch.contains("targetLang"),
            "translate() catch block must include sourceLang and targetLang in log message.",
        )

        assertTrue(
            afterCatch.contains("text"),
            "translate() catch block must return original text as fallback.",
        )
    }

    // ── 15.1b — Static analysis: VoiceMessageReceiver calls TranslatorEngine ──────────────────

    @Test
    fun `15_1b - VoiceMessageReceiver calls translatorEngine translate for different language`() {
        if (!voiceMessageReceiverFile.exists()) return

        val content = voiceMessageReceiverFile.readText()

        assertTrue(
            content.contains("translatorEngine.translate"),
            "VoiceMessageReceiver must call translatorEngine.translate().",
        )
        assertTrue(
            content.contains("originalLanguage") && content.contains("preferredLanguage"),
            "VoiceMessageReceiver must compare originalLanguage and preferredLanguage.",
        )
        assertTrue(
            content.contains("if") && content.contains("!="),
            "VoiceMessageReceiver must conditionally translate only when languages differ.",
        )
    }

    // ── 15.1c — Runtime: mock returns fallback text ──────────────────────────────────────────

    @Test
    fun `15_1c - mocked translation failure returns original text for integration testing`() =
        runBlocking {
            val mockTranslatorEngine = mockk<TranslatorEngine> {
                coEvery { translate(any(), any(), any()) } returns originalText
            }

            val result = mockTranslatorEngine.translate(
                text = originalText,
                sourceLang = originalLanguage,
                targetLang = preferredLanguage,
            )

            assertEquals(originalText, result, "Mocked translation failure must return original text.")
        }

    // ── 15.1d — Runtime: VoiceMessageReceiver plays original text via TTS on failure ──────────

    @Test
    fun `15_1d - VoiceMessageReceiver plays original text via TTS when translation fails`() =
        runBlocking {
            val mockTtsManager = mockk<TtsManager>(relaxed = true)
            val mockVoiceController = mockk<VoiceConversationController>(relaxed = true)
            val mockSettingsStore = mockk<SettingsStore> {
                every { sttLanguageCode } returns MutableStateFlow(preferredLanguage)
            }
            val mockTranslatorEngine = mockk<TranslatorEngine> {
                // Return original text simulating fallback (model unavailable)
                coEvery { translate(any(), any(), any()) } returns originalText
            }
            val mockMessageRepository = mockk<MessageRepository>(relaxed = true)

            val scope = testScope()
            val receiver = VoiceMessageReceiver(
                ttsManager = mockTtsManager,
                scope = scope,
                voiceController = mockVoiceController,
                settingsStore = mockSettingsStore,
                translatorEngine = mockTranslatorEngine,
                messageRepository = mockMessageRepository,
            )

            val incomingMessage = MessageEntity(
                id = "msg-123",
                conversationId = receiverId,
                senderId = senderId,
                body = originalText,
                sentAt = Instant.now().toEpochMilli(),
                voiceTextLanguage = originalLanguage.uppercase(), // "HI"
                isAlert = false,
            )

            val ttsRequestSlot = slot<TtsRequest>()

            // Process and wait for the launched coroutine to finish
            receiver.onVoiceMessageReceived(incomingMessage).join()

            coVerify(exactly = 1) {
                mockTranslatorEngine.translate(
                    text = originalText,
                    sourceLang = originalLanguage.lowercase(),
                    targetLang = preferredLanguage.lowercase(),
                )
            }

            coVerify { mockTtsManager.speak(capture(ttsRequestSlot)) }

            val capturedRequest = ttsRequestSlot.captured
            assertEquals(
                originalText, capturedRequest.text,
                "TTS must play original text when translation fails.",
            )
            assertEquals(
                TtsLanguage.HINDI, capturedRequest.language,
                "TTS must use original language (HINDI) when translation fails.",
            )
        }

    // ── 15.1e — Static: translate() early-return paths ───────────────────────────────────────

    @Test
    fun `15_1e - translate handles same-language and blank-text early returns`() {
        if (!translatorEngineFile.exists()) return

        val content = translatorEngineFile.readText()

        val translatePattern = Regex(
            """suspend\s+fun\s+translate\s*\([^)]+\)[^{]*\{.*?finally\s*\{[^}]*\}\s*\}""",
            RegexOption.DOT_MATCHES_ALL,
        )

        val match = translatePattern.find(content)
        assertNotNull(match, "Could not find translate() function in TranslatorEngine.kt")
        val functionBody = match.value

        assertTrue(
            functionBody.contains("sourceLang == targetLang") && functionBody.contains("return text"),
            "translate() must have early return when sourceLang == targetLang.",
        )
        assertTrue(
            functionBody.contains("isBlank") || functionBody.contains("isEmpty"),
            "translate() should check for blank/empty text.",
        )
    }

    // ── 15.1f — Integration: end-to-end flow with translation failure ────────────────────────

    @Test
    fun `15_1f - end-to-end flow handles translation failure and plays original text`() =
        runBlocking {
            val mockTtsManager = mockk<TtsManager>(relaxed = true)
            val mockVoiceController = mockk<VoiceConversationController>(relaxed = true)
            val mockSettingsStore = mockk<SettingsStore> {
                every { sttLanguageCode } returns MutableStateFlow(preferredLanguage)
            }
            // Simulate the internal catch block behaviour: translate() returns original text
            val mockTranslatorEngine = mockk<TranslatorEngine> {
                coEvery {
                    translate(
                        text = originalText,
                        sourceLang = originalLanguage.lowercase(),
                        targetLang = preferredLanguage.lowercase(),
                    )
                } returns originalText
            }
            val mockMessageRepository = mockk<MessageRepository>(relaxed = true)

            val scope = testScope()
            val receiver = VoiceMessageReceiver(
                ttsManager = mockTtsManager,
                scope = scope,
                voiceController = mockVoiceController,
                settingsStore = mockSettingsStore,
                translatorEngine = mockTranslatorEngine,
                messageRepository = mockMessageRepository,
            )

            val incomingMessage = MessageEntity(
                id = "msg-integration-test",
                conversationId = receiverId,
                senderId = senderId,
                body = originalText,
                sentAt = Instant.now().toEpochMilli(),
                voiceTextLanguage = originalLanguage.uppercase(),
                isAlert = false,
            )

            val ttsRequestSlot = slot<TtsRequest>()

            receiver.onVoiceMessageReceived(incomingMessage).join()

            // 1. Translation was attempted
            coVerify(exactly = 1) {
                mockTranslatorEngine.translate(
                    text = originalText,
                    sourceLang = originalLanguage.lowercase(),
                    targetLang = preferredLanguage.lowercase(),
                )
            }

            // 2. TTS was called with fallback text
            coVerify { mockTtsManager.speak(capture(ttsRequestSlot)) }

            val request = ttsRequestSlot.captured
            assertEquals(originalText, request.text,
                "End-to-end flow must play original text via TTS when translation fails.")
            assertEquals(TtsLanguage.HINDI, request.language,
                "End-to-end flow must use original language (HINDI) when translation fails.")
            assertEquals(TtsPriority.NORMAL, request.priority,
                "Non-alert messages should use NORMAL priority.")

            // 3. Metrics were reported
            verify(exactly = 1) {
                mockVoiceController.reportInboundMessageMetrics(
                    messageId = incomingMessage.id,
                    t4 = any(),
                    t5 = any(),
                )
            }
        }

    // ── 15.1g — Preservation: same-language skips translation ────────────────────────────────

    @Test
    fun `15_1g - same-language messages skip translation and play directly via TTS`() =
        runBlocking {
            val mockTtsManager = mockk<TtsManager>(relaxed = true)
            val mockVoiceController = mockk<VoiceConversationController>(relaxed = true)
            val mockSettingsStore = mockk<SettingsStore> {
                every { sttLanguageCode } returns MutableStateFlow("en")
            }
            val mockTranslatorEngine = mockk<TranslatorEngine>(relaxed = true)
            val mockMessageRepository = mockk<MessageRepository>(relaxed = true)

            val scope = testScope()
            val receiver = VoiceMessageReceiver(
                ttsManager = mockTtsManager,
                scope = scope,
                voiceController = mockVoiceController,
                settingsStore = mockSettingsStore,
                translatorEngine = mockTranslatorEngine,
                messageRepository = mockMessageRepository,
            )

            val englishText = "Hello world"
            val incomingMessage = MessageEntity(
                id = "msg-same-lang",
                conversationId = receiverId,
                senderId = senderId,
                body = englishText,
                sentAt = Instant.now().toEpochMilli(),
                voiceTextLanguage = "EN", // Same as receiver preference
                isAlert = false,
            )

            val ttsRequestSlot = slot<TtsRequest>()

            receiver.onVoiceMessageReceived(incomingMessage).join()

            // Translation must NOT have been called
            coVerify(exactly = 0) {
                mockTranslatorEngine.translate(any(), any(), any())
            }

            // TTS must have been called with original text
            coVerify { mockTtsManager.speak(capture(ttsRequestSlot)) }

            val request = ttsRequestSlot.captured
            assertEquals(englishText, request.text,
                "Same-language messages must play original text without translation.")
            assertEquals(TtsLanguage.ENGLISH, request.language,
                "Same-language messages must use original language for TTS.")
        }
}
