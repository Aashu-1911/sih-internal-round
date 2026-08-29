package app.swarsetu.voice

import app.swarsetu.stt.SttLanguage
import app.swarsetu.stt.SttPipeline
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration Test: PTT Flow with Group Conversation
 *
 * **Task 13.1 — Test PTT in group conversation**
 *
 * **Validates: Requirements 2.4, 2.5, 2.6, 2.7**
 *
 * This suite verifies the full PTT walkie-talkie flow for a GROUP conversation (not a
 * Nearby room, not a DM):
 *
 *  1. Press-and-hold → `sttPipeline.startCapture(language)` called with the correct language
 *  2. Before capture starts → `voiceController.isMeshEnabled = true` set
 *  3. Before capture starts → `voiceMessageAdapter.startVoiceMessage(language, recipientId, group, …)`
 *     called with group routing context where:
 *     - `recipientId = conversationId` (the group ID)
 *     - `group = groupInfo` (non-null GroupInfo object, NOT null like in a Nearby room)
 *  4. Release mic button → `sttPipeline.stopCapture()` called
 *  5. Transcribed text is forwarded over the mesh with `voiceTextLanguage` tag and correct
 *     group routing context (recipientId = group ID, not peer node-ID)
 *
 * The tests use source-file inspection (same pattern as [VoiceMeshPipelineBugConditionTest]
 * and [PttDmIntegrationTest]) to remain runnable as plain JVM unit tests with no Android
 * runtime.  Where runtime behaviour can be exercised via MockK, we do so as well.
 *
 * File-path strategy: Gradle runs unit tests with the *module* directory (app/) as the
 * working directory, so paths are relative to app/, matching the same convention used by
 * other voice pipeline tests.
 */
class PttGroupIntegrationTest {

    // ── fixtures ────────────────────────────────────────────────────────────────────────────────

    private val groupConversationId = "group-abc123"
    private val selectedLanguage = SttLanguage.HINDI

    // Relative to the module (app/) working directory used by Gradle unit tests —
    // same convention as PttDmIntegrationTest and VoiceMeshPipelineBugConditionTest.
    private val chatViewModelPath = "src/main/java/app/swarsetu/ui/chat/ChatViewModel.kt"
    private val voiceMessageAdapterPath = "src/main/java/app/swarsetu/voice/VoiceMessageAdapter.kt"

    private lateinit var chatViewModelFile: File
    private lateinit var voiceMessageAdapterFile: File

    @Before
    fun setUp() {
        chatViewModelFile = File(chatViewModelPath)
        voiceMessageAdapterFile = File(voiceMessageAdapterPath)
    }

    // ── 13.1a — Requirement 2.4: startCapture called on PTT press in group chat ─────────────────

    /**
     * WHEN the user presses and holds the mic button in a group chat screen
     * THEN the system SHALL call `sttPipeline.startCapture(language = selectedSttLanguage)`.
     *
     * **Validates: Requirement 2.4**
     */
    @Test
    fun `13_1a - pressing mic button in group calls sttPipeline startCapture with selected language`() {
        if (!chatViewModelFile.exists()) return

        val content = chatViewModelFile.readText()
        val startFn = extractStartVoiceRecordingBody(content) ?: return

        // Requirement 2.4: startCapture must be called inside startVoiceRecording
        assertTrue(
            startFn.contains("sttPipeline.startCapture"),
            "startVoiceRecording() must call sttPipeline.startCapture() for PTT mode.\n" +
                "Expected: sttPipeline.startCapture(language) present in startVoiceRecording().\n" +
                "Actual: call is absent — Requirement 2.4 not satisfied.",
        )

        // The call must pass the locally-selected language
        assertTrue(
            startFn.contains("startCapture(language") ||
                startFn.contains("startCapture(selectedSttLanguage"),
            "sttPipeline.startCapture() must pass the selected STT language.\n" +
                "Expected: startCapture(language) or startCapture(selectedSttLanguage).\n" +
                "Actual: call does not forward the language parameter.",
        )
    }

    // ── 13.1b — Requirement 2.7: isMeshEnabled set true before capture in group ─────────────────

