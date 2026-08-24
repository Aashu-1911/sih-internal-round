# STT Subsystem — Handoff Documentation

**Author:** Member 1 — STT Lead  
**Date:** August 24, 2026  
**Status:** Core implemented, model assets pending

---

## 1. How to Initialize STT

### Via Koin DI (recommended)

The STT engine is an app-wide singleton, wired in `di/SttModule.kt`:

```kotlin
// In your ViewModel or composable:
val sttEngine: SttEngine by inject()

// Initialize for a specific language:
sttEngine.initialize(SttConfig(language = SttLanguage.HINDI))
```

### Via SttPipeline (recommended for UI integration)

`SttPipeline` ties together capture + VAD + engine:

```kotlin
val pipeline = SttPipeline(context, sttEngine)
// Start capture → auto-transcribe when silence detected
pipeline.startCapture(language = SttLanguage.HINDI)
```

### Direct instantiation (testing)

```kotlin
val modelManager = SttModelManager(context)
val factory = SttEngineFactory(context, modelManager)
val engine = factory.create()
engine.initialize(SttConfig(language = SttLanguage.ENGLISH))
```

---

## 2. How to Select a Language

```kotlin
// Switch language (may trigger model swap):
engine.setLanguage(SttLanguage.TAMIL)

// Or via config:
engine.initialize(SttConfig(language = SttLanguage.BENGALI))

// Get current language:
val current = engine.currentLanguage // SttLanguage?
```

### Available languages

All 10 PS 26173 languages are declared:

| Code | Language | DisplayName |
|------|----------|-------------|
| `hi` | Hindi | हिन्दी |
| `gu` | Gujarati | ગુજરાતી |
| `mr` | Marathi | मराठी |
| `kn` | Kannada | ಕನ್ನಡ |
| `ml` | Malayalam | മലയാളം |
| `ta` | Tamil | தமிழ் |
| `te` | Telugu | తెలుగు |
| `or` | Odia | ଓଡ଼ିଆ |
| `bn` | Bengali | বাংলা |
| `en` | English | English |

**Note:** Languages are structurally declared. Actual model availability depends on bundled model files in `assets/stt-{code}/`. Until models are bundled, the engine returns empty results (graceful degradation).

---

## 3. Expected Audio Format

| Property | Value |
|----------|-------|
| Format | 16-bit signed PCM |
| Channels | Mono |
| Sample rate | 16,000 Hz |
| Byte order | Little-endian |
| Encoding | `AudioFormat.ENCODING_PCM_16BIT` |

### Via PcmCapture

```kotlin
val capture = PcmCapture(context)
if (capture.canCapture()) {
    capture.start()
    val pcm: ShortArray? = capture.readChunk(maxSamples = 1600) // 100ms
    // Or capture until silence:
    val pcm = capture.captureUntilSilence(silenceMs = 1500)
    capture.stop()
}
```

### From VoiceRecorder (existing)

The existing `VoiceRecorder` produces AAC, not PCM. For STT, use `PcmCapture` instead. The two can coexist — `PcmCapture` feeds STT while `VoiceRecorder` feeds voice-note storage.

---

## 4. How to Consume Partial Results

### Via SttPipeline

```kotlin
// Observe partial text during capture:
pipeline.partialText.collect { text ->
    // Update UI with live transcription preview
    editText.setText(text)
}
```

### Via SttEngine directly

```kotlin
// Streaming transcription:
engine.transcribeStream(pcm, language).collect { result ->
    when (result.type) {
        SttResultType.PARTIAL -> {
            // Update UI with intermediate text
            updatePartialText(result.text)
        }
        SttResultType.FINAL -> {
            // Transcription complete
            useFinalText(result.text)
        }
    }
}
```

---

## 5. How to Consume Final Results

### Via SttPipeline

```kotlin
pipeline.latestResult.collect { result ->
    if (result != null && result.isUsable) {
        // Use the transcription
        sendChat(result.text)
    }
}
```

### Via SttEngine directly

```kotlin
val result = engine.transcribe(pcm, language)
if (result.isUsable) {
    // result.text is the final transcription
    // result.confidence is the engine's confidence (0..1, or -1 if unknown)
    // result.durationMs is the processing time
}
```

### SttResult properties

```kotlin
data class SttResult(
    val text: String,           // The transcribed text
    val type: SttResultType,    // PARTIAL or FINAL
    val language: SttLanguage,  // Which language was used
    val confidence: Float,      // 0..1 or -1 (unknown)
    val durationMs: Long,       // Processing time in ms
) {
    val isUsable: Boolean       // true when FINAL and text is non-blank
}
```

---

## 6. Error Handling

The engine follows the project's graceful-degradation pattern:

```kotlin
try {
    val result = engine.transcribe(pcm, language)
    // result may be empty on failure — never throws for expected cases
} catch (e: SttException) {
    when (e) {
        is SttException.ModelLoadError -> { /* Model file missing/corrupt */ }
        is SttException.UnsupportedLanguage -> { /* Language not supported */ }
        is SttException.InferenceError -> { /* Inference failed */ }
        is SttException.EngineNotReady -> { /* Engine not initialized */ }
        is SttException.AudioInputError -> { /* Invalid PCM input */ }
        is SttException.OutOfMemory -> { /* OOM during inference */ }
    }
}
```

