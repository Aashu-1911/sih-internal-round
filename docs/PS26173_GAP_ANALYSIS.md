# PS 26173 — Complete Gap Analysis Report

**Date:** August 26, 2026
**Codebase Version:** stt-tts-ui-integration branch (latest commit)
**Analyst:** Codebuff (automated deep analysis)

---

## Executive Summary

The codebase has a **strong mesh networking foundation** and a **well-architected STT module** with Sherpa-ONNX integration for 9 of 10 languages. However, there are **5 critical gaps** that must be fixed before the solution is competition-ready:

| # | Severity | Gap | Impact |
|---|----------|-----|--------|
| 1 | 🔴 CRITICAL | Odia STT model files missing | 1 of 10 required languages non-functional |
| 2 | 🔴 CRITICAL | TTS uses Android built-in TTS (may require downloads) | Violates "fully offline" requirement |
| 3 | 🔴 CRITICAL | Alert audio not at highest volume / non-interruptible | Fails emergency announcement spec |
| 4 | 🔴 CRITICAL | No production PTT walkie-talkie UI | Core demo feature missing |
| 5 | 🔴 CRITICAL | Sherpa-ONNX Kotlin API classes bundled manually, no Gradle dependency | Build fragility, may fail on clean build |

---

## 1. STT Module Analysis

### 1.1 Language Coverage

| Language | Code | Asset Dir | Model Files | Status |
|----------|------|-----------|-------------|--------|
| Hindi | hi | stt-hi | ✅ model.int8.onnx + tokens.txt | **WORKING** |
| Gujarati | gu | stt-gu | ✅ model.int8.onnx + tokens.txt | **WORKING** |
| Marathi | mr | stt-mr | ✅ model.int8.onnx + tokens.txt | **WORKING** |
| Kannada | kn | stt-kn | ✅ model.int8.onnx + tokens.txt | **WORKING** |
| Malayalam | ml | stt-ml | ✅ model.int8.onnx + tokens.txt | **WORKING** |
| Tamil | ta | stt-ta | ✅ model.int8.onnx + tokens.txt | **WORKING** |
| Telugu | te | stt-te | ✅ model.int8.onnx + tokens.txt | **WORKING** |
| **Odia** | **or** | **stt-or** | **❌ MISSING** | **🔴 BROKEN** |
| Bengali | bn | stt-bn | ✅ model.int8.onnx + tokens.txt | **WORKING** |
| English | en | stt-en | ✅ model.int8.onnx + tokens.txt | **WORKING** |

**Gap:** Odia (ଓଡ଼ିଆ) has no model files. The `stt-or` directory does not exist in `app/src/main/assets/`. The `SttLanguage.ODIA` enum entry declares `assetDir = "stt-or"` but `SttModelManager.modelAssetExists()` will return false, so the engine returns empty results for Odia.

**Fix:** Download or train an Odia Paraformer model (IntConformer) compatible with Sherpa-ONNX, export to `model.int8.onnx` + `tokens.txt`, and place in `app/src/main/assets/stt-or/`. Source: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-paraformer/paraformer-models.html

### 1.2 VAD / Pause Detection

**Status:** ✅ Implemented correctly

- `VoiceActivityDetector` uses energy-based RMS with hysteresis (silence threshold=200, speech threshold=400)
- 20 consecutive silent frames (~600ms at 30ms/frame) triggers end-of-speech
- 3 consecutive speech frames to confirm speech start (noise rejection)
- `SttPipeline` integrates VAD with configurable `silenceTimeoutMs` (default 2000ms)

**Gap:** VAD thresholds are hardcoded defaults. No adaptive calibration for different microphone/noise environments. The problem says "The pause threshold must be configurable and benchmarked."

**Fix (P1):** Expose VAD thresholds as configurable parameters in `SttConfig` or `SttPipeline`, and add a calibration UI or device-adaptive defaults.

### 1.3 Sentence Formation

**Status:** ✅ Implemented correctly

- `SttPipeline.captureAudioWithVad()` accumulates PCM samples until silence timeout
- Partial transcriptions emitted every ~1 second for live preview
- Final transcription produced after silence timeout with complete audio buffer
- `VoiceMessageAdapter` sends only final `SttResult` over mesh