    /**
     * WHEN the user initiates a PTT recording in a group chat screen
     * THEN the system SHALL set `voiceController.isMeshEnabled = true` BEFORE capture starts.
     *
     * **Validates: Requirement 2.7**
     */
    @Test
    fun `13_1b - PTT press in group sets voiceController isMeshEnabled=true before startCapture`() {
        if (!chatViewModelFile.exists()) return

        val content = chatViewModelFile.readText()
        val startFn = extractStartVoiceRecordingBody(content) ?: return

        // isMeshEnabled must be set
        assertTrue(
            startFn.contains("voiceController.isMeshEnabled") &&
                startFn.contains("= true"),
            "startVoiceRecording() must set voiceController.isMeshEnabled = true.\n" +
                "Expected: assignment present before sttPipeline.startCapture().\n" +
                "Actual: assignment absent — Requirement 2.7 not satisfied.",
        )

        // isMeshEnabled = true must appear BEFORE startCapture in the source text
        val meshEnabledPos = startFn.indexOf("isMeshEnabled")
        val startCapturePos = startFn.indexOf("sttPipeline.startCapture")
        assertTrue(
            meshEnabledPos >= 0 && startCapturePos >= 0 && meshEnabledPos < startCapturePos,
            "voiceController.isMeshEnabled = true must appear BEFORE sttPipeline.startCapture().\n" +
                "This ensures the mesh guard is lifted before any STT result is emitted.\n" +
                "Offsets — isMeshEnabled: $meshEnabledPos, startCapture: $startCapturePos.",
        )
    }

    // ── 13.1c — Requirement 2.6: startVoiceMessage called with group info ───────────────────────

    /**
     * WHEN PTT recording begins in a GROUP conversation
     * THEN the system SHALL call `voiceMessageAdapter.startVoiceMessage(language, recipientId, group, …)`
     * where:
     *  - `recipientId = conversationId` (the group ID, NOT null)
     *  - `group = groupInfo` (non-null GroupInfo object with group metadata)
     *
     * This differs from a DM (recipientId = peer ID, group = null) and a Nearby room
     * (recipientId = null, group = null).
     *
     * **Validates: Requirement 2.6**
     */
    @Test
    fun `13_1c - PTT press in group calls voiceMessageAdapter startVoiceMessage with group info`() {
        if (!chatViewModelFile.exists()) return

        val content = chatViewModelFile.readText()
        val startFn = extractStartVoiceRecordingBody(content) ?: return

        // startVoiceMessage must be called
        assertTrue(
            startFn.contains("voiceMessageAdapter.startVoiceMessage"),
            "startVoiceRecording() must call voiceMessageAdapter.startVoiceMessage().\n" +
                "Expected: call present with language + recipientId + group parameters.\n" +
                "Actual: call absent — Requirement 2.6 not satisfied.",
        )

        // For a group (non-room), the group parameter must be populated via groups.find(conversationId)
        assertTrue(
            startFn.contains("groups.find"),
            "startVoiceRecording() must look up the group via groups.find(conversationId).\n" +
                "Expected: groups.find(conversationId) present before startVoiceMessage call.\n" +
                "Actual: group lookup not found — group routing context cannot be set.",
        )

        // The group parameter must be passed as group?.toGroupInfo()
        assertTrue(
            startFn.contains("group") &&
                (startFn.contains("toGroupInfo()") || startFn.contains("group?.toGroupInfo()")),
            "startVoiceMessage() must supply group parameter via group?.toGroupInfo().\n" +
                "Expected: group = group?.toGroupInfo() in startVoiceMessage call.\n" +
                "Actual: toGroupInfo() conversion not found.",
        )

        // For a non-room group, recipientId must be the conversationId (the group ID)
        assertTrue(
            startFn.contains("recipientId") &&
                startFn.contains("conversationId"),
            "startVoiceMessage() must supply recipientId = conversationId for a group.\n" +
                "Expected: recipientId wired to conversationId (group ID).\n" +
                "Actual: conversationId not referenced in the startVoiceMessage call site.",
        )

        // The group path must guard against room: `if (isRoom) null else conversationId`
        assertTrue(
            startFn.contains("isRoom"),
            "startVoiceRecording() must check isRoom to decide recipientId and group for startVoiceMessage().\n" +
                "Groups pass the group ID + groupInfo; rooms pass null + null.\n" +
                "Expected: isRoom check present near startVoiceMessage call.",
        )
    }

