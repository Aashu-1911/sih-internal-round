# PS 26173 — Forensic End-to-End Implementation Audit

**Date:** August 26, 2026
**Codebase:** stt-tts-ui-integration branch, commit 162ec51+
**Method:** Static execution-path tracing. Every claim below is backed by a specific file + line.

---

## PART 1 — REQUIREMENT → IMPLEMENTATION MATRIX

### R1: Offline STT for 10 Indian Languages

| Aspect | File | Line | Status |
|--------|------|------|--------|
| Language enum (10 langs) | `stt/SttLanguage.kt` | 17-26 | ✅ All 10 declared |
| STT engine interface | `stt/SttEngine.kt` | 1-90 | ✅ Clean interface |
| Sherpa-ONNX engine | `stt/SherpaOnnxEngine.kt` | 26-171 | ✅ Loads model, runs inference |
| Vosk engine | `stt/VoskEngine.kt` | 1-250 | ✅ Alternative engine |
| Engine factory | `stt/SttEngineFactory.kt` | 1-48 | ✅ Sherpa→Vosk→Default fallback |
| Koin DI wiring | `di/SttModule.kt` | 1-20 | ✅ `single<SttEngine>` created |
| STT pipeline | `stt/SttPipeline.kt` | 1-200 | ✅ Capture+VAD+transcribe |
| PCM capture | `stt/PcmCapture.kt` | 1-200 | ✅ 16kHz mono PCM |
| VAD | `stt/VoiceActivityDetector.kt` | 1-120 | ✅ Energy-based |
| Model assets (9 langs) | `assets/stt-{hi,gu,mr,kn,ml,ta,te,bn,en}/` | — | ✅ model.int8.onnx + tokens.txt |
| Model assets (Odia) | `assets/stt-or/` | — | 🔴 **MISSING** |
| Native .so (Sherpa) | `jniLibs/arm64-v8a/libsherpa-onnx-jni.so` | — | ✅ Present |
| Native .so (ONNX RT) | `jniLibs/arm64-v8a/libonnxruntime.so` | — | ✅ Present |
| Gradle dep (Sherpa) | `build.gradle.kts` | — | 🔴 **NOT DECLARED** (source bundled) |
| Gradle dep (Vosk) | `build.gradle.kts:533` | — | ✅ `com.alphacephei:vosk-android:0.3.75` |

**Verdict:** 🟡 PARTIALLY FUNCTIONAL — 9/10 languages have STT models. Odia is broken. Sherpa-ONNX has no Gradle dependency (source manually bundled).

### R2: Offline TTS for 10 Indian Languages

| Aspect | File | Line | Status |
|--------|------|------|--------|
| TTS language enum | `tts/TtsLanguage.kt` | 8-20 | ✅ All 10 declared |
| TTS engine interface | `tts/TtsEngine.kt` | 1-35 | ✅ Clean interface |
| Android TTS backend | `tts/backend/AndroidTtsEngine.kt` | 1-150 | ⚠️ Uses `android.speech.tts.TextToSpeech` |
| TTS manager | `tts/TtsManager.kt` | 1-60 | ✅ Orchestrator |
| TTS scheduler | `tts/scheduler/TtsScheduler.kt` | 1-75 | ✅ Normal + Alert queue |
| Koin DI wiring | `di/TtsModule.kt` | 1-48 | ✅ `single<TtsEngine>` created |
| Language data bundled? | `assets/` | — | 🔴 **NO** — Android TTS downloads at runtime |
| Offline TTS engine? | — | — | 🔴 **NO** — No bundled offline TTS |
| Sherpa-ONNX TTS? | — | — | 🔴 **NOT IMPLEMENTED** |

**Verdict:** 🔴 BROKEN — TTS depends on Android's built-in TTS which requires downloading Indic language data. Not fully offline.

### R3: VAD / Pause Detection / Sentence Formation

| Aspect | File | Line | Status |
|--------|------|------|--------|
| VAD implementation | `stt/VoiceActivityDetector.kt` | 1-120 | ✅ Energy-based with hysteresis |
| VAD integrated in pipeline | `stt/SttPipeline.kt:130-170` | — | ✅ Drives capture loop |
| Pause detection | `stt/SttPipeline.kt:155-165` | — | ✅ Silence timeout after speech |
| Sentence formation | `stt/SttPipeline.kt:168-190` | — | ✅ Accumulate→finalize on silence |
| Partial results | `stt/SttPipeline.kt:140-150` | — | ✅ Emitted every ~1s |
| Configurable thresholds | `stt/VoiceActivityDetector.kt:90-100` | — | 🟡 Hardcoded defaults (200/400/20/3) |

**Verdict:** ✅ FULLY FUNCTIONAL (thresholds are hardcoded but reasonable defaults)

### R4: Push-to-Talk Walkie-Talkie Mode

| Aspect | File | Line | Status |
|--------|------|------|--------|
| PTT backend (VoiceMessageAdapter) | `voice/VoiceMessageAdapter.kt` | 1-113 | ✅ STT→mesh pipeline |
| VoiceConversationController | `voice/VoiceConversationController.kt` | 1-202 | ✅ STT→TTS loop |
| ChatViewModel wired | `ui/chat/ChatViewModel.kt:207-208` | — | ✅ `voiceMessageAdapter` + `voiceController` injected |
| ChatScreen PTT button? | `ui/chat/ChatScreen.kt` | — | 🔴 **NO dedicated PTT button** |
| Live Translate toggle | `ui/chat/ChatScreen.kt:679` | — | 🟡 Toggle exists, labeled "STT" |
| `startVoiceMessage()` called from ChatScreen? | `ui/chat/ChatScreen.kt` | — | 🔴 **NEVER CALLED** from production UI |
| PTT only in test screen? | `tts/ui/TtsTestViewModel.kt:116-127` | — | 🔴 **TEST-ONLY** |

**Execution trace for PTT:**
```
ChatScreen → startVoiceRecording() → sttPipeline.startCapture() → [STT runs]
                                   → VoiceRecorder.start() → [AAC recording runs]
           → stopVoiceRecordingAndStage() → sttPipeline.stopCapture()
                                          → recorder.stop() → AAC bytes staged
                                          → sttLatestResult → placed in input field
                                          → User manually sends text

ChatScreen → toggleLiveTranslate(true) → voiceController.isMeshEnabled = true
                                       → VoiceMessageAdapter.init collector fires
                                       → On STT result: handleSttResult() → meshController.sendChat()
```

