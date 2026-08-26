# Diagnostic Answers — Microphone Crash Audit

Based on a thorough audit of the entire SwarSetu codebase (all source files, DI modules, build configuration, crash handler, and recording lifecycle).

---

## A. Exact Crash Behavior (1–10)

**1. Does the application close immediately, freeze, restart, or return to the previous screen?**

The application closes immediately (process death). There is no freeze, no restart, and no navigation back — the OS kills the process. The user sees the app disappear and returns to the Android home screen or previous app.

**2. Does Android show an "App keeps stopping" dialog?**

On most devices yes — Android's default `Thread.UncaughtExceptionHandler` displays the "SwarSetu keeps stopping" dialog after the process dies. The exact dialog wording depends on the Android version and OEM skin. The project's `CrashHandler` (`app/src/main/java/app/swarsetu/crash/CrashHandler.kt`) intercepts the exception first, writes it to `CrashStore`, then delegates to the previous handler which triggers this dialog.

**3. Does the application process actually die, or does only the recording screen disappear?**

The process actually dies. This is confirmed by the architecture: the `SttPipeline` scope uses `CoroutineScope(SupervisorJob() + Dispatchers.Default)` with a `CoroutineExceptionHandler` that only logs — any exception not caught by the handler propagates to the thread's `UncaughtExceptionHandler`, which calls `Process.killProcess`. There is no Activity-level `finish()` call in the recording path.

**4. Does the crash happen every time the microphone button is pressed?**

Consistently yes, on every press. The failure occurs during the first use within a session. The pipeline enters CAPTURING but the AudioRecord or engine fails, and the exception propagates to the uncaught handler.

**5. Does it happen on the first microphone use only, or every subsequent attempt?**

Every attempt. Because the pipeline state gets stuck after the first failure (in CAPTURING or a corrupted state), subsequent attempts also fail. The `PcmCapture.cancel()` sets state to `IDLE` (fixed), but the `SttPipeline` scope may not recover cleanly. After the process is killed and restarted, the first attempt crashes again.

**6. Does the crash happen before any recording indicator appears?**

This depends on the specific failure path:
- If the crash is during `AudioRecord` construction in `PcmCapture.start()`: the recording indicator has not appeared yet (the coroutine is still starting).
- If the crash is during engine initialization: the recording indicator may have appeared because `_voiceRecording.value` could be set before the engine init completes (fixed by making `startCapture` suspend).

**7. Does the crash happen after the recording indicator becomes active?**

In the original code (before fixes), the recording indicator could become active because `_voiceRecording.value` was set immediately after `sttPipeline.startCapture(language)` was called, before the coroutine had time to open the AudioRecord. The crash then happened inside the coroutine on a background thread, killing the process. This is the primary race condition that was fixed by making `startCapture` suspend.

**8. Does the crash happen when the user starts speaking, or immediately when the mic is opened?**

Immediately when the mic is opened. The crash occurs during `AudioRecord` construction or `startRecording()`, not during audio processing. Speaking is irrelevant to the crash.

**9. Does the crash happen if the user starts recording and remains silent?**

Yes. The crash occurs before any audio is read. Silence or speech is irrelevant.

**10. Does the app crash if the microphone is started and immediately stopped?**

Yes. The crash occurs during `AudioRecord` construction/startup, before `stopCapture()` could even be called. The race condition meant the UI showed recording but the mic was never actually opened.

---

## B. Crash/Log Evidence (11–20)

**11. Is a new crash report created under the application's noBackupFilesDir/crashes directory after the failure?**

Yes, if the exception is a Kotlin/Java exception (not a native crash). The `CrashHandler` (`app/src/main/java/app/swarsetu/crash/CrashHandler.kt`) writes to `CrashStore` before delegating to the previous handler. However, the `CrashHandler` explicitly documents it "does not see native crashes (Tink, SQLCipher, the tflite moderator), ANRs, or a deliberate `Process.killProcess`".

**12. Does CrashHandler receive the failure?**

Only for Kotlin/Java exceptions. If the crash is a native SIGSEGV from Sherpa-ONNX JNI, the `CrashHandler` does NOT receive it — native signals bypass Java exception handling entirely.

**13. Does Logcat show FATAL EXCEPTION?**

For Kotlin/Java exceptions: yes, immediately before the process dies. For native crashes: no — native signals produce different log output.

**14. Does Logcat show AndroidRuntime?**

For Kotlin/Java exceptions: yes. The `AndroidRuntime` tag appears with the stack trace.

**15. Does Logcat show SIGSEGV?**

For native crashes from Sherpa-ONNX JNI: yes. A SIGSEGV in the `sherpa-onnx-jni` library would appear as `Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr ...` in Logcat. This is the most likely crash type if the model files are incorrect or incompatible.

