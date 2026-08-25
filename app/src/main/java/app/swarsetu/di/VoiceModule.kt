package app.swarsetu.di

import app.swarsetu.voice.DefaultVoiceConversationController
import app.swarsetu.voice.VoiceConversationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val voiceModule = module {
    // Provide a dedicated CoroutineScope for the VoiceConversationController
    single {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