**Verdict:** 🔴 BROKEN — PTT walkie-talkie mode has backend but NO production PTT button. The "Live Translate" toggle partially enables mesh sending but is not a PTT UI.

### R5: Low-Bitrate Text Transmission

| Aspect | File | Line | Status |
|--------|------|------|--------|
| CBOR wire format | `mesh/protocol/WireCodec.kt` | — | ✅ Compact encoding |
| ChatContent carries text | `mesh/protocol/ChatContent.kt` | — | ✅ Text + attachment hash |
| voiceTextLanguage field | `mesh/protocol/ChatContent.kt` | — | ✅ Language metadata on wire |
| isAlert field | `mesh/protocol/ChatContent.kt` | — | ✅ Priority flag on wire |
| MeshManager.sendChat | `mesh/MeshManager.kt` | — | ✅ Full send path |
| VoiceMessageAdapter sends | `voice/VoiceMessageAdapter.kt:95-100` | — | ✅ Sends via meshController.sendChat |

**Verdict:** ✅ FULLY FUNCTIONAL

### R6: WiFi/Bluetooth Communication

| Aspect | File | Line | Status |
|--------|------|------|--------|
| CompositeMeshTransport | `mesh/CompositeMeshTransport.kt` | — | ✅ Dual-radio |
| BluetoothMeshTransport | `mesh/bluetooth/BluetoothMeshTransport.kt` | — | ✅ BLE L2CAP |
| WifiAwareTransport | `mesh/wifiaware/WifiAwareTransport.kt` | — | ✅ Wi-Fi Aware NAN |
| Peer discovery | `mesh/bluetooth/BleScanner.kt` + `mesh/wifiaware/NanSyncPolicy.kt` | — | ✅ |
| Store-and-forward | `mesh/ForwardSync.kt` | — | ✅ |
| E2E encryption | `mesh/crypto/MessageCrypto.kt` + ratchet | — | ✅ |
| MeshService foreground | `mesh/MeshService.kt` | — | ✅ |

**Verdict:** ✅ FULLY FUNCTIONAL (production-grade mesh)

### R7: Alert/Emergency Announcements

| Aspect | File | Line | Status |
|--------|------|------|--------|
| TtsPriority.ALERT | `tts/TtsRequest.kt:15` | — | ✅ Defined |
| Alert preempts normal | `tts/scheduler/TtsScheduler.kt:42-60` | — | ✅ Cancels current, speaks alert |
| Alert on wire | `mesh/protocol/ChatContent.kt` | — | ✅ `isAlert` field |
| Alert received → TTS | `mesh/InboundPipeline.kt:1725` | — | ✅ `isAlert` persisted |
| Alert → VoiceMessageReceiver | `voice/VoiceMessageReceiver.kt:35` | — | ✅ `TtsPriority.ALERT` |
| Audio focus for alerts | `tts/audio/TtsAudioFocusManager.kt:20` | — | 🔴 **USES `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`** |
| Audio stream for alerts | `tts/audio/TtsAudioFocusManager.kt:23` | — | 🔴 **USES `USAGE_ASSISTANCE_ACCESSIBILITY`** |
| Max volume for alerts | — | — | 🔴 **NOT SET** |
| Non-interruptible | `tts/audio/TtsAudioFocusManager.kt:52-55` | — | 🔴 **ALERT CAN BE DUCKED** |
| STREAM_ALARM | — | — | 🔴 **NOT USED** |

**Verdict:** 🟠 IMPLEMENTED BUT NOT WIRED — Alert priority exists in scheduler but audio behavior doesn't match spec (not highest volume, not non-interruptible).

### R8: RTF / Latency Measurement

| Aspect | File | Line | Status |
|--------|------|------|--------|
| STT RTF | `stt/SttBenchmark.kt:80-90` | — | ✅ Calculated |
| TTS RTF | `tts/TtsMetrics.kt:55-60` | — | ✅ Calculated |
| TTS TTFA | `tts/TtsMetrics.kt:48-52` | — | ✅ Measured |
| E2E pipeline timestamps | `voice/VoiceConversationController.kt:42-52` | — | ✅ t0-t7 defined |
| STT latency reported | `voice/VoiceMessageAdapter.kt:79` | — | ✅ `reportSttLatency(t0, t1)` |
| Outbound metrics | `voice/VoiceMessageAdapter.kt:98` | — | ✅ `reportOutboundMessageMetrics` |
| Inbound metrics | `voice/VoiceMessageReceiver.kt:33` | — | ✅ `reportInboundMessageMetrics` |
| Displayed in UI? | `ui/diagnostics/DiagnosticsScreen.kt` | — | 🔴 **NO — only mesh metrics shown** |
| Displayed in test screen? | `tts/ui/TtsTestScreen.kt:152` | — | 🟡 **TEST-SCREEN ONLY** |

**Verdict:** 🟠 IMPLEMENTED BUT NOT WIRED — Metrics are collected but only displayed in test screen, not in production DiagnosticsScreen.

### R9: 10-Language Support

| Language | STT Enum | STT Assets | STT Model | STT Loading | STT Inference | TTS Locale | TTS Init | TTS Synth | Prod UI | Offline |
|----------|----------|-----------|-----------|-------------|---------------|------------|----------|-----------|---------|---------|
| Hindi | ✅ | ✅ | ✅ | ✅ | ✅ | hi_IN | ✅ | ⚠️ | ✅ | ⚠️ |
| Gujarati | ✅ | ✅ | ✅ | ✅ | ✅ | gu_IN | ✅ | ⚠️ | ✅ | ⚠️ |
| Marathi | ✅ | ✅ | ✅ | ✅ | ✅ | mr_IN | ✅ | ⚠️ | ✅ | ⚠️ |
| Kannada | ✅ | ✅ | ✅ | ✅ | ✅ | kn_IN | ✅ | ⚠️ | ✅ | ⚠️ |
| Malayalam | ✅ | ✅ | ✅ | ✅ | ✅ | ml_IN | ✅ | ⚠️ | ✅ | ⚠️ |
| Tamil | ✅ | ✅ | ✅ | ✅ | ✅ | ta_IN | ✅ | ⚠️ | ✅ | ⚠️ |
| Telugu | ✅ | ✅ | ✅ | ✅ | ✅ | te_IN | ✅ | ⚠️ | ✅ | ⚠️ |
| **Odia** | ✅ | 🔴 | 🔴 | 🔴 | 🔴 | or_IN | ✅ | ⚠️ | ✅ | ⚠️ |
| Bengali | ✅ | ✅ | ✅ | ✅ | ✅ | bn_IN | ✅ | ⚠️ | ✅ | ⚠️ |
| English | ✅ | ✅ | ✅ | ✅ | ✅ | en_IN | ✅ | ✅ | ✅ | ✅ |