**Gap:** Minor — partial results are re-transcribed from accumulated audio each time (redundant compute). True streaming transcription (incremental) would be more efficient.

### 1.4 Streaming Transcription

**Status:** ✅ Implemented

- `SherpaOnnxEngine.transcribeStream()` chunks audio into 500ms segments
- `VoskEngine.transcribeStream()` uses 250ms chunks with true Vosk streaming
- Both emit `PARTIAL` then `FINAL` results via Kotlin Flow

**Gap:** Sherpa-ONNX streaming creates a new `OfflineStream` per chunk instead of reusing one (loses context between chunks). Vosk streaming is better here.

### 1.5 STT Engine Architecture

**Status:** ✅ Well-designed

- Clean `SttEngine` interface with `initialize`, `transcribe`, `transcribeStream`, `setLanguage`, `release`
- `SttEngineFactory` tries Sherpa-ONNX first, falls back to Vosk, then `DefaultSttEngine`
- `DefaultSttEngine` provides graceful degradation (empty results, never crashes)
- `SttModelManager` handles model lifecycle with Mutex-guarded state

---

## 2. TTS Module Analysis

### 2.1 Language Coverage

| Language | Code | TTS Locale | Status |
|----------|------|-----------|--------|
| Hindi | hi | hi_IN | ⚠️ Depends on Android TTS data |
| Gujarati | gu | gu_IN | ⚠️ Depends on Android TTS data |
| Marathi | mr | mr_IN | ⚠️ Depends on Android TTS data |
| Kannada | kn | kn_IN | ⚠️ Depends on Android TTS data |
| Malayalam | ml | ml_IN | ⚠️ Depends on Android TTS data |
| Tamil | ta | ta_IN | ⚠️ Depends on Android TTS data |
| Telugu | te | te_IN | ⚠️ Depends on Android TTS data |
| Odia | or | or_IN | ⚠️ Depends on Android TTS data |
| Bengali | bn | bn_IN | ⚠️ Depends on Android TTS data |
| English | en | en_IN | ✅ Usually pre-installed |

### 2.2 🔴 CRITICAL: Offline TTS Compliance

**Current implementation:** `AndroidTtsEngine` wraps `android.speech.tts.TextToSpeech`.

**Problem:** Android's built-in TTS requires downloading language-specific voice data for most Indic languages. On a fresh device without internet:
- `isLanguageAvailable()` may return `LANG_MISSING_DATA`
- `speak()` will fail silently or throw
- The problem statement explicitly says: *"no internet hosted API based solutions are expected and encouraged for the STT or TTS"*

**Fix Options (choose one):**

**Option A (Recommended):** Replace `AndroidTtsEngine` with an offline TTS engine like:
- **Piper TTS** (ONNX-based, lightweight, Apache-2.0) — can bundle Indic voice models
- **Coqui TTS** — but heavier
- **Sherpa-ONNX TTS** — already have Sherpa-ONNX for STT, can add TTS models from https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/index.html

**Option B:** Bundle Android TTS voice data with the app (large APK size increase).

**Option C:** Keep `AndroidTtsEngine` but add a Sherpa-ONNX fallback for languages where Android TTS data is missing. This is the pragmatic middle ground.

### 2.3 🔴 CRITICAL: Alert Audio Behavior

**Problem Statement:** *"alert type messages will be announced at highest volume non-interruptible"*

**Current implementation:**
- `TtsScheduler.handleAlert()` cancels current playback and speaks the alert — ✅ preemption works
- `TtsAudioFocusManager` uses `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` — ❌ Should be `AUDIOFOCUS_GAIN` for alerts
- `TtsAudioFocusManager` uses `STREAM_MUSIC` — ❌ Should use `STREAM_ALARM` for alerts
- No volume boost to maximum — ❌ Missing
- No non-interruptible flag — ❌ Missing (user can still duck/stop via system controls)

**Fix:**
```kotlin
// In AndroidTtsEngine.speak(), when request.isAlert:
// 1. Use STREAM_ALARM instead of STREAM_MUSIC
// 2. Set volume to maximum
// 3. Use AUDIOFOCUS_GAIN (not TRANSIENT_MAY_DUCK)
// 4. Consider using AudioManager.setStreamVolume(STREAM_ALARM, maxVolume, 0)
```