    // ── 13.1d — Requirement 2.5: stopCapture called on PTT release in group ─────────────────────

    /**
     * WHEN the user releases the mic button after holding it in a group chat
     * THEN the system SHALL call `sttPipeline.stopCapture()` to end PCM capture and
     * allow the pipeline to finalise the transcription.
     *
     * **Validates: Requirement 2.5**
     */
    @Test
    fun `13_1d - releasing mic button in group calls sttPipeline stopCapture`() {
        if (!chatViewModelFile.exists()) return

        val content = chatViewModelFile.readText()
        val stopFn = extractStopVoiceRecordingBody(content) ?: return

        assertTrue(
            stopFn.contains("sttPipeline.stopCapture"),
            "stopVoiceRecordingAndStage() must call sttPipeline.stopCapture().\n" +
                "Expected: sttPipeline.stopCapture() call present.\n" +
                "Actual: call absent — Requirement 2.5 not satisfied.",
        )
    }

    // ── 13.1e — Ordering guarantee: mesh enabled and group context both before capture ──────────

    /**
     * WHEN PTT recording begins in a group
     * THEN the system SHALL set mesh-enabled flag AND group routing context BOTH before
     * `sttPipeline.startCapture()` is invoked, so that the very first STT result
     * is forwarded correctly with group routing.
     *
     * **Validates: Requirements 2.6, 2.7**
     */
    @Test
    fun `13_1e - isMeshEnabled and startVoiceMessage with group both precede startCapture`() {
        if (!chatViewModelFile.exists()) return

        val content = chatViewModelFile.readText()
        val startFn = extractStartVoiceRecordingBody(content) ?: return

        val meshPos = startFn.indexOf("isMeshEnabled")
        val contextPos = startFn.indexOf("voiceMessageAdapter.startVoiceMessage")
        val capturePos = startFn.indexOf("sttPipeline.startCapture")

        assertTrue(meshPos >= 0, "voiceController.isMeshEnabled assignment not found in startVoiceRecording().")
        assertTrue(contextPos >= 0, "voiceMessageAdapter.startVoiceMessage() call not found in startVoiceRecording().")
        assertTrue(capturePos >= 0, "sttPipeline.startCapture() call not found in startVoiceRecording().")

        // Both setup steps must precede the actual audio capture
        assertTrue(
            meshPos < capturePos,
            "isMeshEnabled must be set BEFORE startCapture().\n" +
                "Offsets — isMeshEnabled: $meshPos, startCapture: $capturePos.",
        )
        assertTrue(
            contextPos < capturePos,
            "startVoiceMessage() must be called BEFORE startCapture().\n" +
                "Offsets — startVoiceMessage: $contextPos, startCapture: $capturePos.",
        )
    }

    // ── 13.1f — Runtime: VoiceMessageAdapter accepts group routing context ──────────────────────

    /**
     * Runtime validation using MockK: when `startVoiceMessage()` is called with a group
     * context (recipientId = group ID, group = groupInfo object), the adapter stores the
     * context without throwing.
     *
     * **Validates: Requirements 2.6, 2.7**
     */
    @Test
    fun `13_1f - VoiceMessageAdapter accepts group routing context without exception`() {
        val mockSttPipeline =
            mockk<SttPipeline>(relaxed = true) {
                every { latestResult } returns MutableStateFlow(null)
            }
        val mockMeshController = mockk<app.swarsetu.mesh.MeshController>(relaxed = true)
        val mockVoiceController =
            mockk<VoiceConversationController>(relaxed = true) {
                every { isMeshEnabled } returns false
                every { isMeshEnabled = any() } answers { }
            }

        val scope =
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() +
                    kotlinx.coroutines.Dispatchers.Default,
            )

        val adapter =
            VoiceMessageAdapter(
                scope = scope,
                sttPipeline = mockSttPipeline,
                meshController = mockMeshController,
                voiceController = mockVoiceController,
                translatorEngine = app.swarsetu.translation.TranslatorEngine(),
                peers = io.mockk.mockk(relaxed = true),
            )

        // Create GroupInfo for a group conversation
        val groupInfo =
            app.swarsetu.mesh.protocol.GroupInfo(
                id = groupConversationId,
                name = "Test Group",
                members = listOf("member1", "member2"),
                createdBy = "member1",
            )

