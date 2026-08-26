package app.swarsetu.voice

import app.swarsetu.mesh.MeshController
import app.swarsetu.mesh.crypto.MessageContent
import app.swarsetu.mesh.protocol.GroupInfo
import app.swarsetu.mesh.protocol.ReplyRef
import app.swarsetu.stt.SttLanguage
import app.swarsetu.stt.SttPipeline
import app.swarsetu.stt.SttResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges the local STT pipeline to the Mesh network.
 * 
 * When a voice message is initiated, this adapter configures the routing context.
 * As soon as the STT pipeline produces a final, usable result, the adapter composes and 
 * originates a new chat message over the mesh.
 */
class VoiceMessageAdapter(
    private val scope: CoroutineScope,
    private val sttPipeline: SttPipeline,
    private val meshController: MeshController,
    private val voiceController: VoiceConversationController,
) {
    private data class RoutingContext(
        val recipientId: String?,
        val group: GroupInfo?,
        val replyTo: ReplyRef?,
        val isAlert: Boolean,
    )

    private val currentContext = AtomicReference<RoutingContext?>(null)
    private var lastSentResult: SttResult? = null

    init {
        scope.launch {
            sttPipeline.latestResult.collect { result ->
                // Check if MESH mode is enabled at the controller level
                if (result != null && voiceController.isMeshEnabled) {
                    handleSttResult(result)
                }
            }
        }
    }

    /**
     * Starts listening for a voice message that will be sent over the mesh.
     */
    fun startVoiceMessage(
        language: SttLanguage,
        recipientId: String? = null,
        group: GroupInfo? = null,
        replyTo: ReplyRef? = null,
        isAlert: Boolean = false,
    ) {
        currentContext.set(RoutingContext(recipientId, group, replyTo, isAlert))
        if (sttPipeline.canCapture) {
            sttPipeline.startCapture(language)
        }
    }

    /**
     * Stops listening. If a final result was already captured, it will still be processed.
     */
    fun stopVoiceMessage() {
        sttPipeline.stopCapture()
    }

    private suspend fun handleSttResult(result: SttResult) {
        if (!result.isUsable) return
        if (result === lastSentResult) return
        lastSentResult = result

        try {
            val t1 = System.currentTimeMillis()
            val t0 = t1 - result.durationMs
            voiceController.reportSttLatency(t0, t1)

            val context = currentContext.get() ?: return
            val ttsLanguage = result.language.toTtsLanguage() ?: return
            
            val t2 = System.currentTimeMillis()

            // Calculate payload size by simulating exactly what MeshController builds
            val content = MessageContent(
                body = result.text,
                replyTo = context.replyTo,
                voiceTextLanguage = ttsLanguage.name,
                isAlert = if (context.isAlert) true else null
            )
            val payloadSizeBytes = content.encode().size
            
            val messageId = UUID.randomUUID().toString()
            val t3 = System.currentTimeMillis()
            
            voiceController.reportOutboundMessageMetrics(messageId, payloadSizeBytes, t2, t3)

            meshController.sendChat(
                text = result.text,
                recipientId = context.recipientId,
                group = context.group,
                replyTo = context.replyTo,
                voiceTextLanguage = ttsLanguage.name,
                isAlert = context.isAlert,
                messageId = messageId
            )
            
            // Clear context after successful transmission to prevent accidental re-sends.
            currentContext.set(null)
        } catch (e: Throwable) {
            android.util.Log.e("VoiceMessageAdapter", "Failed to send STT result: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}
