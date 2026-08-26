package app.swarsetu.di

import android.util.Log
import app.swarsetu.voice.DefaultVoiceConversationController
import app.swarsetu.voice.VoiceConversationController
import app.swarsetu.stt.SttTraceLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val voiceModule = module {
    // Provide a dedicated CoroutineScope for the VoiceConversationController
    single {
        SttTraceLogger.log("BOOT-VOICE-001", "create Voice scope")
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t ->
                Log.w("VoiceScope", "Uncaught exception in voice coroutine: ${t.javaClass.simpleName}: ${t.message}", t)
            }
        ).also { SttTraceLogger.log("BOOT-VOICE-001E", "created Voice scope") }
    }

    single<VoiceConversationController> {
        SttTraceLogger.log("BOOT-VOICE-002", "create VoiceConversationController")
        DefaultVoiceConversationController(
            scope = get(),
            sttPipeline = get(),
            ttsManager = get()
        ).also { SttTraceLogger.log("BOOT-VOICE-002E", "created VoiceConversationController") }
    }

    factory {
        SttTraceLogger.log("BOOT-VOICE-003", "create VoiceMessageReceiver")
        app.swarsetu.voice.VoiceMessageReceiver(
            ttsManager = get(),
            scope = get(),
            voiceController = get()
        ).also { SttTraceLogger.log("BOOT-VOICE-003E", "created VoiceMessageReceiver") }
    }

    factory {
        SttTraceLogger.log("BOOT-VOICE-004", "create VoiceMessageAdapter")
        app.swarsetu.voice.VoiceMessageAdapter(
            scope = get(),
            sttPipeline = get(),
            meshController = get(),
            voiceController = get()
        ).also { SttTraceLogger.log("BOOT-VOICE-004E", "created VoiceMessageAdapter") }
    }
}