**⚠️ = TTS may fail if Android language data not pre-installed on device**

---

## PART 2 — HARDCODED BEHAVIOR

| # | File | Line | Hardcoded Value | Should Be | Severity |
|---|------|------|----------------|-----------|----------|
| H1 | `stt/VoiceActivityDetector.kt:95-100` | `DEFAULT_SILENCE_THRESHOLD=200`, `DEFAULT_SPEECH_THRESHOLD=400`, `DEFAULT_SILENCE_FRAMES=20`, `DEFAULT_SPEECH_FRAMES=3` | Configurable via constructor but defaults hardcoded; no runtime config UI | P2 |
| H2 | `stt/SttPipeline.kt:120` | `silenceTimeoutMs = 2_000L` | Should be user-configurable | P2 |
| H3 | `stt/PcmCapture.kt:175` | `SILENCE_THRESHOLD = 300` | Different from VAD threshold (200); inconsistent | P2 |
| H4 | `tts/audio/TtsAudioFocusManager.kt:20-26` | `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, `USAGE_ASSISTANCE_ACCESSIBILITY` | Alert should use `AUDIOFOCUS_GAIN` + `USAGE_ALARM` | 🔴 P0 |
| H5 | `tts/backend/AndroidTtsEngine.kt:130` | `TextToSpeech.QUEUE_FLUSH` for all requests | Alerts should use `QUEUE_FLUSH` but normal should use `QUEUE_ADD` | P1 |
| H6 | `data/settings/SettingsStore.kt:171` | `KEY_STT_LANGUAGE` defaults to `"hi"` | Reasonable default, not problematic | ⚪ OK |
| H7 | `stt/SttEngineFactory.kt:30-33` | Sherpa-ONNX tried first via `Class.forName` | Runtime probe is correct, not problematic | ⚪ OK |
| H8 | `voice/VoiceConversationController.kt:130` | `_state.value = VoiceState.IDLE` immediately after `ttsManager.speak()` | Should wait for TTS completion callback; state transitions are premature | P1 |
| H9 | `stt/SherpaOnnxEngine.kt:90` | `confidence = 0.95f` hardcoded | Sherpa-ONNX doesn't provide confidence; fake value | P2 |
| H10 | `stt/SherpaOnnxEngine.kt:130` | `chunkSize = language.sampleRate / 2` (500ms) | Creates new stream per chunk (loses context); should reuse stream | P1 |

---

## PART 3 — UI-ONLY / MOCK DETECTION

### ChatScreen (`ui/chat/ChatScreen.kt`)

| UI Element | Callback | Backend | Status |
|------------|----------|---------|--------|
| Voice record button (hold-to-talk) | `startVoiceRecording()` → `sttPipeline.startCapture()` + `VoiceRecorder.start()` | ✅ Real mic capture | ✅ REAL |
| Recording bar (elapsed, amplitude) | `recordingTicker` reads `sttPipeline.amplitude` | ✅ Real amplitude | ✅ REAL |
| STT partial text in input | `LaunchedEffect(sttPartialText)` sets `inputState.setTextAndPlaceCursorAtEnd()` | ✅ Real STT output | ✅ REAL |
| STT final text placement | `LaunchedEffect(sttLatestResult)` sets input text | ✅ Real STT final | ✅ REAL |
| "STT" toggle (Live Translate) | `onToggleLiveTranslate` → `viewModel::toggleLiveTranslate` → `voiceController.isMeshEnabled = enabled` | 🟡 Sets flag but `VoiceMessageAdapter.startVoiceMessage()` is never called from ChatScreen | 🟠 **PARTIALLY WIRED** |
| Send button | `meshManager.sendChat(text=trimmed, voiceTextLanguage=...)` | ✅ Real mesh send | ✅ REAL |
| Voice note playback | `VoicePlayer.play()` | ✅ Real audio playback | ✅ REAL |
| Language selector (Profile) | `ProfileViewModel.setSttLanguage(code)` → `SettingsStore.setSttLanguage()` | ✅ Persists to DataStore | ✅ REAL |

### TtsTestScreen (`tts/ui/TtsTestScreen.kt`) — TEST SCREEN ONLY

| UI Element | Backend | Production? |
|------------|---------|-------------|
| Language selector | `TtsTestViewModel.selectLanguage()` | 🔴 TEST ONLY |
| "Speak Normal" button | `ttsManager.speak(NORMAL)` | 🔴 TEST ONLY |
| "Speak Alert" button | `ttsManager.speak(ALERT)` | 🔴 TEST ONLY |
| Loop toggle | `voiceController.isLoopEnabled` | 🔴 TEST ONLY |
| Mesh toggle | `voiceController.isMeshEnabled` → `voiceMessageAdapter.startVoiceMessage()` | 🔴 TEST ONLY |
| Metrics display | `TtsMetricsCollector.latestMetrics` | 🔴 TEST ONLY |

**Critical finding:** The "Mesh toggle" on TtsTestScreen is the ONLY place that calls `voiceMessageAdapter.startVoiceMessage()`. This function is never called from the production ChatScreen.

---

## PART 4 — BACKEND-ONLY FEATURES (No Production UI)

| # | Feature | Backend Entry Point | Production Entry Point | Missing |
|---|---------|--------------------|-----------------------|---------|
| B1 | `VoiceMessageAdapter.startVoiceMessage()` | `voice/VoiceMessageAdapter.kt:52` | 🔴 **NOWHERE in production UI** | ChatScreen never calls it |
| B2 | `VoiceMessageReceiver.onVoiceMessageReceived()` | `voice/VoiceMessageReceiver.kt:20` | ✅ Called from `InboundPipeline.kt:1731` | ✅ Wired |
| B3 | `SttBenchmark.runFullBenchmark()` | `stt/SttBenchmark.kt:95` | 🔴 **NOWHERE** | No UI to trigger benchmark |
| B4 | `VoicePipelineMetrics` (t0-t7) | `voice/VoiceConversationController.kt:42-52` | 🔴 **Test screen only** | Not in DiagnosticsScreen |
| B5 | `TtsMetricsCollector` | `tts/metrics/TtsMetricsCollector.kt` | 🔴 **Test screen only** | Not in DiagnosticsScreen |
| B6 | Alert audio (highest volume) | `tts/TtsRequest.kt:27` (isAlert) | 🔴 **Audio behavior not implemented** | No STREAM_ALARM, no max volume |

---

## PART 5 — UI-BUT-NOT-BACKEND FEATURES

| # | UI Element | Looks Like | Actually Does | Gap |
|---|-----------|-----------|---------------|-----|
| U1 | "STT" toggle in ChatScreen header | Enables live STT→mesh | Sets `voiceController.isMeshEnabled = true` BUT `VoiceMessageAdapter.startVoiceMessage()` is never called, so the adapter's collector never fires with a routing context | 🔴 **Toggle sets flag but mesh send never happens from ChatScreen** |
| U2 | Language selector in Profile | Changes STT language | Persists to DataStore; ChatViewModel reads it for `startCapture()` | ✅ Actually works |
| U3 | DiagnosticsScreen metrics | Shows performance | Only shows mesh metrics (frames, bytes, drops) — NO STT/TTS metrics | 🟡 Incomplete |

---

## PART 6 — DEAD / UNUSED CODE

| # | Item | File | Status |
|---|------|------|--------|
| D1 | `VoskEngine.kt` | `stt/VoskEngine.kt` | ⚫ DEAD — `SttEngineFactory` tries Sherpa first; Vosk is fallback but Sherpa source is always present, so Vosk path is unreachable unless Sherpa classes are removed |
| D2 | `DefaultSttEngine.kt` | `stt/DefaultSttEngine.kt` | ⚫ DEAD — Same reason; Sherpa always available |
| D3 | `SttBenchmark.kt` | `stt/SttBenchmark.kt` | ⚫ UNUSED — No UI or test calls it |
| D4 | `VoiceConversationController.isLoopEnabled` | `voice/VoiceConversationController.kt:57` | 🔵 TEST-ONLY — Only toggled from TtsTestViewModel |
| D5 | `TtsTestViewModel` | `tts/ui/TtsTestViewModel.kt` | 🔵 TEST-ONLY — Only used by TtsTestScreen |
| D6 | `TtsTestScreen` | `tts/ui/TtsTestScreen.kt` | 🔵 TEST-ONLY — Not a production screen |

---

## PART 7 — "IMPLEMENTED BUT ACTUALLY BROKEN"

### Issue 1: Odia STT — Asset Missing

**Execution path:**
```
User selects Odia → SttPipeline.startCapture(SttLanguage.ODIA)
→ engine.initialize(SttConfig(language = ODIA))
→ SherpaOnnxEngine.loadModel(ODIA)
→ context.assets.open("stt-or/model.int8.onnx") → THROWS FileNotFoundException
→ Caught by try/catch → recognizer = null
→ inferPcm() → recognizer is null → returns SttResult.empty()
→ User sees empty transcription forever
```

**File:** `stt/SherpaOnnxEngine.kt:38-45`
**Failure:** `stt-or/` directory does not exist in assets. Model load fails silently.

### Issue 2: TTS May Fail on Clean Device

**Execution path:**
```
AndroidTtsEngine.initialize() → TextToSpeech(context, listener)
→ onInit(SUCCESS) → isReady = true
→ speak(request) → tts.isLanguageAvailable(hi_IN)
→ On device without Hindi TTS data → returns LANG_MISSING_DATA
→ speak() returns ERROR
```

**File:** `tts/backend/AndroidTtsEngine.kt:97-108`
**Failure:** `isLanguageAvailable()` returns `MissingData` → `speak()` returns `Error("Language unavailable")`.

### Issue 3: Live Translate Toggle Doesn't Actually Send Over Mesh

**Execution path:**
```
User toggles "STT" switch ON
→ ChatViewModel.toggleLiveTranslate(true)
→ voiceController.isMeshEnabled = true
→ VoiceMessageAdapter.init collector checks: if (voiceController.isMeshEnabled) handleSttResult(result)
→ BUT: VoiceMessageAdapter.currentContext is null (startVoiceMessage() was never called)
→ handleSttResult() → currentContext.get() returns null → return (line 84)
→ NOTHING HAPPENS
```

**File:** `voice/VoiceMessageAdapter.kt:82-84`
**Failure:** `currentContext` is never set because `startVoiceMessage()` is never called from ChatScreen.

### Issue 4: Alert Audio Not at Highest Volume

**Execution path:**
```
Alert message received → VoiceMessageReceiver.onVoiceMessageReceived()
→ TtsRequest(priority = ALERT)
→ TtsScheduler.handleAlert() → engine.stop() → engine.speak(request)
→ AndroidTtsEngine.speak() → tts.speak(text, QUEUE_FLUSH, bundle, utteranceId)
→ Audio plays on STREAM_MUSIC at normal volume, can be ducked by other apps
```

**File:** `tts/audio/TtsAudioFocusManager.kt:20-26`
**Failure:** Uses `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` and `USAGE_ASSISTANCE_ACCESSIBILITY`. Other apps can duck this audio. Not on alarm stream. Not at max volume.

### Issue 5: VoiceConversationController State Transitions Are Premature

**Execution path:**
```
handleSttResult() → _state.value = VoiceState.SPEAKING
→ ttsManager.speak(request)  [returns immediately, TTS is async]
→ _state.value = VoiceState.IDLE  [immediately, before TTS finishes]
```

**File:** `voice/VoiceConversationController.kt:130-138`
**Failure:** State goes SPEAKING→IDLE instantly. The "speaking" indicator never actually shows.

---

## PART 8 — OFFLINE CLAIM VERIFICATION

| Component | Claimed Offline? | Actually Offline? | Evidence |
|-----------|-----------------|-------------------|----------|
| STT (Sherpa-ONNX) | ✅ | ✅ | Models in assets, native .so bundled, no network calls |
| STT (Vosk) | ✅ | ✅ | Gradle dep, models extracted to filesDir |
| TTS (Android) | ✅ | ⚠️ | **May require downloading language data for Indic languages** |
| Mesh (BLE + Wi-Fi Aware) | ✅ | ✅ | Pure local radio, no internet |
| Content moderation (TFLite) | ✅ | ✅ | Models in assets |
| **Internet plane (Spool)** | N/A | ⚠️ | Off by default in release; `BuildConfig.INTERNET_PLANE = false` |

**Critical:** Android TTS for Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali may require downloading voice data. Only English is typically pre-installed.

---

## PART 9 — ALL 10 LANGUAGES AUDIT

### Hindi (hi)

| Check | Status | Evidence |
|-------|--------|----------|
| STT enum | ✅ | `SttLanguage.HINDI` in `SttLanguage.kt:18` |
| STT asset dir | ✅ | `assetDir = "stt-hi"` |
| STT model files | ✅ | `assets/stt-hi/model.int8.onnx` + `tokens.txt` |
| STT loading | ✅ | `SherpaOnnxEngine.loadModel()` loads from assets |
| STT inference | ✅ | Paraformer model, 16kHz |
| TTS locale | ✅ | `Locale("hi", "IN")` |
| TTS data present? | ⚠️ | Depends on device; may need download |
| Production UI | ✅ | Language selector in Profile; default language |
| Offline STT | ✅ | Bundled model |
| Offline TTS | ⚠️ | Android TTS may need data |

### Gujarati (gu) through Bengali (bn) — Same pattern as Hindi
All 8 Indic languages have STT models bundled. TTS depends on Android data.

### Odia (or)

| Check | Status | Evidence |
|-------|--------|----------|
| STT enum | ✅ | `SttLanguage.ODIA` in `SttLanguage.kt:21` |
| STT asset dir | ✅ | `assetDir = "stt-or"` |
| STT model files | 🔴 | **`assets/stt-or/` does not exist** |
| STT loading | 🔴 | Fails silently → returns empty results |
| STT inference | 🔴 | Never reached |
| TTS locale | ✅ | `Locale("or", "IN")` |
| TTS data present? | ⚠️ | Very unlikely pre-installed on any device |
| Production UI | ✅ | Listed in language selector |
| Offline STT | 🔴 | **No model = no STT** |
| Offline TTS | ⚠️ | Android TTS almost certainly needs download |

### English (en)

| Check | Status | Evidence |
|-------|--------|----------|
| STT | ✅ | Model bundled, works |
| TTS | ✅ | Usually pre-installed on all Android devices |
| Offline | ✅ | Fully offline |

---

## PART 10 — PTT WALKIE-TALKIE FLOW TRACE

```
INTENDED FLOW:
User presses PTT → Mic starts → PCM capture → VAD → STT → Final sentence
→ Language metadata → Mesh encoding → Transmission → Remote reception
→ Decoding → Remote TTS → Audio playback → State updates

