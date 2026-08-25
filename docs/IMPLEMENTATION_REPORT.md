# PS 26173 — Implementation Report

**Date:** August 26, 2026
**Branch:** stt-tts-ui-integration

---

## IMPLEMENTED

### 1. Live Translate → Mesh Send Wiring (P0 Fix)
**Files:** `ChatViewModel.kt`, `ChatScreen.kt`
**Problem:** The "STT" toggle in ChatScreen set `voiceController.isMeshEnabled = true` but `VoiceMessageAdapter.startVoiceMessage()` was never called, so `currentContext` was null and STT results were never sent over mesh.
**Fix:**
- `toggleLiveTranslate()` now calls `voiceMessageAdapter.startVoiceMessage()` with proper routing context (recipientId, GroupInfo) when enabling
- `toggleLiveTranslate()` calls `voiceMessageAdapter.stopVoiceMessage()` when disabling
- ChatScreen's `LaunchedEffect(sttLatestResult)` skips populating the input field when Live Translate is ON (results are sent over mesh instead)

### 2. Alert Audio Behavior (P0 Fix)
**Files:** `TtsAudioFocusManager.kt`, `TtsManager.kt`, `AndroidTtsEngine.kt`
**Problem:** Alert messages used `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` on `USAGE_ASSISTANCE_ACCESSIBILITY` — other apps could duck the alert audio. Not at highest volume. Not on alarm stream.
**Fix:**
- `TtsAudioFocusManager` now supports two modes: Normal (existing behavior) and Alert (new)
- Alert mode uses `AUDIOFOCUS_GAIN` on `STREAM_ALARM` with `USAGE_ALARM` — other apps cannot duck this audio
- Alert mode sets `STREAM_ALARM` volume to maximum before playback
- `TtsManager.speak()` passes `isAlert` flag to `audioFocusManager.requestFocus()`
- `AndroidTtsEngine.speak()` uses `QUEUE_FLUSH` for alerts (immediate preemption), `QUEUE_ADD` for normal speech

### 3. STT/TTS Metrics in DiagnosticsScreen (P1 Fix)
**Files:** `DiagnosticsViewModel.kt`, `DiagnosticsScreen.kt`, `UiModule.kt`
**Problem:** STT/TTS metrics were only visible in the test screen (TtsTestScreen), not in the production DiagnosticsScreen.
**Fix:**
- `DiagnosticsViewModel` now accepts optional `SttPipeline` and `TtsMetricsCollector` via Koin `getOrNull()`
- Exposes `sttState`, `sttPartialText`, and `ttsMetrics` as StateFlows
- `DiagnosticsScreen` displays live STT state, partial text, and TTS metrics (language, TTFA, RTF, status)

### 4. Test Compatibility Fixes
**Files:** `FakeMeshController.kt`, `ChatViewModelTest.kt`, `DiagnosticsScreenContentTest.kt`, `ProfileScreenContentTest.kt`
**Problem:** Pre-existing test compilation errors due to missing parameters in test constructors.
**Fix:** Added missing `messageId` parameter to FakeMeshController, `voiceController`/`sttPipeline` to ChatViewModelTest, `sttState`/`sttPartialText`/`ttsMetrics` to DiagnosticsScreenContentTest.

---

## FIXED

| Issue | Before | After |
|-------|--------|-------|
| Live Translate toggle | Sets flag but nothing happens | STT results sent over mesh |
| Alert audio | Duckable, default volume | Alarm stream, max volume, non-duckable |
| Diagnostics metrics | Only mesh metrics shown | STT/TTS metrics also shown |
| Test compilation | 5+ compilation errors | All compile clean |

---

## NEW

- Alert audio routing (STREAM_ALARM + AUDIOFOCUS_GAIN)
- Alert volume boost (max volume on STREAM_ALARM)
- STT pipeline state display in DiagnosticsScreen
- TTS metrics display (TTFA, RTF, language, status) in DiagnosticsScreen

---

## REMOVED

- `SPEAKING_TIMEOUT_MS` constant (reverted premature state fix — deferred to future TTS completion callback integration)

---

## REMAINING

### P0 (Blocks Competition Demo)
1. **Odia STT model missing** — `stt-or/` directory has no model files. Need Odia Paraformer model from k2-fsa.
2. **TTS not fully offline** — `AndroidTtsEngine` uses `android.speech.tts.TextToSpeech` which may require downloading Indic language data. Need offline TTS engine (Sherpa-ONNX TTS or Piper).
3. **No PTT walkie-talkie button in ChatScreen** — Backend exists but no dedicated PTT UI with state indicator.