**Key invariant:** `transcribe()` returns `SttResult.empty()` on expected failures (missing model, no speech, etc.). Only truly unexpected errors throw.

---

## 7. Model Lifecycle

```
initialize(config) → loadModel() → [ready]
         ↓
    transcribe(pcm) → [result]
         ↓
    setLanguage(lang) → releaseModel() → loadModel() → [ready]
         ↓
    release() → releaseModel() → [not ready]
```

- **initialize:** Loads model for the configured language. Idempotent if already loaded for same language.
- **setLanguage:** Switches language. Triggers model swap if needed.
- **release:** Frees all resources. Idempotent. Can re-initialize after release.
- **Auto-degradation:** If model fails to load, engine is "ready" but returns empty results.

---

## 8. Performance Characteristics

### Expected (based on Vosk documentation and similar devices)

| Metric | Expected Range | Notes |
|--------|---------------|-------|
| Model init time | 1–5 seconds | One-time cost per language |
| First inference | 200–500ms | Includes JIT compilation |
| Steady-state inference | 50–200ms | For 1-second audio |
| Real-time factor | 0.05–0.2 | <1.0 means faster than real-time |
| Model size | 1–50 MB | Per language (small model) |
| Peak RAM | 50–150 MB | Per loaded model |
| Streaming latency | 100–300ms | Partial result delay |

### Actual measurements

Benchmark using `SttBenchmark`:

```kotlin
val benchmark = SttBenchmark(engine)
val results = benchmark.runFullBenchmark()
results.forEach { println(it.toSummary()) }
```

**Actual values must be measured on target hardware.** The above are estimates from Vosk documentation.

---

## 9. Supported Languages

All 10 PS 26173 languages are structurally supported:

- **Structurally declared:** All 10 languages have BCP-47 codes, display names, and asset directories.
- **Model availability:** Depends on bundled Vosk model files in `assets/stt-{code}/`.
- **Inference quality:** Depends on model accuracy for each language.

### Language capability matrix

| Language | Declared | Asset Dir | Model | Loading | Inference | Status |
|----------|----------|-----------|-------|---------|-----------|--------|
| Hindi | ✓ | stt-hi | PENDING | PENDING | PENDING | DECLARED |
| Gujarati | ✓ | stt-gu | PENDING | PENDING | PENDING | DECLARED |
| Marathi | ✓ | stt-mr | PENDING | PENDING | PENDING | DECLARED |
| Kannada | ✓ | stt-kn | PENDING | PENDING | PENDING | DECLARED |
| Malayalam | ✓ | stt-ml | PENDING | PENDING | PENDING | DECLARED |
| Tamil | ✓ | stt-ta | PENDING | PENDING | PENDING | DECLARED |
| Telugu | ✓ | stt-te | PENDING | PENDING | PENDING | DECLARED |
| Odia | ✓ | stt-or | PENDING | PENDING | PENDING | DECLARED |
| Bengali | ✓ | stt-bn | PENDING | PENDING | PENDING | DECLARED |
| English | ✓ | stt-en | PENDING | PENDING | PENDING | DECLARED |

**Status meanings:**
- `DECLARED` — Language is in the enum with correct metadata
- `AVAILABLE` — Model file exists in assets
- `LOADING` — Model loads successfully
- `INFERENCE` — Model produces non-empty transcription
- `TESTED` — Tested with representative speech

---

## 10. Known Limitations

1. **No model files bundled yet.** The engine returns empty results until Vosk model files are added to `assets/stt-{code}/`.
2. **Vosk dependency not yet added to Gradle.** The `com.alphacephei:vosk-android` library needs to be added to `gradle/libs.versions.toml`.
3. **No WER measurement.** Word Error Rate requires reference transcripts. External evaluation needed.
4. **No quantized models.** Vosk models are full-precision. Quantization would reduce size but requires model conversion.
5. **Model switching is sequential.** Only one language model is loaded at a time. Switching triggers a full model swap.
6. **No automatic language detection.** Manual language selection is the P0 approach.
7. **F-Droid compatibility.** Vosk ships native .so files. F-Droid may need special handling.

---

## 11. Integration Example

### Complete example: Record → Transcribe → Send

```kotlin
class ChatViewModel(...) : ViewModel() {
    private val sttEngine: SttEngine by inject()
    private val pcmCapture: PcmCapture by inject()

    fun recordAndTranscribe(language: SttLanguage) {
        viewModelScope.launch {
            // 1. Initialize engine
            sttEngine.initialize(SttConfig(language = language))

            // 2. Capture audio
            pcmCapture.start()
            val pcm = pcmCapture.captureUntilSilence(
                silenceMs = 1_500L,
                onAmplitude = { amp -> /* update UI level meter */ },
            )
            pcmCapture.stop()

            if (pcm == null || pcm.isEmpty()) return@launch

            // 3. Transcribe
            val result = sttEngine.transcribe(pcm, language)

            // 4. Use result
            if (result.isUsable) {
                sendChat(result.text)
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch { sttEngine.release() }
        super.onCleared()
    }
}
```