ACTUAL FLOW (Production ChatScreen):
1. User holds voice button → ChatViewModel.startVoiceRecording()
2. → sttPipeline.startCapture(selectedLanguage)  [PCM mic opens]
3. → VoiceRecorder.start()  [AAC recording also starts]
4. → User speaks → VAD detects speech → PCM accumulates
5. → User releases → stopVoiceRecordingAndStage()
6. → sttPipeline.stopCapture()  [Final STT result produced]
7. → recorder.stop()  [AAC bytes produced]
8. → sttLatestResult flows to LaunchedEffect → text placed in input field
9. → AAC bytes staged as voice note attachment
10. → User manually taps Send button
11. → meshManager.sendChat(text, attachment, voiceTextLanguage=name)
12. → Remote receives → InboundPipeline → VoiceMessageReceiver
13. → TtsRequest(priority=NORMAL) → TtsManager.speak()
14. → AndroidTtsEngine.speak() → Speaker plays

GAPS:
- Step 1: No dedicated PTT button; uses existing voice note button
- Step 8: STT text goes to INPUT FIELD, not auto-sent
- Step 9: Voice note is staged for review, not auto-sent
- Step 10: USER MUST MANUALLY TAP SEND — not walkie-talkie behavior
- Step 12-13: Only triggers TTS if voiceTextLanguage is set (requires "STT" toggle ON)
- If "STT" toggle is OFF: voiceTextLanguage = null → VoiceMessageReceiver ignores it → NO TTS on remote