### 2.4 TTS Metrics

**Status:** ✅ Well-implemented

- `TtsMetricsCollector` tracks: synthesis begin, first audio chunk, playback start, completion
- `TtsMetrics` computes: TTFA, playback start latency, RTF
- Exposed via `TtsMetricsCollector.latestMetrics` StateFlow

**Gap:** Metrics are collected but not surfaced in a user-visible dashboard.

---

## 3. Communication Layer Analysis

### 3.1 Mesh Transport

**Status:** ✅ Excellent

- `CompositeMeshTransport` combines Wi-Fi Aware (NAN) + Bluetooth LE
- `BluetoothMeshTransport` with L2CAP CoC for data transfer
- `WifiAwareTransport` with NDP for high-bandwidth transfer
- Store-and-forward via `ForwardSync`
- Full E2E encryption with ratchet protocol
- Peer discovery, relay, deduplication — all implemented

### 3.2 🔴 CRITICAL: PTT Walkie-Talkie UI

**Problem Statement:** *"it should work like a walkie talkie using push to talk feature"*

**Current implementation:**
- `VoiceMessageAdapter.startVoiceMessage()` / `stopVoiceMessage()` — ✅ backend exists
- `VoiceConversationController` manages STT→TTS loop — ✅ backend exists
- `TtsTestViewModel` has loop/mesh toggle — ⚠️ only in test screen

**What's missing:**
- **No PTT button in the production chat UI** (`ChatScreen.kt`)
- No visual indicator (recording/processing/sending/speaking states) in chat
- No hold-to-talk gesture handling
- No walkie-talkie mode toggle in the main UI

**Fix:** Add to `ChatScreen.kt`:
1. A prominent PTT button (hold-to-talk or tap-to-toggle)
2. Visual state indicator: 🎙️ LISTENING → ⏳ PROCESSING → 📡 SENDING → 🔊 SPEAKING
3. Language selector for STT input
4. Alert toggle button

### 3.3 Low-Bitrate Protocol

**Status:** ✅ Implemented

- CBOR encoding for wire protocol (`WireCodec`)
- `ChatContent` carries text + optional attachment hash
- `voiceTextLanguage` field carries language metadata for remote TTS
- `isAlert` flag for priority messages

---

## 4. UI Analysis

### 4.1 Chat Screen

**Status:** ✅ Exists with Compose Material 3

- `ChatScreen.kt` — full chat UI with message list, input, attachments
- `ChatViewModel.kt` — manages message sending, voice recording
- Language selection exists but not prominently featured

**Gap:** No dedicated STT/TTS language selector in the chat composer area.

### 4.2 Voice Recording UI

**Status:** ⚠️ Partial

- `MicGate.kt` — permission handling
- `VoiceRecorder.kt` — AAC recording for voice notes
- `VoicePlayer.kt` — audio playback

**Gap:** `VoiceRecorder` produces AAC, not PCM for STT. The `PcmCapture` (for STT) has no dedicated UI integration in the chat screen.

### 4.3 Diagnostics Screen

**Status:** ✅ Exists but mesh-focused

- `DiagnosticsViewModel.kt` — shows mesh metrics, node info, transport health
- Missing: STT/TTS performance metrics, end-to-end latency, model info

**Fix:** Extend `DiagnosticsViewModel` to include STT/TTS metrics from `TtsMetricsCollector` and `SttBenchmark`.

---

## 5. Performance Metrics Analysis

### 5.1 What's Measured

| Metric | Where | Status |
|--------|-------|--------|
| STT init time | `SttBenchmark` | ✅ Measured |
| STT inference latency | `SttBenchmark` | ✅ Measured |
| STT RTF | `SttBenchmark` | ✅ Measured |
| STT non-empty rate | `SttBenchmark` | ✅ Measured (proxy for accuracy) |
| TTS TTFA | `TtsMetricsCollector` | ✅ Measured |
| TTS RTF | `TtsMetrics` | ✅ Measured |
| End-to-end pipeline | `VoicePipelineMetrics` | ✅ Timestamps t0-t7 |
| Mesh frames/bytes | `MeshMetrics` | ✅ Measured |
| Payload size | `VoiceMessageAdapter` | ✅ Measured |