### P1 (Score Impact)
4. **VoiceConversationController SPEAKING indicator** — Transitions to IDLE immediately after `speak()` instead of waiting for TTS completion. Needs TtsMetricsCollector completion callback.
5. **Sherpa-ONNX has no Gradle dependency** — Kotlin API classes are manually bundled as source. Works but fragile.
6. **No WER measurement** — STT accuracy is measured by non-empty rate, not word error rate.
7. **Streaming creates new stream per chunk** — SherpaOnnxEngine creates new OfflineStream per 500ms chunk instead of reusing one (loses inter-chunk context).

### P2 (Polish)
8. VAD thresholds not user-configurable
9. No unified performance dashboard
10. No battery/CPU/RAM measurement in-app

---

## FILES CHANGED

| File | Change |
|------|--------|
| `app/src/main/java/app/swarsetu/ui/chat/ChatViewModel.kt` | Fixed toggleLiveTranslate() to wire VoiceMessageAdapter |
| `app/src/main/java/app/swarsetu/ui/chat/ChatScreen.kt` | Skip input field population when Live Translate ON |
| `app/src/main/java/app/swarsetu/tts/audio/TtsAudioFocusManager.kt` | Added alert audio mode (STREAM_ALARM, max volume) |
| `app/src/main/java/app/swarsetu/tts/TtsManager.kt` | Pass isAlert to audioFocusManager |
| `app/src/main/java/app/swarsetu/tts/backend/AndroidTtsEngine.kt` | Alert uses QUEUE_FLUSH, normal uses QUEUE_ADD |
| `app/src/main/java/app/swarsetu/ui/diagnostics/DiagnosticsViewModel.kt` | Added STT/TTS state flows |
| `app/src/main/java/app/swarsetu/ui/diagnostics/DiagnosticsScreen.kt` | Added STT/TTS metrics section |
| `app/src/main/java/app/swarsetu/di/UiModule.kt` | Pass STT/TTS deps to DiagnosticsViewModel |
| `app/src/main/java/app/swarsetu/voice/VoiceConversationController.kt` | Added TODO for TTS completion callback |
| `app/src/test/java/app/swarsetu/mesh/FakeMeshController.kt` | Added messageId parameter |
| `app/src/test/java/app/swarsetu/ui/chat/ChatViewModelTest.kt` | Added voiceController/sttPipeline mocks |
| `app/src/test/java/app/swarsetu/ui/diagnostics/DiagnosticsScreenContentTest.kt` | Added STT/TTS parameters |
| `app/src/test/java/app/swarsetu/ui/profile/ProfileScreenContentTest.kt` | Added sttLanguageCode field |

---

## BUILD STATUS

- **compileDebugKotlin:** ✅ PASS
- **testDebugUnitTest:** 1513 pass, 1 fail (pre-existing KoinGraphTest — native .so not loadable in JVM)

---

## PS 26173 COVERAGE

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Offline STT (9/10 languages) | ✅ | Sherpa-ONNX with bundled models |
| Offline STT (Odia) | 🔴 | Model files missing |
| Offline TTS | ⚠️ | Android TTS may need downloads |
| 10 language support | 🟡 | 9 working, 1 broken (Odia) |
| VAD / pause detection | ✅ | VoiceActivityDetector + SttPipeline |
| Sentence formation | ✅ | Accumulate → finalize on silence |
| Mesh communication (BLE + Wi-Fi) | ✅ | CompositeMeshTransport |
| Push-to-talk backend | ✅ | VoiceMessageAdapter wired |
| Push-to-talk UI | 🔴 | No production PTT button |
| Live Translate → mesh | ✅ | Fixed in this PR |
| Alert/emergency audio | ✅ | Fixed: STREAM_ALARM, max volume, non-duckable |
| Alert preemption | ✅ | TtsScheduler handles ALERT priority |
| RTF measurement | ✅ | SttBenchmark + TtsMetrics |
| TTFA measurement | ✅ | TtsMetricsCollector |
| E2E latency timestamps | ✅ | VoicePipelineMetrics t0-t7 |
| Performance in Diagnostics | ✅ | Fixed: STT/TTS metrics now shown |
| E2E encryption | ✅ | Ratchet protocol + MessageCrypto |
| Store-and-forward | ✅ | ForwardSync |
| Content moderation | ✅ | TFLite models bundled |
| Open-source dependencies | ✅ | All Apache-2.0/BSD |
| Low-end device support | ⚠️ | Needs runtime testing |
| Fully offline (no internet) | ⚠️ | TTS may fail offline for Indic languages |

---

## FINAL RISK

| Risk | Severity | Status |
|------|----------|--------|
| Odia STT non-functional | P0 | Needs model file |
| TTS may fail offline for Indic | P0 | Needs offline TTS engine |
| No PTT UI in production | P0 | Backend wired, UI missing |
| Sherpa-ONNX source bundled | P1 | Works but fragile |
| SPEAKING indicator premature | P1 | Needs TTS completion callback |