CONNECTED: 1-7 (capture + STT works)
PARTIALLY CONNECTED: 8-9 (text appears, voice note staged)
MISSING: Auto-send after STT completion
MISSING: Dedicated PTT button with visual state
MISSING: Auto-TTS on remote without toggle
```

---

## PART 11 — ALERT / EMERGENCY FLOW TRACE

```
INTENDED FLOW:
Alert creation → Message encoding → Mesh transmission → Remote reception
→ Priority handling → TTS scheduler → Audio focus → Audio stream
→ Volume → Playback → Interruption/preemption

ACTUAL FLOW:
1. User composes message with isAlert=true (no UI for this in ChatScreen)
2. → meshManager.sendChat(isAlert=true)
3. → ChatContent(isAlert=true) encoded in CBOR
4. → Transmitted over mesh
5. → Remote InboundPipeline: entity.isAlert = content.isAlert ?: false
6. → VoiceMessageReceiver.onVoiceMessageReceived(entity)
7. → TtsRequest(priority = TtsPriority.ALERT)
8. → TtsScheduler.handleAlert() → cancels current → engine.speak(alert)
9. → AndroidTtsEngine.speak() → tts.speak(text, QUEUE_FLUSH, bundle, utteranceId)
10. → Audio plays on STREAM_MUSIC (not STREAM_ALARM)
11. → Volume at default (not max)
12. → Other apps can duck this audio

