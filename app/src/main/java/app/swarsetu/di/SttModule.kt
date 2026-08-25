package app.swarsetu.di

import app.swarsetu.stt.PcmCapture
import app.swarsetu.stt.SttEngine
import app.swarsetu.stt.SttEngineFactory
import app.swarsetu.stt.SttModelManager
import app.swarsetu.stt.VoskEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for the STT (Speech-to-Text) subsystem. Bound alongside [appModule], [meshModule],
 * [moderationModule], and [uiModule] in [app.swarsetu.SwarSetuApplication].
 *
 * All STT objects are app-scoped singletons:
 * - [SttModelManager] — manages model availability and lifecycle
 * - [SttEngineFactory] — creates engine instances
 * - [SttEngine] — the shared, app-wide engine singleton (Vosk-backed)
 * - [PcmCapture] — the shared, app-wide PCM capture instance
 *
 * The engine is a `single` (one instance, app-wide) because model loading is expensive
 * (~50–100 MB) and the microphone is an exclusive system resource — only one transcription
 * should run at a time. This mirrors [app.swarsetu.ui.voice.VoicePlayer]'s pattern.
 *
 * **Fallback:** If Vosk's native dependencies are unavailable (e.g. F-Droid build without
 * the Vosk .so), swap the `single<SttEngine>` binding to use [DefaultSttEngine] instead —
 * it gracefully degrades to returning empty results.
 */
val sttModule =
    module {
        single { SttModelManager(androidContext()) }
        single { SttEngineFactory(androidContext(), get()) }
        single<SttEngine> { get<SttEngineFactory>().create() }
        single { PcmCapture(androidContext()) }
        single { app.swarsetu.stt.SttPipeline(androidContext(), get()) }
    }
