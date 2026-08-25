package app.swarsetu.di

import android.content.Context
import app.swarsetu.stt.SttPipeline
import app.swarsetu.tts.TtsManager
import app.swarsetu.tts.ui.TtsTestViewModel
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get

class KoinGraphTest : KoinTest {

    @Before
    fun setUp() {
        val mockAudioManager = mockk<android.media.AudioManager>(relaxed = true)
        val mockContext = mockk<Context>(relaxed = true) {
            io.mockk.every { getSystemService(Context.AUDIO_SERVICE) } returns mockAudioManager
        }
        startKoin {
            androidContext(mockContext)
            modules(
                appModule,
                meshModule,
                moderationModule,
                sttModule,
                ttsModule,
                uiModule,
                voiceModule
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `verify Koin DI graph can resolve STT and TTS dependencies`() {
        // Verify SttPipeline resolves (which caused the NoDefinitionFoundException)
        val pipeline = get<SttPipeline>()
        checkNotNull(pipeline)

        // Verify TtsManager resolves
        val ttsManager = get<TtsManager>()
        checkNotNull(ttsManager)

        // Verify TtsTestViewModel resolves
        val ttsViewModel = get<TtsTestViewModel>()
        checkNotNull(ttsViewModel)
    }
}
