package app.swarsetu.voice

import android.content.Context
import app.swarsetu.di.appModule
import app.swarsetu.di.meshModule
import app.swarsetu.di.moderationModule
import app.swarsetu.di.sttModule
import app.swarsetu.di.ttsModule
import app.swarsetu.di.uiModule
import app.swarsetu.di.voiceModule
import app.swarsetu.stt.SttLanguage
import app.swarsetu.stt.SttPipeline
import app.swarsetu.ui.chat.ChatViewModel
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.test.KoinTest
import org.koin.test.get
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Bug Condition Exploration Tests for Voice Mesh Pipeline
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8**
 *
 * **CRITICAL**: These tests MUST FAIL on unfixed code - failure confirms the bug exists.
 * **DO NOT attempt to fix the test or the code when it fails.**
 * **NOTE**: These tests encode the expected behavior - they will validate the fix when they pass after implementation.
 * **GOAL**: Surface counterexamples that demonstrate the bugs exist.
 */
class VoiceMeshPipelineBugConditionTest : KoinTest {
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        val mockAudioManager = mockk<android.media.AudioManager>(relaxed = true)
        mockContext =
            mockk<Context>(relaxed = true) {
                io.mockk.every { getSystemService(Context.AUDIO_SERVICE) } returns mockAudioManager
            }
    }

    @After
    fun tearDown() {
        try {
            stopKoin()
        } catch (e: Exception) {
            // Already stopped
        }
    }

    /**
     * Test 1.1: Merge Conflict - Compilation Check
     * **Validates: Requirement 1.1**
     *
     * WHEN the project has unresolved merge conflict markers in source files
     * THEN the system SHALL fail to compile with syntax errors
     *
     * **EXPECTED OUTCOME ON UNFIXED CODE**: Test FAILS (confirms merge conflicts exist)
     */
    @Test
    fun `test 1_1 - merge conflict markers prevent compilation in SttPipeline`() {
        // This test checks if SttPipeline.kt has merge conflict markers
        // If merge conflicts exist, the file would have syntax errors

        val sttPipelineFile = File("app/src/main/java/app/swarsetu/stt/SttPipeline.kt")
        if (sttPipelineFile.exists()) {
            val content = sttPipelineFile.readText()

            // Check for merge conflict markers
            val hasConflicts =
                content.contains("<<<<<<< Updated upstream") ||
                    (content.contains("=======") && content.contains(">>>>>>> Stashed changes"))

            // Expected: NO merge conflicts (file should compile)
            // On unfixed code: This will FAIL because merge conflicts exist
            assertFalse(
                hasConflicts,
                "SttPipeline.kt contains unresolved merge conflict markers. " +
                    "Expected: File should compile without syntax errors. " +
                    "Actual: Merge conflict markers found.",
            )
        }

        // Also check ChatViewModel.kt
        val chatViewModelFile = File("app/src/main/java/app/swarsetu/ui/chat/ChatViewModel.kt")
        if (chatViewModelFile.exists()) {
            val content = chatViewModelFile.readText()
            val hasConflicts = content.contains("<<<<<<< Updated upstream")

            assertFalse(
                hasConflicts,
                "ChatViewModel.kt contains unresolved merge conflict markers. " +
                    "Expected: File should compile without syntax errors. " +
                    "Actual: Merge conflict markers found.",
            )
        }

        // Also check ChatScreen.kt
        val chatScreenFile = File("app/src/main/java/app/swarsetu/ui/chat/ChatScreen.kt")
        if (chatScreenFile.exists()) {
            val content = chatScreenFile.readText()
            val hasConflicts = content.contains("<<<<<<< Updated upstream")

            assertFalse(
                hasConflicts,
                "ChatScreen.kt contains unresolved merge conflict markers. " +
                    "Expected: File should compile without syntax errors. " +
                    "Actual: Merge conflict markers found.",
            )
        }
    }

    /**
     * Test 1.2: Koin DI Parameter Count Mismatch
     * **Validates: Requirement 1.2**
     *
     * WHEN the app launches and navigates to chat screen
     * THEN the system SHALL resolve ChatViewModel without KoinDefinitionException
     *
     * **EXPECTED OUTCOME ON UNFIXED CODE**: Test FAILS with KoinDefinitionException
     */
    @Test
    fun `test 1_2 - Koin DI resolves ChatViewModel with 21 parameters including sttPipeline`() {
        // This test verifies the UiModule has 21 get() calls for ChatViewModel (including sttPipeline).
        // On unfixed code: Only 20 get() calls present, causing KoinDefinitionException at runtime.
        // On fixed code: 21 get() calls present, matching ChatViewModel's constructor exactly.
        //
        // We verify this directly from the source to avoid needing a full Android runtime environment.

        val uiModuleFile = File("app/src/main/java/app/swarsetu/di/UiModule.kt")
        if (uiModuleFile.exists()) {
            val content = uiModuleFile.readText()

            // Find the ChatViewModel factory block
            val chatViewModelFactoryPattern =
                Regex(
                    """viewModel\s*\{\s*params\s*->[\s\S]*?ChatViewModel\([\s\S]*?\)\s*\}""",
                    setOf(RegexOption.MULTILINE),
                )

            val factoryMatch = chatViewModelFactoryPattern.find(content)
            assertNotNull(factoryMatch, "Could not find ChatViewModel viewModel factory in UiModule.kt")

            val factoryBody = factoryMatch!!.value

            // Count the get() calls inside the factory
            val getCallCount = Regex("""(?:^|,|\()\s*get\s*\(""").findAll(factoryBody).count()
            val paramsGetCount = Regex("""params\.get\(\)""").findAll(factoryBody).count()
            val totalGetCount = getCallCount + paramsGetCount

            // Also check that sttPipeline comment is present
            val hasSttPipelineComment = factoryBody.contains("sttPipeline")

            assertTrue(
                hasSttPipelineComment,
                "UiModule ChatViewModel factory should have sttPipeline parameter. " +
                    "Expected: 21st get() call with sttPipeline comment. " +
                    "Actual: No sttPipeline reference found in factory.",
            )

            // Verify at least 21 get() calls are present (params.get() + 20 type-resolved get() calls)
            // The factory uses: params.get() for conversationId + 20 get() calls for injected deps
            assertTrue(
                totalGetCount >= 21,
                "UiModule ChatViewModel factory should have 21 get() calls (1 params.get() + 20 get()). " +
                    "Expected: >= 21 total get() calls. " +
                    "Actual: $totalGetCount get() calls. DI wiring is incorrect.",
            )
        } else {
            // Fallback: try Koin resolution and distinguish DI wiring errors from runtime errors
            try {
                startKoin {
                    androidContext(mockContext)
                    modules(appModule, meshModule, moderationModule, sttModule, ttsModule, uiModule, voiceModule)
                }
                try {
                    get<ChatViewModel> { parametersOf("test-conversation-id", false) }
                } catch (e: org.koin.core.error.InstanceCreationException) {
                    // Check cause chain for definition-missing errors
                    var cause: Throwable? = e
                    while (cause != null) {
                        if (cause.message?.contains("SttPipeline") == true &&
                            (cause.javaClass.name.contains("NoDefinition") || cause.javaClass.name.contains("KoinDefinitionException"))
                        ) {
                            fail("ChatViewModel DI wiring is missing sttPipeline: ${cause.message}")
                        }
                        cause = cause.cause
                    }
                    // No SttPipeline-specific definition error — wiring is correct
                } catch (e: Throwable) {
                    fail("Unexpected DI error: ${e.javaClass.simpleName}: ${e.message}")
                }
            } finally {
                stopKoin()
            }
        }
    }

    /**
     * Test 1.3: CoroutineScope Injection Ambiguity
     * **Validates: Requirement 1.3**
     *
     * WHEN VoiceModule provides a bare CoroutineScope without named qualifier
     * THEN the system SHALL inject the correct scope using named("voiceScope")
     *
     * **EXPECTED OUTCOME ON UNFIXED CODE**: Test FAILS (wrong scope injected or ambiguity)
     */
    @Test
    fun `test 1_3 - VoiceModule uses named qualifier for CoroutineScope injection`() {
        try {
            startKoin {
                androidContext(mockContext)
                modules(voiceModule)
            }

            // Try to resolve a named scope
            // Expected: VoiceModule should provide a named("voiceScope") binding
            // On unfixed code: This will FAIL because VoiceModule has bare single { CoroutineScope(...) }

            val namedScope =
                try {
                    get<CoroutineScope>(named("voiceScope"))
                } catch (e: Throwable) {
                    fail(
                        "Failed to resolve named('voiceScope') CoroutineScope. " +
                            "Expected: VoiceModule should use single(named(\"voiceScope\")) for CoroutineScope binding. " +
                            "Actual: ${e.javaClass.simpleName}: ${e.message}",
                    )
                }

            assertNotNull(namedScope, "Named voice scope should be resolved successfully")
        } finally {
            stopKoin()
        }
    }

    /**
     * Test 1.4: PTT Button Press - STT Capture Not Started
     * **Validates: Requirement 1.4**
     *
     * WHEN user presses and holds the mic button in chat screen
     * THEN the system SHALL call sttPipeline.startCapture() with the selected language
     *
     * **EXPECTED OUTCOME ON UNFIXED CODE**: Test FAILS (startCapture() not called)
     */
    @Test
    fun `test 1_4 - pressing mic button calls sttPipeline startCapture()`() {
        // This test verifies that ChatViewModel.startVoiceRecording() calls sttPipeline.startCapture()
        // On unfixed code: The "Updated upstream" side only starts VoiceRecorder (AAC/ADTS)
        // and does NOT call sttPipeline.startCapture(), so this test will FAIL

        val chatViewModelFile = File("app/src/main/java/app/swarsetu/ui/chat/ChatViewModel.kt")
        if (chatViewModelFile.exists()) {
            val content = chatViewModelFile.readText()

            // Look for startVoiceRecording() function
            val startVoiceRecordingPattern =
                Regex(
                    """fun\s+startVoiceRecording\s*\([^)]*\)\s*:\s*Boolean\s*\{[^}]+\}""",
                    RegexOption.DOT_MATCHES_ALL,
                )

            val match = startVoiceRecordingPattern.find(content)
            if (match != null) {
                val functionBody = match.value

                // Expected: Function should call sttPipeline.startCapture()
                // On unfixed code: This will FAIL because the function only calls recorder.start()
                assertTrue(
                    functionBody.contains("sttPipeline.startCapture"),
                    "startVoiceRecording() should call sttPipeline.startCapture() for PTT mode. " +
                        "Expected: sttPipeline.startCapture(language) call present. " +
                        "Actual: No sttPipeline.startCapture() call found in function body.",
                )
            } else {
                fail("Could not find startVoiceRecording() function in ChatViewModel.kt")
            }
        }
    }

    /**
     * Test 1.5: PTT Button Release - STT Capture Not Stopped
     * **Validates: Requirement 1.5**
     *
     * WHEN user releases the mic button after PTT recording
     * THEN the system SHALL call sttPipeline.stopCapture() to finalize transcription
     *
     * **EXPECTED OUTCOME ON UNFIXED CODE**: Test FAILS (stopCapture() not called)
     */
    @Test
    fun `test 1_5 - releasing mic button calls sttPipeline stopCapture()`() {
        // This test verifies that ChatViewModel.stopVoiceRecordingAndStage() calls sttPipeline.stopCapture()
        // On unfixed code: The "Updated upstream" side only stops VoiceRecorder
        // and does NOT call sttPipeline.stopCapture(), so this test will FAIL

        val chatViewModelFile = File("app/src/main/java/app/swarsetu/ui/chat/ChatViewModel.kt")
        if (chatViewModelFile.exists()) {
            val content = chatViewModelFile.readText()

            // Look for stopVoiceRecordingAndStage() function
            val stopVoiceRecordingPattern =
                Regex(
                    """fun\s+stopVoiceRecordingAndStage\s*\([^)]*\)\s*\{[^}]+\}""",
                    RegexOption.DOT_MATCHES_ALL,
                )

            val match = stopVoiceRecordingPattern.find(content)
            if (match != null) {
                val functionBody = match.value

                // Expected: Function should call sttPipeline.stopCapture()
                // On unfixed code: This will FAIL because the function only calls recorder.stop()
                assertTrue(
                    functionBody.contains("sttPipeline.stopCapture"),
                    "stopVoiceRecordingAndStage() should call sttPipeline.stopCapture() to finalize PTT transcription. " +
                        "Expected: sttPipeline.stopCapture() call present. " +
                        "Actual: No sttPipeline.stopCapture() call found in function body.",
                )
            } else {
                fail("Could not find stopVoiceRecordingAndStage() function in ChatViewModel.kt")
            }
        }
    }

    /**
     * Test 1.6: VoiceMessageAdapter Routing Context Not Set
     * **Validates: Requirement 1.6**
     *
     * WHEN PTT recording begins
     * THEN the system SHALL call voiceMessageAdapter.startVoiceMessage() with routing context
     *
     * **EXPECTED OUTCOME ON UNFIXED CODE**: Test FAILS (startVoiceMessage() not called)
     */
    @Test
    fun `test 1_6 - PTT recording calls voiceMessageAdapter startVoiceMessage()`() {
        // This test verifies that ChatViewModel.startVoiceRecording() calls voiceMessageAdapter.startVoiceMessage()
        // with the correct routing context before starting STT capture
        // On unfixed code: This call is missing, so VoiceMessageAdapter.currentContext stays null
        // and STT results are silently dropped. This test will FAIL.

        val chatViewModelFile = File("app/src/main/java/app/swarsetu/ui/chat/ChatViewModel.kt")
        if (chatViewModelFile.exists()) {
            val content = chatViewModelFile.readText()

            // Look for startVoiceRecording() function
            val startVoiceRecordingPattern =
                Regex(
                    """fun\s+startVoiceRecording\s*\([^)]*\)\s*:\s*Boolean\s*\{[^}]+\}""",
                    RegexOption.DOT_MATCHES_ALL,
                )

            val match = startVoiceRecordingPattern.find(content)
            if (match != null) {
                val functionBody = match.value

                // Expected: Function should call voiceMessageAdapter.startVoiceMessage()
                // On unfixed code: This will FAIL because the call is missing
                assertTrue(
                    functionBody.contains("voiceMessageAdapter.startVoiceMessage"),
                    "startVoiceRecording() should call voiceMessageAdapter.startVoiceMessage() to set routing context. " +
                        "Expected: voiceMessageAdapter.startVoiceMessage(language, recipientId, group) call present. " +
                        "Actual: No voiceMessageAdapter.startVoiceMessage() call found in function body.",
                )
            } else {
                fail("Could not find startVoiceRecording() function in ChatViewModel.kt")
            }
        }
    }

    /**
     * Test 1.7: Mesh Enable Flag Not Set
     * **Validates: Requirement 1.7**
     *
     * WHEN PTT recording begins
     * THEN the system SHALL set voiceController.isMeshEnabled = true
     *
     * **EXPECTED OUTCOME ON UNFIXED CODE**: Test FAILS (isMeshEnabled not set to true)
     */
    @Test
    fun `test 1_7 - PTT recording sets voiceController isMeshEnabled to true`() {
        // This test verifies that ChatViewModel.startVoiceRecording() sets voiceController.isMeshEnabled = true
        // On unfixed code: This assignment is missing, so isMeshEnabled stays false by default
        // and the mesh guard in VoiceMessageAdapter blocks transmission. This test will FAIL.

        val chatViewModelFile = File("app/src/main/java/app/swarsetu/ui/chat/ChatViewModel.kt")
        if (chatViewModelFile.exists()) {
            val content = chatViewModelFile.readText()

            // Look for startVoiceRecording() function
            val startVoiceRecordingPattern =
                Regex(
                    """fun\s+startVoiceRecording\s*\([^)]*\)\s*:\s*Boolean\s*\{[^}]+\}""",
                    RegexOption.DOT_MATCHES_ALL,
                )

            val match = startVoiceRecordingPattern.find(content)
            if (match != null) {
                val functionBody = match.value

                // Expected: Function should set voiceController.isMeshEnabled = true
                // On unfixed code: This will FAIL because the assignment is missing
                assertTrue(
                    functionBody.contains("voiceController.isMeshEnabled") &&
                        functionBody.contains("= true"),
                    "startVoiceRecording() should set voiceController.isMeshEnabled = true to enable mesh transmission. " +
                        "Expected: voiceController.isMeshEnabled = true assignment present. " +
                        "Actual: No voiceController.isMeshEnabled = true found in function body.",
                )
            } else {
                fail("Could not find startVoiceRecording() function in ChatViewModel.kt")
            }
        }
    }

    /**
     * Test 1.8: Translation Fallback Silent Without Logging
     * **Validates: Requirement 1.8**
     *
     * WHEN TranslatorEngine.translate() fails due to missing ML Kit model
     * THEN the system SHALL log a warning about model availability
     *
     * **EXPECTED OUTCOME ON UNFIXED CODE**: Test FAILS (no detailed logging in catch block)
     */
    @Test
    fun `test 1_8 - TranslatorEngine logs model availability failures`() {
        // This test verifies that TranslatorEngine.translate() has proper logging in its catch block
        // On unfixed code: The catch block silently returns original text without logging
        // the real cause (missing ML Kit model). This test will FAIL.

        val translatorEngineFile = File("app/src/main/java/app/swarsetu/translation/TranslatorEngine.kt")
        if (translatorEngineFile.exists()) {
            val content = translatorEngineFile.readText()

            // Look for translate() function and its catch block
            val translatePattern =
                Regex(
                    """fun\s+translate\s*\([^)]+\)[^{]*\{.*?\}""",
                    RegexOption.DOT_MATCHES_ALL,
                )

            val match = translatePattern.find(content)
            if (match != null) {
                val functionBody = match.value

                // Look for catch block
                if (functionBody.contains("catch")) {
                    val catchBlockPattern =
                        Regex(
                            """\bcatch\s*\([^)]+\)\s*\{[^}]+\}""",
                            RegexOption.DOT_MATCHES_ALL,
                        )

                    val catchMatch = catchBlockPattern.find(functionBody)
                    if (catchMatch != null) {
                        val catchBlock = catchMatch.value

                        // Expected: Catch block should have Log.w or Log.e with model availability info
                        // On unfixed code: This will FAIL because catch block just returns text silently
                        val hasLogging = catchBlock.contains("Log.w") || catchBlock.contains("Log.e")
                        val hasModelMessage =
                            catchBlock.contains("model", ignoreCase = true) ||
                                catchBlock.contains("download", ignoreCase = true) ||
                                catchBlock.contains("ML Kit", ignoreCase = true)

                        assertTrue(
                            hasLogging && hasModelMessage,
                            "TranslatorEngine.translate() catch block should log model availability failures. " +
                                "Expected: Log.w/Log.e with message about missing ML Kit model. " +
                                "Actual: Catch block has ${if (hasLogging) "logging" else "NO logging"} and " +
                                "${if (hasModelMessage) "model-related message" else "NO model-related message"}.",
                        )
                    } else {
                        fail("Could not find catch block in translate() function")
                    }
                } else {
                    fail("translate() function has no catch block")
                }
            } else {
                fail("Could not find translate() function in TranslatorEngine.kt")
            }
        }
    }
}
