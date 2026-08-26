package app.swarsetu.di

import android.util.Log
import app.swarsetu.voice.DefaultVoiceConversationController
import app.swarsetu.voice.VoiceConversationController
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val voiceModule = module {
    // Provide a dedicated CoroutineScope for the VoiceConversationController
    single {
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, t ->
                Log.w("VoiceScope", "Uncaught exception in voice coroutine: ${t.javaClass.simpleName}: ${t.message}", t)
            }
        )
    }

    single<VoiceConversationController> {
        DefaultVoiceConversationController(
            scope = get(),
            sttPipeline = get(),
            ttsManager = get()
        )
    }

    single {
        app.swarsetu.voice.VoiceMessageReceiver(
            ttsManager = get(),
            scope = get(),
            voiceController = get()
        )
    }

    single {
        app.swarsetu.voice.VoiceMessageAdapter(
            scope = get(),
            sttPipeline = get(),
            meshController = get(),
            voiceController = get()
        )
    }
}