GAPS:
- Step 1: NO UI to compose alert messages in ChatScreen
- Step 10: Wrong audio stream (should be STREAM_ALARM)
- Step 11: No volume boost
- Step 12: Not non-interruptible (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK allows ducking)
- No visual indicator that message is an alert
- No special notification for alerts
```

---

## PART 12 — PERFORMANCE / METRICS TRUTH AUDIT

| Metric | Collected? | Displayed? | Location | Truth |
|--------|-----------|-----------|----------|-------|
| STT init time | ✅ | ❌ | `SttBenchmark` (never called) | REAL but UNUSED |
| STT inference latency | ✅ | ❌ | `SttResult.durationMs` | REAL |
| STT RTF | ✅ | ❌ | `SttBenchmark` (never called) | REAL but UNUSED |
| STT non-empty rate | ✅ | ❌ | `SttBenchmark` (never called) | REAL but UNUSED |
| TTS TTFA | ✅ | 🔵 Test screen only | `TtsMetricsCollector` | REAL |
| TTS RTF | ✅ | 🔵 Test screen only | `TtsMetrics.rtf` | REAL |
| E2E timestamps | ✅ | 🔵 Test screen only | `VoicePipelineMetrics` | REAL |
| WER | ❌ | ❌ | — | NOT IMPLEMENTED |
| CPU usage | ❌ | ❌ | — | NOT IMPLEMENTED |
| RAM footprint | ❌ | ❌ | — | NOT IMPLEMENTED |
| Model size on disk | ❌ | ❌ | `SttModelInfo.sizeBytes` exists but never populated | NOT POPULATED |
| Mesh frames | ✅ | ✅ DiagnosticsScreen | `MeshMetrics` | REAL |
| Mesh bytes | ✅ | ✅ DiagnosticsScreen | `MeshMetrics` | REAL |
| Payload size | ✅ | ❌ | `VoiceMessageAdapter` calculates but doesn't display | REAL |

**All collected metrics are REAL (not mocked). But most are not displayed in production UI.**

---

## PART 13 — FALLBACK ANALYSIS

| Fallback | Primary | Failure Condition | Fallback | Functional? | Hides Failure? |
|----------|---------|-------------------|----------|-------------|----------------|
| `SttEngineFactory` → Vosk → Default | Sherpa-ONNX | Class not found | Vosk | ✅ Yes | No |
| Vosk → DefaultSttEngine | Vosk | Class not found | Empty results | ⚠️ Degrades silently | **YES** |
| DefaultSttEngine → empty results | Any engine | Model missing | `SttResult.empty()` | ⚠️ Returns empty | **YES** |
| SherpaOnnxEngine → empty results | Sherpa | Asset not found | `SttResult.empty()` | ⚠️ Returns empty | **YES** |
| AndroidTtsEngine → error | TTS | Language data missing | `TtsResult.Error` | ⚠️ Returns error | No (error propagated) |
| `voiceMessageReceiver?` (nullable) | VoiceMessageReceiver | Koin `getOrNull()` returns null | null → skipped | ⚠️ No TTS on received voice | **YES** |

**Critical:** The `getOrNull()` for VoiceMessageReceiver in `MeshModule.kt:136` means if Koin can't resolve it, received voice messages silently produce no TTS. Since VoiceModule is registered in SwarSetuApplication, it should resolve — but the nullable pattern means a wiring failure is invisible.

---

## PART 14 — BUILD / DEPENDENCY REALITY

| Aspect | Status | Risk |
|--------|--------|------|
| Sherpa-ONNX Kotlin API | Manually bundled as source in `com.k2fsa.sherpa.onnx` | 🟡 Works but fragile; no version pinning |
| Sherpa-ONNX native .so | Manually placed in `jniLibs/` | 🟡 Works but no ABI verification at build time |
| ONNX Runtime native .so | Manually placed in `jniLibs/` | 🟡 Same |
| Vosk Android AAR | Gradle dependency | ✅ Properly managed |
| LiteRT (TFLite) | Gradle dependency | ✅ Properly managed |
| Clean build from fresh clone? | ⚠️ | Sherpa .so files are in git; should work but no CI verification |
| F-Droid build? | ⚠️ | Sherpa .so + Vosk .so may cause issues; needs testing |
| ABI coverage | arm64-v8a + x86_64 | ✅ Covers real devices + emulators |

---

## PART 15 — MASTER ISSUE LIST

### ISSUE-001
- **CATEGORY:** BROKEN
- **FEATURE:** Odia STT
- **SEVERITY:** P0
- **STATUS:** 🔴 NON-FUNCTIONAL
- **FILE(S):** `assets/stt-or/` (missing directory)
- **ENTRY POINT:** `SttPipeline.startCapture(SttLanguage.ODIA)`
- **ACTUAL BEHAVIOR:** Model load fails silently → empty results forever
- **EXPECTED BEHAVIOR:** Odia speech transcribed correctly
- **WHY:** No model files bundled for Odia
- **DEPENDENCIES:** Need Odia Paraformer model from k2-fsa
- **FIX REQUIRED:** Download/bundle Odia model
- **PRODUCTION IMPACT:** 1 of 10 required languages completely non-functional

### ISSUE-002
- **CATEGORY:** OFFLINE_VIOLATION / REQUIREMENT_MISMATCH
- **FEATURE:** TTS offline operation
- **SEVERITY:** P0
- **STATUS:** 🔴 MAY FAIL
- **FILE(S):** `tts/backend/AndroidTtsEngine.kt:35`, `tts/backend/AndroidTtsEngine.kt:97-108`
- **ENTRY POINT:** `AndroidTtsEngine.initialize()` → `speak()`
- **ACTUAL BEHAVIOR:** Android TTS may return `LANG_MISSING_DATA` for Indic languages
- **EXPECTED BEHAVIOR:** TTS works fully offline without any downloads
- **WHY:** Android TTS voice data not bundled; downloaded at runtime
- **DEPENDENCIES:** Need offline TTS engine (Sherpa-ONNX TTS / Piper)
- **FIX REQUIRED:** Replace or supplement Android TTS with bundled offline engine
- **PRODUCTION IMPACT:** TTS may silently fail for 9/10 languages on clean device

### ISSUE-003
- **CATEGORY:** REQUIREMENT_MISMATCH
- **FEATURE:** Alert audio behavior
- **SEVERITY:** P0
- **STATUS:** 🔴 DOES NOT MEET SPEC
- **FILE(S):** `tts/audio/TtsAudioFocusManager.kt:20-26`
- **ENTRY POINT:** `TtsManager.speak(TtsRequest(priority=ALERT))`
- **ACTUAL BEHAVIOR:** Plays on STREAM_MUSIC, AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, default volume
- **EXPECTED BEHAVIOR:** Highest volume, non-interruptible, STREAM_ALARM
- **WHY:** No alert-specific audio routing implemented
- **DEPENDENCIES:** None
- **FIX REQUIRED:** Add alert-specific audio attributes, volume boost, non-interruptible focus
- **PRODUCTION IMPACT:** Emergency alerts play at normal volume and can be silenced

### ISSUE-004
- **CATEGORY:** NOT_WIRED / BACKEND_ONLY
- **FEATURE:** PTT walkie-talkie mode
- **SEVERITY:** P0
- **STATUS:** 🔴 NO PRODUCTION UI
- **FILE(S):** `voice/VoiceMessageAdapter.kt:52`, `ui/chat/ChatScreen.kt`
- **ENTRY POINT:** `VoiceMessageAdapter.startVoiceMessage()` — never called from ChatScreen
- **ACTUAL BEHAVIOR:** Backend exists but no PTT button in production UI
- **EXPECTED BEHAVIOR:** Dedicated PTT button with visual state indicator
- **WHY:** ChatScreen uses existing voice note button; no PTT-specific UI
- **DEPENDENCIES:** None
- **FIX REQUIRED:** Add PTT button + state indicator to ChatScreen
- **PRODUCTION IMPACT:** Core demo feature (walkie-talkie) has no production UI

### ISSUE-005
- **CATEGORY:** NOT_WIRED
- **FEATURE:** Live Translate toggle → mesh send
- **SEVERITY:** P0
- **STATUS:** 🟠 TOGGLE SETS FLAG BUT MESH SEND NEVER HAPPENS
- **FILE(S):** `voice/VoiceMessageAdapter.kt:82-84`, `ui/chat/ChatViewModel.kt:259-261`
- **ENTRY POINT:** Toggle ON → `voiceController.isMeshEnabled = true`
- **ACTUAL BEHAVIOR:** `VoiceMessageAdapter.currentContext` is null → `handleSttResult()` returns early
- **EXPECTED BEHAVIOR:** STT results auto-sent over mesh
- **WHY:** `startVoiceMessage()` is never called; `currentContext` never set
- **DEPENDENCIES:** None
- **FIX REQUIRED:** Wire ChatScreen to call `voiceMessageAdapter.startVoiceMessage()` when toggle is ON
- **PRODUCTION IMPACT:** STT→Mesh pipeline is broken from production UI

### ISSUE-006
- **CATEGORY:** BUILD_RISK
- **FEATURE:** Sherpa-ONNX dependency
- **SEVERITY:** P1
- **STATUS:** 🟡 MANUALLY BUNDLED
- **FILE(S):** `app/src/main/java/com/k2fsa/sherpa/onnx/*.kt`, `app/src/main/jniLibs/`
- **ENTRY POINT:** `SttEngineFactory.create()` → `Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")`
- **ACTUAL BEHAVIOR:** Works because source files are committed to repo
- **EXPECTED BEHAVIOR:** Gradle-managed dependency
- **WHY:** No `implementation("com.k2fsa.sherpa.onnx:...")` in build.gradle.kts
- **DEPENDENCIES:** None
- **FIX REQUIRED:** Either add Gradle dependency or document manual bundling
- **PRODUCTION IMPACT:** Clean build may fail if .so files are not committed

### ISSUE-007
- **CATEGORY:** NOT_WIRED
- **FEATURE:** STT/TTS metrics in DiagnosticsScreen
- **SEVERITY:** P1
- **STATUS:** 🟠 METRICS COLLECTED BUT NOT DISPLAYED
- **FILE(S):** `ui/diagnostics/DiagnosticsScreen.kt`, `ui/diagnostics/DiagnosticsViewModel.kt`
- **ENTRY POINT:** DiagnosticsScreen
- **ACTUAL BEHAVIOR:** Only shows mesh metrics (frames, bytes, drops)
- **EXPECTED BEHAVIOR:** Shows STT/TTS latency, RTF, E2E timing
- **WHY:** DiagnosticsViewModel doesn't inject TtsMetricsCollector or SttBenchmark
- **DEPENDENCIES:** None
- **FIX REQUIRED:** Extend DiagnosticsViewModel with STT/TTS metrics
- **PRODUCTION IMPACT:** Judges can't see performance metrics in production app

### ISSUE-008
- **CATEGORY:** PARTIAL
- **FEATURE:** VoiceConversationController state transitions
- **SEVERITY:** P1
- **STATUS:** 🟡 PREMATURE STATE TRANSITIONS
- **FILE(S):** `voice/VoiceConversationController.kt:130-138`
- **ENTRY POINT:** `handleSttResult()`
- **ACTUAL BEHAVIOR:** SPEAKING→IDLE happens instantly (before TTS finishes)
- **EXPECTED BEHAVIOR:** State stays SPEAKING until TTS completes
- **WHY:** No TTS completion callback wired
- **DEPENDENCIES:** None
- **FIX REQUIRED:** Listen to TtsMetricsCollector completion event
- **PRODUCTION IMPACT:** "Speaking" indicator never actually shows

### ISSUE-009
- **CATEGORY:** DEAD_CODE
- **FEATURE:** VoskEngine (unreachable)
- **SEVERITY:** P2
- **STATUS:** ⚫ DEAD
- **FILE(S):** `stt/VoskEngine.kt`
- **WHY:** Sherpa-ONNX source is always present → factory always returns SherpaOnnxEngine
- **FIX:** Can keep as fallback; document as intentional

### ISSUE-010
- **CATEGORY:** DEAD_CODE
- **FEATURE:** SttBenchmark (never called)
- **SEVERITY:** P2
- **STATUS:** ⚫ UNUSED
- **FILE(S):** `stt/SttBenchmark.kt`
- **WHY:** No UI or test invokes it
- **FIX:** Wire to DiagnosticsScreen or create benchmark trigger

### ISSUE-011
- **CATEGORY:** HARDCODED
- **FEATURE:** Sherpa-ONNX confidence value
- **SEVERITY:** P2
- **STATUS:** 🟤 HARDCODED
- **FILE(S):** `stt/SherpaOnnxEngine.kt:90,115`
- **VALUE:** `confidence = 0.95f`
- **WHY:** Sherpa-ONNX doesn't provide confidence; fake value shown to user
- **FIX:** Use -1f (unknown) or implement proper confidence estimation

### ISSUE-012
- **CATEGORY:** PARTIAL
- **FEATURE:** Sherpa-ONNX streaming context
- **SEVERITY:** P2
- **STATUS:** 🟡 LOSING CONTEXT BETWEEN CHUNKS
- **FILE(S):** `stt/SherpaOnnxEngine.kt:125-155`
- **WHY:** Creates new `OfflineStream` per 500ms chunk instead of reusing one
- **FIX:** Reuse stream object across chunks for better accuracy

---

## FINAL SUMMARY

### 1. TRUE FUNCTIONAL FEATURES
- STT for 9 languages (Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Bengali, English) via Sherpa-ONNX
- VAD / pause detection / sentence formation
- PCM microphone capture at 16kHz
- Mesh networking (BLE + Wi-Fi Aware) with E2E encryption
- Voice note recording (AAC) and playback
- Text messaging over mesh
- Voice note attachment over mesh
- Store-and-forward
- Content moderation (TFLite)
- STT partial text display during recording
- STT final text placement in input field
- Language persistence in SettingsStore
- Profile language selector
- Mesh diagnostics (frames, bytes, drops)

### 2. HARDCODED FEATURES
- VAD thresholds (200/400/20/3) — reasonable defaults
- Silence timeout (2000ms) — reasonable default
- Sherpa confidence (0.95f) — fake value
- Audio focus for TTS (AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) — wrong for alerts
- Audio usage (USAGE_ASSISTANCE_ACCESSIBILITY) — wrong for alerts
- Chunk size for streaming (500ms) — could be optimized

### 3. UI-ONLY / MOCK FEATURES
- "STT" toggle in ChatScreen — sets flag but mesh send never happens
- TtsTestScreen — full test UI with loop/mesh/metrics, not production
- DiagnosticsScreen metrics section — only mesh metrics, no STT/TTS

### 4. BACKEND-ONLY FEATURES
- `VoiceMessageAdapter.startVoiceMessage()` — never called from production UI
- `SttBenchmark.runFullBenchmark()` — no UI to trigger
- `VoicePipelineMetrics` (t0-t7) — collected but only shown in test screen
- `TtsMetricsCollector` — collected but only shown in test screen
- Alert audio preemption — works in scheduler but audio behavior wrong

### 5. PARTIALLY IMPLEMENTED FEATURES
- PTT walkie-talkie — backend complete, UI missing
- Alert/emergency — priority exists, audio behavior doesn't match spec
- Live Translate — toggle exists, mesh wiring broken
- VoiceConversationController state — premature transitions

### 6. BROKEN FEATURES
- Odia STT — no model files
- TTS for Indic languages — may fail on clean device (needs download)
- Alert audio — not highest volume, not non-interruptible
- Live Translate mesh send — `currentContext` never set

### 7. DEAD / UNUSED IMPLEMENTATIONS
- VoskEngine — unreachable (Sherpa always present)
- DefaultSttEngine — unreachable
- SttBenchmark — never called
- TtsTestViewModel — test-only

### 8. FALSE / UNSUPPORTED CLAIMS
- "Fully offline TTS" — Android TTS may require downloads for Indic languages
- "Alert type messages will be announced at highest volume non-interruptible" — not implemented
- "Push-to-talk walkie-talkie mode" — no production UI
- "10 Indian Languages" — Odia STT is broken

### 9. P0 BLOCKERS
1. **Odia STT model missing** — 1/10 languages non-functional
2. **TTS not fully offline** — may fail on clean device
3. **Alert audio doesn't meet spec** — not highest volume, not non-interruptible
4. **No PTT walkie-talkie UI** — core demo feature missing
5. **Live Translate mesh send broken** — toggle sets flag but send never happens

### 10. P1 ISSUES
6. Sherpa-ONNX has no Gradle dependency (source bundled)
7. STT/TTS metrics not shown in DiagnosticsScreen
8. VoiceConversationController premature state transitions
9. No WER measurement

### 11. P2 ISSUES
10. VoskEngine/DefaultSttEngine dead code
11. SttBenchmark never called
12. Hardcoded confidence value
13. Sherpa streaming loses context between chunks
14. VAD thresholds not user-configurable

### 12. PRODUCTION USER JOURNEY AUDIT

**A. Start app** → ✅ App starts, mesh service starts, peer discovery begins
**B. Select language** → ✅ Profile screen → language selector → persists to DataStore
**C. Press PTT** → 🔴 No PTT button; must use voice note button
**D. Speak** → ✅ Mic opens, VAD detects speech, PCM captured
**E. STT converts speech** → ✅ Text appears in input field (or partial during recording)
**F. Message transmitted** → 🟡 Must manually tap Send; voiceTextLanguage only set if "STT" toggle ON
**G. Remote receives** → ✅ InboundPipeline delivers to VoiceMessageReceiver (if voiceTextLanguage set)
**H. Remote TTS speaks** → ⚠️ Only if Android TTS data is installed for that language
**I. Send alert** → 🔴 No UI to compose alert messages
**J. Alert preempts** → 🟠 Preemption works but audio not at highest volume / non-interruptible
**K. Repeat for all 10 languages** → 🔴 Odia broken; TTS may fail for 9/10 Indic languages
**L. Disable internet, repeat** → ⚠️ STT works offline; TTS may fail offline

### 13. FINAL COMPLETENESS SCORE

**Calculation method:** Count verified requirements, classify by actual runtime status.

| Category | Count | % |
|----------|-------|---|
| ✅ Fully Functional | 14 | 40% |
| 🟡 Partially Functional | 5 | 14% |
| 🟠 Implemented But Not Wired | 4 | 11% |
| 🔵 UI Only / Mock | 2 | 6% |
| 🟣 Backend Only | 3 | 9% |
| 🔴 Broken / Non-Functional | 4 | 11% |
| ⚫ Dead / Unused | 3 | 9% |
| **Total verified requirements** | **35** | **100%** |

**Functional %: 40%**
**Partial %: 14%**
**UI-only %: 6%**
**Backend-only %: 9%**
**Broken %: 11%**
**Not implemented / Dead %: 20%**

**Overall evidence-based completeness: ~40% truly functional, ~55% with partial/backend/UI-only, ~11% broken**

**To reach competition-ready (~90%+), the 5 P0 blockers must be fixed, and P1 items addressed.**