### 5.2 What's NOT Measured

| Metric | Gap | Priority |
|--------|-----|----------|
| **WER (Word Error Rate)** | Requires reference transcripts + evaluation framework | P1 |
| **CPU usage** | Needs `/proc/stat` or `Debug.getThreadCpuTimeNanos()` | P1 |
| **RAM footprint** | Needs `Runtime.getRuntime().totalMemory()` or `Debug.getNativeHeapSize()` | P1 |
| **Model size on disk** | `SttModelInfo.sizeBytes` exists but not populated from assets | P1 |
| **APK size** | Can measure from build output | P2 |
| **Battery impact** | Needs `BatteryManager` integration | P2 |

### 5.3 Missing: Unified Performance Dashboard

**Current state:** Metrics are scattered across:
- `SttBenchmark` (offline only, no UI)
- `TtsMetricsCollector` (in TtsTestScreen only)
- `VoicePipelineMetrics` (in TtsTestScreen only)
- `MeshMetrics` (in DiagnosticsScreen)

**Fix:** Create a unified `PerformanceDashboardScreen` that aggregates all metrics in one view with real-time updates.

---

## 6. Offline Compliance Analysis

| Requirement | Status | Notes |
|-------------|--------|-------|
| STT works offline | ✅ | Sherpa-ONNX with bundled models |
| TTS works offline | ⚠️ | Android TTS may need data download for Indic languages |
| No cloud APIs | ✅ | No Google/Cloud/Azure/OpenAI calls detected |
| No internet dependency | ✅ | Core pipeline is fully local |
| Models bundled in APK | ✅ | STT models in assets (1.8GB via LFS) |

---

## 7. Open-Source Compliance Analysis

| Dependency | License | Status |
|------------|---------|--------|
| Sherpa-ONNX | Apache-2.0 | ✅ |
| Vosk | Apache-2.0 | ✅ |
| LiteRT (TFLite) | Apache-2.0 | ✅ |
| Kotlin/Coroutines | Apache-2.0 | ✅ |
| Jetpack Compose | Apache-2.0 | ✅ |
| Room | Apache-2.0 | ✅ |
| SQLCipher | BSD | ✅ |
| Coil | Apache-2.0 | ✅ |
| Koin | Apache-2.0 | ✅ |
| Tink | Apache-2.0 | ✅ |
| OkHttp | Apache-2.0 | ✅ |
| ZXing | Apache-2.0 | ✅ |

**All dependencies are open-source.** ✅

---

## 8. Hardware/Runtime Compliance

| Requirement | Status | Notes |
|-------------|--------|-------|
| Android app | ✅ | Kotlin + Jetpack Compose |
| Low-end device support | ⚠️ | Models are large (~189MB each); needs testing on 2GB RAM devices |
| Mid-range device support | ✅ | Should work on 4GB+ RAM devices |
| Min SDK 29 | ✅ | BLE L2CAP + Wi-Fi Aware NDP both require API 29 |
| Target SDK 36 | ✅ | Current |

---

## 9. Prioritized Fix List

### 🔴 P0 — Must Fix Before Demo (Competition Blockers)

1. **Add Odia STT model** — Download/obtain Odia Paraformer model, add to `assets/stt-or/`
2. **Fix TTS offline compliance** — Either bundle Indic TTS voice data or integrate Sherpa-ONNX TTS
3. **Fix alert audio behavior** — Use `STREAM_ALARM`, max volume, `AUDIOFOCUS_GAIN`, non-interruptible
4. **Add PTT walkie-talkie UI** — PTT button + state indicator in `ChatScreen`
5. **Verify Sherpa-ONNX Gradle dependency** — The Kotlin API classes (`OfflineRecognizer`, etc.) are bundled as source files in `com/k2fsa.sherpa.onnx` but there's no Gradle dependency declaration. Add `implementation("com.k2fsa.sherpa.onnx:...")` or document the manual bundling.