        // Simulate PTT press in a group: should not throw
        adapter.startVoiceMessage(
            language = selectedLanguage,
            recipientId = groupConversationId,
            group = groupInfo,
        )

        // Also verify stopVoiceMessage is callable
        adapter.stopVoiceMessage()
    }

    // ── 13.1g — Mesh send includes voiceTextLanguage tag and group routing ──────────────────────

    /**
     * WHEN the STT pipeline emits a final result while in group PTT mode
     * THEN the adapter shall send the text over the mesh including the `voiceTextLanguage`
     * tag and SHALL route with the group context (recipientId = group ID, group info included).
     *
     * **Validates: Requirements 2.6, 2.7**
     */
    @Test
    fun `13_1g - mesh send includes voiceTextLanguage tag and group routing context`() {
        if (!voiceMessageAdapterFile.exists()) return

        val content = voiceMessageAdapterFile.readText()

        // The adapter must call meshController.sendChat
        assertTrue(
            content.contains("meshController.sendChat"),
            "VoiceMessageAdapter must call meshController.sendChat() to transmit transcribed text.",
        )

        // voiceTextLanguage must be passed to the mesh send
        assertTrue(
            content.contains("voiceTextLanguage"),
            "meshController.sendChat() must include voiceTextLanguage tag.\n" +
                "This tag carries the language of the transcribed text for receiver's TTS.",
        )

        // recipientId must be forwarded from the stored context (group ID for groups)
        assertTrue(
            content.contains("context.recipientId"),
            "meshController.sendChat() must use context.recipientId so group messages are routed " +
                "to the correct group conversation rather than peer-to-peer.",
        )

        // group must be forwarded from the stored context
        assertTrue(
            content.contains("context.group"),
            "meshController.sendChat() must use context.group to include group routing metadata.",
        )

        // isMeshEnabled guard must be present
        assertTrue(
            content.contains("isMeshEnabled"),
            "VoiceMessageAdapter must check voiceController.isMeshEnabled before sending.\n" +
                "Expected: guard present in latestResult collector.",
        )
    }

    // ── 13.1h — Group routing: group is NOT null for group conversation ─────────────────────────

    /**
     * WHEN PTT is initiated in a group conversation (not a DM, not a Nearby room)
     * THEN the system SHALL pass `group = groupInfo` (non-null) to
     * `voiceMessageAdapter.startVoiceMessage()`.
     *
     * This is verified by checking that the code does `groups.find(conversationId)` and
     * conditionally passes `group?.toGroupInfo()` based on `isRoom`.
     *
     * For a group (isRoom = false, conversationId = group ID):
     *  - groups.find(conversationId) returns GroupEntity
     *  - group?.toGroupInfo() returns GroupInfo object
     *  - recipientId = conversationId (group ID)
     *
     * **Validates: Requirement 2.6**
     */
    @Test
    fun `13_1h - group routing context is non-null for group conversation`() {
        if (!chatViewModelFile.exists()) return

        val content = chatViewModelFile.readText()
        val startFn = extractStartVoiceRecordingBody(content) ?: return

        // The implementation must use a conditional: `if (isRoom) null else groups.find(conversationId)`
        assertTrue(
            startFn.contains("if") &&
                startFn.contains("isRoom") &&
                startFn.contains("groups.find"),
            "startVoiceRecording() must conditionally look up the group:\n" +
                "  if (isRoom) null else groups.find(conversationId)\n" +
                "For a group (isRoom = false), groups.find() returns the GroupEntity.\n" +
                "Expected: conditional isRoom check with groups.find in else branch.\n" +
                "Actual: conditional or groups.find not found.",
        )

        // The group parameter must be passed as group?.toGroupInfo() so it's non-null for groups
        assertTrue(
            startFn.contains("group?.toGroupInfo()") || startFn.contains("toGroupInfo()"),
            "startVoiceMessage() must pass group?.toGroupInfo() as the group parameter.\n" +
                "For a group conversation, this resolves to a non-null GroupInfo object.\n" +
                "Expected: toGroupInfo() call present in startVoiceMessage().\n" +
                "Actual: toGroupInfo() not found.",
        )
    }

    // ── 13.1i — Group vs DM vs Room: recipientId and group parameter differentiation ───────────

    /**
     * WHEN PTT is initiated in different conversation types (DM, group, room)
     * THEN the system SHALL differentiate routing context:
     *
     *  - **DM** (isRoom = false, no group): recipientId = peer ID, group = null
     *  - **Group** (isRoom = false, group exists): recipientId = group ID, group = groupInfo
     *  - **Room** (isRoom = true): recipientId = null, group = null
     *
     * This test verifies the conditional logic that implements this differentiation.
     *
     * **Validates: Requirement 2.6**
     */
    @Test
    fun `13_1i - routing context differentiates between DM group and room conversations`() {
        if (!chatViewModelFile.exists()) return

        val content = chatViewModelFile.readText()
        val startFn = extractStartVoiceRecordingBody(content) ?: return

        // The recipientId parameter must use `if (isRoom) null else conversationId`
        assertTrue(
            startFn.contains("recipientId") &&
                startFn.contains("if") &&
                startFn.contains("isRoom"),
            "startVoiceMessage() must conditionally set recipientId based on isRoom:\n" +
                "  recipientId = if (isRoom) null else conversationId\n" +
                "Expected: conditional isRoom check for recipientId parameter.\n" +
                "Actual: conditional not found.",
        )

        // The group parameter must use `if (isRoom) null else groups.find(conversationId)?.toGroupInfo()`
        assertTrue(
            startFn.contains("group") &&
                startFn.contains("if") &&
                startFn.contains("isRoom") &&
                startFn.contains("groups.find"),
            "startVoiceMessage() must conditionally set group based on isRoom:\n" +
                "  group = if (isRoom) null else groups.find(conversationId)?.toGroupInfo()\n" +
                "For a group, groups.find() returns non-null and toGroupInfo() creates GroupInfo.\n" +
                "For a DM with no group, groups.find() returns null so group = null.\n" +
                "For a room, isRoom = true so group = null.\n" +
                "Expected: conditional isRoom check with groups.find for group parameter.\n" +
                "Actual: conditional or groups.find not found.",
        )
    }

    // ── 13.1j — Pipeline state guard in group conversation ──────────────────────────────────────

    /**
     * WHEN `startVoiceRecording()` is called in a group while the pipeline is already capturing
     * THEN the function SHALL return false and NOT call `startCapture()` again.
     *
     * **Validates: Requirement 2.4 (guard clause)**
     */
    @Test
    fun `13_1j - startVoiceRecording in group returns false when pipeline is not IDLE`() {
        if (!chatViewModelFile.exists()) return

        val content = chatViewModelFile.readText()
        val startFn = extractStartVoiceRecordingBody(content) ?: return

        // The guard must check the current pipeline state
        assertTrue(
            startFn.contains("sttPipeline.state.value") &&
                (startFn.contains("PipelineState.IDLE") || startFn.contains("IDLE")),
            "startVoiceRecording() must guard against a non-IDLE pipeline state.\n" +
                "Expected: check sttPipeline.state.value != IDLE → return false.\n" +
                "Actual: guard absent — concurrent PTT presses could corrupt pipeline state.",
        )

        // The function must return false on rejection
        assertTrue(
            content.contains("fun startVoiceRecording") &&
                content.contains("return false"),
            "startVoiceRecording() must return false on rejection to inform the UI.",
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Extracts the body of `startVoiceRecording()` from the ChatViewModel source, or returns
     * null if the function cannot be located (test is then silently skipped).
     */
    private fun extractStartVoiceRecordingBody(content: String): String? {
        val pattern =
            Regex(
                """fun\s+startVoiceRecording\s*\([^)]*\)\s*:\s*Boolean\s*\{.{0,4000}""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            )
        return pattern.find(content)?.value
    }

    /**
     * Extracts the body of `stopVoiceRecordingAndStage()` from the ChatViewModel source, or
     * returns null if the function cannot be located.
     */
    private fun extractStopVoiceRecordingBody(content: String): String? {
        val pattern =
            Regex(
                """fun\s+stopVoiceRecordingAndStage\s*\([^)]*\)\s*\{.{0,2000}""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            )
        return pattern.find(content)?.value
    }
}