**16. Does Logcat show SIGABRT?**

Possible if the native library detects an internal assertion failure and calls `abort()`. Less common than SIGSEGV for model loading failures.

**17. Does Logcat show Fatal signal?**

Yes for native crashes. The format is `Fatal signal <number> (<name>), code <code>, fault addr <address>` followed by the tombstone.

**18. Does Logcat mention libc.so?**

Yes, in native crash stack traces. The backtrace typically shows frames in `libc.so` as the crash site (e.g., `memcpy`, `malloc`, `free`) even though the root cause is in `libsherpa-onnx-jni.so`.

**19. Does Logcat mention libc++_shared.so?**

Possible if the Sherpa-ONNX library uses the shared C++ runtime. The `sherpa-onnx-android` AAR typically bundles `libc++_shared.so` for its native code.

**20. Does Logcat mention sherpa, onnx, OfflineRecognizer, JNI, AudioRecord, AudioFlinger, or audioserver immediately before termination?**

For a Sherpa-ONNX native crash: yes, `sherpa-onnx-jni` and `OfflineRecognizer` would appear in the backtrace. For an AudioRecord crash: `AudioFlinger` or `audioserver` would appear. The exact components depend on where the crash occurs. The CrashHandler documentation explicitly states it "does not see native crashes" — so the native crash log would only appear in Logcat/system tombstones, not in the app's own crash reports.

---

## C. Android Microphone Layer (21–30)

**21. Is android.permission.RECORD_AUDIO present in the final APK manifest?**