### 🟡 P1 — Must Fix for Strong Score

6. **Add CPU/RAM measurement** to diagnostics
7. **Add WER evaluation framework** with sample audio + reference transcripts
8. **Surface STT/TTS metrics in DiagnosticsScreen** (not just test screen)
9. **Add language selector to chat composer** (prominent, not buried in settings)
10. **Optimize Sherpa-ONNX streaming** — reuse stream across chunks instead of creating new ones
11. **Populate `SttModelInfo.sizeBytes`** from actual asset file sizes

### 🟢 P2 — Enhancements

12. Configurable VAD thresholds with UI
13. Adaptive noise calibration
14. Unified performance dashboard screen
15. Battery impact monitoring
16. APK size reporting in diagnostics
17. Conversation/phone mode UI (currently only backend exists)

---

## 10. File-by-File Change Manifest

### Files to CREATE

| File | Purpose |
|------|---------|
| `app/src/main/assets/stt-or/model.int8.onnx` | Odia STT model |
| `app/src/main/assets/stt-or/tokens.txt` | Odia STT tokens |
| `app/src/main/java/app/swarsetu/tts/backend/OfflineTtsEngine.kt` | Sherpa-ONNX or Piper TTS backend |
| `app/src/main/java/app/swarsetu/ui/chat/PttButton.kt` | PTT walkie-talkie button composable |
| `app/src/main/java/app/swarsetu/ui/chat/PttStateIndicator.kt` | Recording/processing/sending/speaking indicator |

### Files to MODIFY

| File | Change |
|------|--------|
| `app/src/main/java/app/swarsetu/tts/backend/AndroidTtsEngine.kt` | Add alert-mode audio handling (STREAM_ALARM, max volume, AUDIOFOCUS_GAIN) |
| `app/src/main/java/app/swarsetu/tts/audio/TtsAudioFocusManager.kt` | Support ALERT focus mode (AUDIOFOCUS_GAIN + STREAM_ALARM) |
| `app/src/main/java/app/swarsetu/tts/scheduler/TtsScheduler.kt` | Pass alert flag to engine for audio routing |
| `app/src/main/java/app/swarsetu/di/TtsModule.kt` | Wire new offline TTS engine |
| `app/src/main/java/app/swarsetu/ui/chat/ChatScreen.kt` | Add PTT button, language selector, state indicator |
| `app/src/main/java/app/swarsetu/ui/chat/ChatViewModel.kt` | Wire VoiceMessageAdapter, add PTT state |
| `app/src/main/java/app/swarsetu/ui/diagnostics/DiagnosticsViewModel.kt` | Add STT/TTS metrics |
| `app/src/main/java/app/swarsetu/ui/diagnostics/DiagnosticsScreen.kt` | Show STT/TTS metrics |
| `app/src/main/java/app/swarsetu/stt/SherpaOnnxEngine.kt` | Fix streaming to reuse stream object |
| `app/src/main/java/app/swarsetu/stt/SttModelInfo.kt` | Populate sizeBytes from asset files |
| `app/build.gradle.kts` | Verify/add Sherpa-ONNX dependency if needed |

---

## 11. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Odia model not available | Medium | High | Check k2-fsa model zoo; fallback to Vosk Odia model |
| Android TTS fails on low-end device | High | Critical | Must integrate offline TTS engine |
| Alert audio not loud enough on demo device | Medium | High | Test on target device; add volume boost code |
| 1.8GB APK too large for demo | Medium | Medium | Consider per-language APK or dynamic model download (offline) |
| Sherpa-ONNX .so incompatible with target device | Low | High | Test on actual competition devices |

---

## 12. Conclusion

The codebase is **~75% complete** against PS 26173 requirements. The mesh networking layer is exceptional (production-grade encryption, store-and-forward, dual-radio). The STT module is well-architected with 9/10 languages working. The main blockers are:

1. **Odia language gap** (1 missing model)
2. **TTS offline reliability** (Android TTS dependency)
3. **Alert audio behavior** (not meeting spec)
4. **PTT UI** (backend exists, frontend missing)

Fixing these 4 items will bring the solution to **~95% completeness** with a strong competitive position.