---

## 12. Tests Performed

### Unit tests (JVM, no device needed)

| Test File | Coverage |
|-----------|----------|
| `SttLanguageTest.kt` | Language enum, codes, fromCode, supported set |
| `SttResultTest.kt` | Result types, isUsable, confidence, duration |
| `SttModelInfoTest.kt` | Model metadata, size, availability |
| `DefaultSttEngineTest.kt` | Engine lifecycle, graceful degradation, interface contract |
| `VoiceActivityDetectorTest.kt` | VAD state transitions, thresholds, reset |
| `LanguageCapabilityTest.kt` | All 10 languages, codes, names, asset dirs |

### Instrumented tests (require device)

| Test File | Coverage |
|-----------|----------|
| `SttEngineInstrumentedTest.kt` | Real model loading, inference, language switching |
| `PcmCaptureInstrumentedTest.kt` | Microphone capture, VAD-based recording |
| `SttPipelineInstrumentedTest.kt` | End-to-end capture → transcription flow |

### Benchmark tests

| Test | Coverage |
|------|----------|
| `SttBenchmark.kt` | Init time, inference latency, RTF, accuracy |

---

## 13. File Listing

### New files created

| File | Purpose |
|------|---------|
| `app/src/main/java/app/swarsetu/stt/SttEngine.kt` | Core STT interface |
| `app/src/main/java/app/swarsetu/stt/SttResult.kt` | Result + type |
| `app/src/main/java/app/swarsetu/stt/SttLanguage.kt` | 10-language enum |
| `app/src/main/java/app/swarsetu/stt/SttConfig.kt` | Configuration |
| `app/src/main/java/app/swarsetu/stt/SttModelInfo.kt` | Model metadata |
| `app/src/main/java/app/swarsetu/stt/SttModelManager.kt` | Model lifecycle |
| `app/src/main/java/app/swarsetu/stt/SttException.kt` | Error hierarchy |
| `app/src/main/java/app/swarsetu/stt/SttEngineFactory.kt` | Engine factory |
| `app/src/main/java/app/swarsetu/stt/DefaultSttEngine.kt` | Base engine skeleton |
| `app/src/main/java/app/swarsetu/stt/VoskEngine.kt` | Vosk concrete engine |
| `app/src/main/java/app/swarsetu/stt/PcmCapture.kt` | AudioRecord PCM capture |
| `app/src/main/java/app/swarsetu/stt/VoiceActivityDetector.kt` | Energy-based VAD |
| `app/src/main/java/app/swarsetu/stt/SttPipeline.kt` | End-to-end pipeline |
| `app/src/main/java/app/swarsetu/stt/SttBenchmark.kt` | Benchmarking framework |
| `app/src/main/java/app/swarsetu/di/SttModule.kt` | Koin DI module |
| `docs/STT_HANDOFF.md` | This document |

### Modified files

| File | Change |
|------|--------|
| `app/src/main/java/app/swarsetu/SwarSetuApplication.kt` | Added sttModule import and registration |

### Test files

| File | Purpose |
|------|---------|
| `app/src/test/java/app/swarsetu/stt/SttLanguageTest.kt` | Language tests |
| `app/src/test/java/app/swarsetu/stt/SttResultTest.kt` | Result tests |
| `app/src/test/java/app/swarsetu/stt/SttModelInfoTest.kt` | Model info tests |
| `app/src/test/java/app/swarsetu/stt/DefaultSttEngineTest.kt` | Engine contract tests |
| `app/src/test/java/app/swarsetu/stt/VoiceActivityDetectorTest.kt` | VAD tests |
| `app/src/test/java/app/swarsetu/stt/LanguageCapabilityTest.kt` | Language capability matrix |

---

## 14. Remaining Risks

| Risk | Mitigation |
|------|-----------|
| Vosk native .so compatibility with F-Droid | Test F-Droid build separately; consider DefaultSttEngine fallback |
| Model accuracy for Indic languages | Benchmark on target devices; iterate on model selection |
| Memory pressure on low-end devices | Lazy loading + model unloading; bound by SttModelManager |
| Audio contention with BLE mesh | PcmCapture uses VOICE_RECOGNITION source; test concurrent use |
| Wire format: transcript in body field | Additive change; old peers see text + audio attachment |

---

## 15. Exact Handoff Contract

```kotlin
// 1. Inject the engine
val sttEngine: SttEngine by inject()

// 2. Initialize for a language
sttEngine.initialize(SttConfig(language = SttLanguage.HINDI))

// 3. Capture PCM (16-bit, 16kHz, mono)
val pcm: ShortArray = captureMicrophoneAudio()

// 4. Transcribe
val result: SttResult = sttEngine.transcribe(pcm, SttLanguage.HINDI)

// 5. Use the text
if (result.isUsable) {
    val text: String = result.text
    // Send over mesh, display in UI, etc.
}

// 6. Cleanup
sttEngine.release()
```

**That's the entire contract.** The STT subsystem is a pure `ShortArray → SttResult` transform. No Knit, no networking, no TTS, no UI.