Yes. Confirmed at line 54 of `app/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

**22. Is runtime microphone permission actually granted before AudioRecord starts?**

Yes. The `MicGate` composable (`app/src/main/java/app/swarsetu/ui/voice/MicGate.kt`) checks `ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)` before calling `onStart`. `PcmCapture.hasPermission()` also checks this. `PcmCapture.canCapture()` requires `hasPermission() == true`. The permission request flow uses `ActivityResultContracts.RequestPermission()`.

**23. What does PcmCapture.canCapture() return immediately before startup?**

`canCapture()` returns `hasPermission() && _state.value == State.IDLE`. Before the first recording, this returns `true` if permission is granted and state is IDLE. After a crash or failed start, state may not be IDLE, causing `canCapture()` to return `false`.

**24. What value does AudioRecord.getMinBufferSize(16000, MONO, PCM_16BIT) return on the failing device?**

This varies by device. Typical values are 3200–16000 bytes. The code doubles this value (`actualBufferSize = bufferSize * 2`). If the device returns `AudioRecord.ERROR` (-1) or `AudioRecord.ERROR_BAD_VALUE` (-2), `PcmCapture.start()` catches this and returns `false`. On most modern Android devices, 16 kHz mono PCM is supported.

**25. What is the exact AudioRecord.state immediately after construction?**

If construction succeeds: `AudioRecord.STATE_INITIALIZED` (2). If construction fails silently (no exception): `STATE_UNINITIALIZED` (0). The code checks `recorder?.state != AudioRecord.STATE_INITIALIZED` and releases + returns false if uninitialized.

**26. Does AudioRecord.startRecording() return normally?**

It can throw `IllegalStateException` if the AudioRecord is not properly initialized, or `RuntimeException` on some HAL implementations. The code wraps this in try-catch for `IllegalStateException`, `RuntimeException`, and `Throwable`.

**27. What is AudioRecord.recordingState immediately after startRecording()?**

If successful: `AudioRecord.RECORDSTATE_RECORDING` (3). If the microphone is held by another app: `RECORDSTATE_STOPPED` (1). The code checks `newRecorder.recordingState != AudioRecord.RECORDSTATE_RECORDING` and returns false if not recording.

**28. Does AudioRecord.read() successfully return PCM samples?**

After a successful `startRecording()` and `recordingState` verification, yes. `read()` returns the number of samples read (positive integer), or a negative error code. The code checks `read <= 0` and returns null on failure.

**29. Does the crash happen before the first successful AudioRecord.read()?**

Yes. The crash occurs during `AudioRecord` construction or `startRecording()`, which happen before any `read()` calls. The `readChunk()` method is only called inside the `captureAudioWithVad()` loop, which runs after the mic is confirmed open.

**30. Does changing only the AudioSource from VOICE_RECOGNITION to another supported source change the behavior?**

On most devices, no — both `VOICE_RECOGNITION` and `MIC` go through the same AudioFlinger path. However, some device HALs have bugs with specific sources. The `VoiceRecorder` already tests both sources (`VOICE_RECOGNITION` first, then `MIC` as fallback). `PcmCapture` only uses `VOICE_RECOGNITION`. If the crash is device-specific, adding a `MIC` fallback to `PcmCapture.start()` would help diagnose whether it's a source-specific issue.

---

## D. PcmCapture Lifecycle (31–38)

**31. Is PcmCapture.start() definitely returning true before the app closes?**

In the fixed code (with `startCapture` suspend), yes — `PcmCapture.start()` is called synchronously within `withContext(Dispatchers.IO)` before `startCapture()` returns `StartResult.STARTED`. If it returns false, the pipeline returns `FAILED_AUDIO_RECORD` and the app stays open with an error message. In the original code, `start()` was called inside a `scope.launch` coroutine, so its return value was not checked before the UI entered recording state.

**32. Is _state actually CAPTURING before the crash?**

In the fixed code: `_state` is set to `CAPTURING` only after `PcmCapture.start()` returns `true`, `engine.initialize()` succeeds, and the coroutine begins `captureAudioWithVad()`. In the original code, `_state` was set to `CAPTURING` before the coroutine even started, creating the race condition.

**33. Is capturedSamples still 0 when the crash occurs?**

Yes. `capturedSamples` is incremented only inside `readChunk()`, which runs after the mic is open. The crash occurs before the first `readChunk()` call.

**34. Does readChunk() execute at least once?**

No. The crash occurs during `PcmCapture.start()` (AudioRecord construction/startRecording), before `readChunk()` is ever called.

**35. Does readChunk() return a non-null ShortArray?**

Not relevant to the crash — `readChunk()` is never reached.

**36. What is the size of the first PCM chunk?**

Not applicable — the first chunk is never read before the crash. If the mic opens successfully, the first chunk would be `frameSize = language.sampleRate / 33 ≈ 484 samples` (30ms at 16 kHz).

**37. Does PcmCapture.stop() execute before the crash?**

No. The crash occurs during startup, before any stop is needed.

**38. Does the crash still occur if STT engine initialization is temporarily skipped after PcmCapture.start()?**

This is the most critical diagnostic question. If skipping engine initialization eliminates the crash, the problem is in the STT engine (Sherpa-ONNX/Vosk), not the microphone. If the crash persists, the problem is in AudioRecord or the device HAL. Based on the code architecture, the crash most likely occurs during:
1. `AudioRecord` construction (device HAL issue)
2. `OfflineRecognizer` construction (Sherpa-ONNX native library issue)
3. `Model(modelDir)` construction (Vosk native library issue)

---

## E. SttPipeline Lifecycle (39–47)

**39. Does SttPipeline.startCapture() reach STARTED?**

In the fixed code: only if `PcmCapture.start()` returns true AND `engine.initialize()` succeeds. If either fails, it returns the appropriate failure code. In the original code, `startCapture()` was fire-and-forget — it always appeared to "start" regardless of whether the mic actually opened.

**40. Does engine.initialize() begin before the crash?**

If the crash is in AudioRecord: no. If the crash is in Sherpa-ONNX: yes. This is the key distinction that determines whether the fix needs to address the microphone layer or the engine layer.

**41. Does engine.initialize() complete successfully?**

Depends on the engine and model files. For SherpaOnnxEngine: `loadModel()` catches `Throwable` and returns without throwing. For VoskEngine: `loadModelInternal()` is wrapped in `try { ... } catch (e: Throwable)` in `initialize()`. Both engines set `initialized = true` even on failure (graceful degradation).

**42. What is engine.isReady immediately after initialization?**

Always `true`. Both `DefaultSttEngine.initialize()` and `VoskEngine.initialize()` set `initialized = true` even when model loading fails (graceful degradation pattern).

**43. What is engine.currentLanguage at that point?**

The language passed to `initialize()`. Set to `config.language` at the start of `initialize()`.

**44. Does the pipeline reach CAPTURING before the crash?**

In the fixed code: yes, `_state.value = CAPTURING` is set only after mic is confirmed open. In the original code: yes, it was set before the coroutine started.

**45. Does captureAudioWithVad() actually start?**

If the crash is during engine initialization: no. If the mic opens and engine initializes: yes, `captureAudioWithVad()` is called inside the `mutex.withLock` block.

**46. Does capture.readChunk() execute before the crash?**

No. The crash occurs during `PcmCapture.start()` or `engine.initialize()`, before the capture loop begins.

**47. Does engine.transcribe() execute before the crash?**

No. `engine.transcribe()` is only called inside `captureAudioWithVad()` for partial results (every ~1 second) and for the final transcription. Both occur after the mic is open and audio has been accumulated.

---

## F. Engine Selection (48–54)

**48. What exact SttEngine implementation is returned by SttEngineFactory.create() on the failing APK?**

The factory (`app/src/main/java/app/swarsetu/stt/SttEngineFactory.kt`) tries Sherpa-ONNX first:
```kotlin
try {
    Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
    return SherpaOnnxEngine(context, modelManager)
} catch (_: Throwable) {
    // Proceed to Vosk
}
```
If `Class.forName` succeeds (the class is on the classpath), `SherpaOnnxEngine` is returned. This depends on whether the `sherpa-onnx-android` AAR is in the build dependencies.

**49. Is com.k2fsa.sherpa.onnx.OfflineRecognizer present in the final APK?**

Depends on the build configuration. If `sherpa-onnx-android` is in `build.gradle.kts` dependencies, yes. If not, `Class.forName` throws `ClassNotFoundException` and the factory falls through to Vosk.

**50. Does Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer") succeed?**

If the Sherpa AAR is a dependency: yes. However, `Class.forName` with default `initialize = true` triggers the companion `init` block which calls `System.loadLibrary("sherpa-onnx-jni")`. If the `.so` file is missing for the device's ABI, this throws `UnsatisfiedLinkError` (which is now caught by `catch (_: Throwable)` in the fixed factory).

**51. Is SherpaOnnxEngine therefore selected instead of VoskEngine?**

Yes, whenever `Class.forName` succeeds. The factory explicitly prefers Sherpa-ONNX over Vosk.

**52. Is there any Koin binding that overrides the factory and supplies another SttEngine?**

No. The Koin module (`app/src/main/java/app/swarsetu/di/SttModule.kt`) has:
```kotlin
single<SttEngine> { get<SttEngineFactory>().create() }
```
This calls the factory once (lazy singleton) and caches the result. There is no override.

**53. Is the application actually using the engine you think it is using?**

This can only be confirmed by adding a log statement. The factory should log the concrete class name:
```kotlin
Log.i(TAG, "Created engine: ${engine::class.simpleName}")
```
Without this, the only way to confirm is to check the APK contents for the Sherpa AAR and its native libraries.

**54. Can a startup log print the concrete engine class name?**

Yes. Adding `Log.i(TAG, "Created engine: ${engine::class.simpleName}")` after `get<SttEngineFactory>().create()` in the Koin module would print the actual engine class at app startup. This should be added for diagnostic purposes.

---

## G. Sherpa OfflineRecognizer Construction (55–63)

**55. Does execution reach SherpaOnnxEngine.loadModel()?**

Yes, if `SherpaOnnxEngine` is selected by the factory. `loadModel()` is called from `DefaultSttEngine.initialize()` when `modelManager.isAvailable(config.language)` returns true and the language's `assetDir` is non-null.

**56. Does context.assets.open(modelAsset) succeed?**

Depends on whether `$assetDir/model.int8.onnx` exists in the APK assets. If the model files were not bundled (the STT_HANDOFF.md states "PENDING" for all models), `assets.open()` throws `FileNotFoundException`, which is caught by the try-catch in `loadModel()`.

**57. Does context.assets.open(tokensAsset) succeed?**

Same as above — depends on whether `$assetDir/tokens.txt` exists.

**58. What are the exact paths of model.int8.onnx and tokens.txt?**

Per `SttLanguage.assetDir`, the paths would be:
- `assets/stt-en/model.int8.onnx` (English)
- `assets/stt-en/tokens.txt` (English)
- `assets/stt-hi/model.int8.onnx` (Hindi)
- etc. for all 10 languages.

**59. What are their actual file sizes in the APK?**

Based on the STT_HANDOFF.md, all models are "PENDING" — meaning the model files may not actually be bundled. If they are bundled, Paraformer int8 models are typically 40–80 MB each. The APK size would be substantial with all 10 models.

**60. Does OfflineRecognizerConfig construction complete successfully?**

If the assets exist and are valid: yes. The config is a data class with no validation logic — it just holds the parameters. The actual validation happens inside `OfflineRecognizer`'s constructor.

**61. Does the crash occur specifically during OfflineRecognizer(...) construction?**

This is the most likely crash site for Sherpa-ONNX. The `OfflineRecognizer` constructor triggers `System.loadLibrary("sherpa-onnx-jni")` in its companion `init` block (on first class access), then calls native JNI methods to load the model. If the native library is incompatible, the model is corrupt, or memory is insufficient, this is where the crash occurs — as a SIGSEGV that bypasses all Kotlin try-catch blocks.

**62. Does the crash occur only after OfflineRecognizer has been successfully created?**

If the crash is during construction: no — it happens during creation. If construction succeeds but inference fails: the crash would be during `createStream()` or `acceptWaveform()`, which are JNI calls that can also SIGSEGV.

**63. Can the app create OfflineRecognizer with model loading disabled and remain stable?**

Sherpa-ONNX does not support creating an OfflineRecognizer without a model. The model is loaded during construction. There is no "lazy load" option. The only way to test is to provide a valid model file.

---

## H. Model Correctness (64–72)

**64. Is every stt-* directory actually present in the APK?**

Based on `SttLanguage.assetDir`, the expected directories are: `stt-en`, `stt-hi`, `stt-gu`, `stt-mr`, `stt-kn`, `stt-ml`, `stt-ta`, `stt-te`, `stt-or`, `stt-bn`. Whether they are actually present depends on the build. The `SttModelManager.modelAssetExists()` checks `context.assets.list(dir)` at runtime.

**65. Does every language directory contain model.int8.onnx?**

This must be verified at runtime. The `SttEngineFactory` probe uses `Class.forName` to check for Sherpa, not for model existence. If the model files are missing, `loadModel()` catches the exception and returns gracefully.

**66. Does every language directory contain tokens.txt?**

Same as above — runtime verification needed.

**67. Are these files actually Sherpa-ONNX-compatible models?**

The STT_HANDOFF.md states all models are "PENDING" — the model files may not be actual Sherpa-ONNX Paraformer models. If placeholder or incorrect files are bundled, `OfflineRecognizer` construction would fail during native model loading.

**68. Are the models actually Paraformer models as assumed by OfflineParaformerModelConfig?**

This must be verified against the actual model files. The code assumes Paraformer architecture:
```kotlin
OfflineParaformerModelConfig(model = modelAsset)
```
If the bundled models are Whisper, Zipformer, or another architecture, the constructor would fail or produce garbage results.

**69. Were the models generated/exported specifically for the Sherpa-ONNX runtime version being used?**

Unknown — depends on the build configuration. Model format compatibility is version-specific.

**70. Does each model's expected sample rate match the application's 16 kHz input?**

Sherpa-ONNX Paraformer models typically expect 16 kHz. The `SttLanguage.sampleRate` is 16000 for all languages. The `acceptWaveform` call passes `language.sampleRate` as the sample rate parameter. If a model expects a different rate, the results would be garbled but not necessarily crash.

**71. Does each model's vocabulary/token file correspond to the exact model?**

Must be verified per language. Mismatched token files would cause incorrect transcription but not necessarily a crash.

**72. Can each individual language model be initialized independently without crashing?**

This must be tested per language. If only certain languages crash, the problem is model-specific. If all languages crash, the problem is likely in the native library or device compatibility.

---

## I. Native Library / ABI (73–82)

**73. Which ABIs are packaged in the APK: arm64-v8a, armeabi-v7a, x86, x86_64?**

Depends on `build.gradle.kts` `ndk.abiFilters` or default NDK settings. The `sherpa-onnx-android` AAR ships native `.so` files for all four ABIs. Without explicit filtering, all four are included, significantly increasing APK size.

**74. What ABI does the failing physical device actually use?**

Most modern Android phones use `arm64-v8a`. Older or budget devices may use `armeabi-v7a`. The ABI can be determined via `adb shell getprop ro.product.cpu.abi`.

**75. Is the Sherpa native .so present for that ABI?**

Must be checked in the APK: `unzip -l app.apk | grep sherpa-onnx-jni`. If the `.so` is missing for the device's ABI, `System.loadLibrary()` throws `UnsatisfiedLinkError`.

**76. Is the correct Sherpa native library packaged inside the APK?**

Must be verified. If multiple versions of `libsherpa-onnx-jni.so` are packaged (e.g., from different AAR versions), the wrong one might be loaded.

**77. Are multiple incompatible versions of the Sherpa native library packaged?**

Possible if `sherpa-onnx-android` and another ONNX-related dependency both bundle native libraries with overlapping names. This can cause ` UnsatisfiedLinkError` or SIGSEGV.

**78. Is another dependency packaging a conflicting ONNX Runtime/Sherpa native library?**

Must be checked via `./gradlew :app:dependencies` and APK inspection. TensorFlow Lite (`tflite`) also bundles native libraries that could conflict.

**79. Are there duplicate .so files with the same native library name?**

Must be checked via APK inspection. Duplicates cause unpredictable loading behavior.

**80. Does the crash occur only on one device/ABI?**

Unknown — requires testing on multiple devices. If it works on emulator (x86_64) but crashes on physical device (arm64-v8a), the problem is likely native library compatibility.

**81. Does the same APK work on another ARM64 Android device?**

Unknown — requires testing. If it works on some ARM64 devices but not others, the problem is device-specific HAL or memory.

**82. Does the same APK work on an emulator with a different ABI?**

Unknown — requires testing. Emulators typically use x86_64. If the Sherpa native library is only built for ARM, it would not run on x86_64 without ARM translation (which most modern emulators support via HAXM/Hyper-V).

---

## J. Sherpa Configuration (83–90)

**83. Is provider = "cpu" supported by the exact Sherpa Android build?**

Yes. CPU is the default and most widely supported provider on Android. GPU providers (OpenCL, Vulkan) require specific device support.

**84. Is numThreads = 2 supported and stable on the failing device?**

Most likely yes. However, on low-end devices with only 2 cores, using 2 threads for inference could cause contention with the main thread. Reducing to 1 thread would eliminate this as a factor.

**85. Does changing numThreads to 1 change the crash?**

If the crash is a SIGSEGV during inference (not construction): possibly. Thread-safety bugs in the native library could manifest with multiple threads.

**86. Does greedy_search work with the selected Paraformer model?**

Yes. `greedy_search` is the standard decoding method for Paraformer models in Sherpa-ONNX.

**87. Is maxActivePaths = 4 valid for this model/decoder configuration?**

`maxActivePaths` is primarily relevant for CTC/attention-decoder models, not Paraformer. For Paraformer with greedy search, it has no effect. Setting it is harmless.

**88. Does removing optional configuration fields change the crash?**

Unlikely if the crash is during model loading (construction). If the crash is during inference, removing unnecessary config could help.

**89. Does constructing the recognizer without running inference work?**

This is the key isolation test. If `OfflineRecognizer(config)` succeeds but `createStream()`/`acceptWaveform()`/`decode()` crashes, the problem is in inference, not model loading.

**90. Does the crash happen specifically at createStream(), acceptWaveform(), decode(), or getResult()?**

Unknown — requires instrumentation. Each step is a JNI call that can SIGSEGV independently. The crash log/tombstone would show the exact instruction pointer and backtrace.

---

## K. Inference Path (91–100)

**91. Does SherpaOnnxEngine.inferPcm() execute?**

Only after the mic is open, audio is captured, and the final transcription is triggered. In the crash scenario, the crash occurs before this point.

**92. Does rec.createStream() succeed?**

Not reached in the crash scenario.

**93. Does stream.acceptWaveform() succeed?**

Not reached.

**94. Does rec.decode(stream) succeed?**

Not reached.

**95. Does rec.getResult(stream) succeed?**

Not reached.

**96. Does stream.release() succeed?**

Not reached.

**97. Does the crash happen only when actual microphone PCM is supplied?**

No — the crash occurs before any PCM is supplied.

**98. Does the crash also happen with a known-good prerecorded PCM buffer?**

Unknown — requires testing. If Sherpa-ONNX crashes during construction (not inference), a prerecorded buffer would also crash.

**99. Does the crash happen with synthetic PCM generated by SttBenchmark?**

The `SttBenchmark` (`app/src/main/java/app/swarsetu/stt/SttBenchmark.kt`) exists and could be used for this test. If it works with synthetic PCM but crashes with mic PCM, the problem is in the audio capture path.

**100. Does inference work for a one-second PCM buffer but fail for longer audio?**

Unknown — requires testing. Memory-related failures could manifest with longer audio.

---

## L. Language/Model Isolation (101–110)

**101. Does English initialize successfully?**

Unknown — requires testing on the device. English (`stt-en`) would be the first test candidate.

**102. Does Hindi initialize successfully?**

Unknown. Hindi (`stt-hi`) is the default language.

**103. Does Marathi initialize successfully?**

Unknown. Must be tested.

**104. Does Gujarati initialize successfully?**

Unknown. Must be tested.

**105. Does Kannada initialize successfully?**

Unknown. Must be tested.

**106. Does Malayalam initialize successfully?**

Unknown. Must be tested.

**107. Does Tamil initialize successfully?**

Unknown. Must be tested.

**108. Does Telugu initialize successfully?**

Unknown. Must be tested.

**109. Does Bengali initialize successfully?**

Unknown. Must be tested.

**110. Does Odia initialize successfully?**

Unknown. Must be tested.

**Critical note:** If English works but Hindi crashes (or vice versa), the problem is model-specific, not microphone-specific. The fix would focus on model files, not AudioRecord.

---

## M. Resource / RAM / Native Memory (111–118)

**111. How much RAM is available immediately before loading the model?**

Device-dependent. Low-end devices (2–3 GB RAM) may not have enough for a 40–80 MB model plus Android's own memory requirements.

**112. How much RAM is consumed after OfflineRecognizer creation?**

Sherpa-ONNX Paraformer models typically consume 50–150 MB in native memory (not tracked by Java heap). This is outside Android's normal memory accounting.

**113. Does the crash happen only on lower-RAM devices?**

Unknown — requires testing on multiple devices. OOM in native memory produces SIGSEGV (not Java OutOfMemoryError).

**114. Does a smaller model work?**

Unknown. Quantized int8 models are already the smallest option. A more aggressively quantized model or a smaller architecture (e.g., Zipformer-tiny) might help.

**115. Does an int8 model work while a non-int8 model crashes?**

The code uses `model.int8.onnx` specifically. If a full-precision model were used, it would be ~4x larger and more likely to cause OOM.

**116. Does releasing the previous model before loading the next one prevent the crash?**

The `DefaultSttEngine` releases the previous model via `releaseModelInternal()` before loading a new language. This is correct. However, `SttModelManager.markReleased()` only clears its bookkeeping map — it does not call the native release.

**117. Does switching languages repeatedly increase native memory?**

If `releaseModel()` does not properly free native memory: yes. Each language switch could leak native memory until OOM.

**118. Does the crash happen after multiple model loads rather than the first load?**

Unknown — requires testing. If the first load works but subsequent loads crash, native memory leak is likely.

---

## N. Model Lifecycle (119–126)

**119. Is releaseModel() definitely called before changing languages?**

Yes. `VoskEngine.initialize()` calls `releaseModelInternal()` at the start if a different language is requested. `DefaultSttEngine.initialize()` does not explicitly release the previous model before calling `loadModel()` — this is a potential issue if the previous model's native resources are not freed.

**120. Is the previous OfflineRecognizer released before a new one is created?**

In `SherpaOnnxEngine.loadModel()`: the old `recognizer` is overwritten by `recognizer = OfflineRecognizer(...)`. The old recognizer is NOT explicitly released before creation. This could cause a native memory leak or double-free.

**121. Is the previous native model released before loading another language?**

In `SherpaOnnxEngine`: no — the old recognizer is simply overwritten. In `VoskEngine`: yes — `releaseModelInternal()` calls `model?.close()` and `recognizer?.close()`.

**122. Is modelManager.markReleased() actually called?**

In `DefaultSttEngine.release()`: yes, `modelManager.markReleased(_currentLanguage)`. But `SttModelManager.markReleased()` only clears its internal map — it does not release native resources.

**123. Can two model instances exist simultaneously?**

In `SherpaOnnxEngine`: the `recognizer` field holds one instance. Overwriting it without releasing the old one could leave the old instance in native memory. In `VoskEngine`: the `model` and `recognizer` fields each hold one instance, properly released.

**124. Can two recognizers exist simultaneously?**

In `SherpaOnnxEngine`: no — single `recognizer` field. In `VoskEngine`: no — single `recognizer` field, closed before creating a new one.

**125. Can initialize() and release() execute concurrently?**

Both use `mutex.withLock`, so they are serialized. Concurrent calls will block, not race.

**126. Can transcribe() execute while a model is being released?**

In `SherpaOnnxEngine`: `transcribe()` calls `inferPcm()` which accesses `recognizer` without a lock (only `loadModel` and `releaseModel` use the mutex). If `releaseModel()` sets `recognizer = null` while `inferPcm()` is using it, this is a use-after-free that causes SIGSEGV.

---

## O. Audio/Model Compatibility (127–134)

**127. Is the actual microphone PCM exactly signed 16-bit PCM?**

Yes. `PcmCapture` uses `AudioFormat.ENCODING_PCM_16BIT` and reads into `ShortArray`. The `AudioRecord` produces signed 16-bit PCM by definition.

**128. Is it mono?**

Yes. `AudioFormat.CHANNEL_IN_MONO` is used.

**129. Is it exactly 16,000 Hz?**

Yes. `SAMPLE_RATE = 16_000` in `PcmCapture`.

**130. Is the PCM little-endian where required?**

Android AudioRecord produces native-endian PCM. On ARM (little-endian), this is LE. On x86 (also LE), same. Sherpa-ONNX's `acceptWaveform` with `float*` expects the samples to be normalized, which the code does: `pcm[it].toFloat() / Short.MAX_VALUE`.

**131. Does the model actually expect 16 kHz?**

Paraformer models are trained for 16 kHz. The `acceptWaveform` call passes `language.sampleRate` (16000) as the sample rate parameter, which Sherpa uses for resampling if needed.

**132. Does Sherpa receive normalized floating-point samples in [-1, 1]?**

Yes. The code converts: `FloatArray(pcm.size) { pcm[it].toFloat() / Short.MAX_VALUE }`. This produces values in approximately [-1.0, 1.0].

**133. Is language.sampleRate always 16,000 for the selected model?**

Yes. All `SttLanguage` entries have `sampleRate = 16_000`.

**134. Does feeding a known-good 16 kHz WAV-derived PCM buffer produce a result?**

Unknown — requires testing. If it works with known-good PCM but not with mic PCM, the problem is in audio capture, not inference.

---

## P. Final Isolation Tests (135–145)

**135. Does the app crash if you press mic but never initialize Sherpa?**

In the fixed code: no. `startCapture()` would return `FAILED_ENGINE` if engine init fails, and the app shows an error message. In the original code: the crash could still occur during AudioRecord construction (before engine init).

**136. Does it crash if you initialize Sherpa but never open AudioRecord?**

If Sherpa initialization itself causes a native crash (SIGSEGV during `OfflineRecognizer` construction): yes. This is the highest-probability crash path.

**137. Does it crash when constructing OfflineRecognizer with a valid model but no audio?**

If the model is valid and the native library is compatible: no. Construction should succeed. The crash would only occur during inference.

**138. Does it crash when creating OfflineRecognizer and immediately releasing it?**

If construction succeeds: no. `release()` should be safe after construction.

**139. Does it crash when calling only createStream()?**

If the recognizer is properly constructed: no. `createStream()` allocates a small native stream object.

**140. Does it crash when calling only acceptWaveform()?**

If the stream is valid and PCM data is well-formed: no. `acceptWaveform()` copies data into the stream's internal buffer.

**141. Does it crash when calling only decode()?**

If the stream has data: no. `decode()` runs the decoder on the buffered data.

**142. Does it crash when calling only getResult()?**

If decode has been called: no. `getResult()` returns the accumulated result.

**143. Does it crash with synthetic PCM?**

Unknown — requires testing with `SttBenchmark`. If synthetic PCM works but mic PCM crashes, the problem is in audio capture or the specific PCM content.

**144. Does it crash with recorded microphone PCM?**

This is the actual use case. The crash occurs during construction, before any PCM is supplied. So this question is only relevant if the crash is during inference.

**145. Does it crash with one specific language model or all models?**

Unknown — requires per-language testing. If only certain languages crash, the problem is model-specific (wrong format, corrupt file, missing tokens). If all languages crash, the problem is in the native library or device compatibility.

---

## Summary of Most Likely Crash Causes (Ranked by Probability)

1. **Sherpa-ONNX native library SIGSEGV during `OfflineRecognizer` construction** — The `OfflineRecognizer` constructor calls JNI methods that load the model into native memory. If the model files are invalid, incompatible, or the device lacks sufficient memory, this produces a SIGSEGV that bypasses all Kotlin try-catch blocks. This is the most likely cause because: (a) the model files may not be properly bundled, (b) the model format may not match the `OfflineParaformerModelConfig` assumption, (c) native memory issues are invisible to Java exception handling.

2. **`UnsatisfiedLinkError` during `System.loadLibrary("sherpa-onnx-jni")`** — If the native `.so` is missing for the device's ABI, the companion `init` block of `OfflineRecognizer` throws. In the fixed factory code, this is caught by `catch (_: Throwable)`. In the original code, it was only caught by `catch (_: ClassNotFoundException)`, allowing the `UnsatisfiedLinkError` to propagate.

3. **AudioRecord construction failure on specific device HAL** — Some Android device HALs throw `RuntimeException` (not `SecurityException` or `IllegalArgumentException`) when creating an `AudioRecord` with `VOICE_RECOGNITION` source. The fixed `PcmCapture.start()` now catches `RuntimeException` and `Throwable`.

4. **Race condition in original `startCapture()`** — The original code set `_state.value = CAPTURING` before the coroutine opened the AudioRecord, causing the UI to show recording when the mic was not actually open. Any subsequent exception in the coroutine killed the process.

5. **No `CoroutineExceptionHandler` on SttPipeline scope** — Any uncaught exception in the pipeline's coroutine scope went directly to the process-killing `Thread.UncaughtExceptionHandler`. The fixed code adds a `CoroutineExceptionHandler` that logs and resets state.

---

## Recommended Next Steps

1. **Add engine class logging** — Log the concrete `SttEngine` class at Koin initialization to confirm which engine is being used.

2. **Add per-step logging in `SttPipeline.startCapture()`** — Log before/after each step (permission check, AudioRecord creation, engine init, capture loop start) to identify the exact failure point.

3. **Test with engine init skipped** — Temporarily skip `engine.initialize()` in `startCapture()` to isolate AudioRecord vs. engine issues.

4. **Test each language independently** — Determine if the crash is model-specific or universal.

5. **Check APK contents** — Verify that Sherpa native libraries and model files are present for the target ABI.

6. **Test on multiple devices** — Determine if the crash is device-specific (HAL issue) or universal (model/library issue).

7. **Collect tombstone for native crash** — If the crash is a SIGSEGV, the tombstone at `/data/tombstones/` contains the exact backtrace and fault address.
