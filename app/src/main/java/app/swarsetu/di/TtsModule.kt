package app.swarsetu.di

import app.swarsetu.tts.TtsEngine
import app.swarsetu.tts.TtsManager
import app.swarsetu.tts.backend.AndroidTtsEngine
import app.swarsetu.tts.metrics.TtsMetricsCollector
import app.swarsetu.tts.ui.TtsTestViewModel
import app.swarsetu.voice.VoiceMessageAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val ttsModule = module {
    // Singleton scope for the TTS coroutines
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Metrics collector is a singleton to persist metrics across screens
    single { TtsMetricsCollector() }

    // Provide the Android TTS Engine implementation
    single<TtsEngine> { 
        AndroidTtsEngine(
            context = androidContext(),
            metricsCollector = get()
        )
    }

    // TTS Manager is the central facade, exposed as a Singleton
    single {
        TtsManager(
            context = androidContext(),
            engine = get(),
            scope = get()
        )
    }

    // ViewModel for the test screen
    viewModel {
        TtsTestViewModel(
            ttsManager = get(),
            metricsCollector = get(),
            voiceController = get(),
            voiceMessageAdapter = get()
        )
    }
}
