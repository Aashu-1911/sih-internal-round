# SwarSetu Project Status & Phase 1 Answers

This file contains answers to the technical integration and architecture questions for Phase 1 development of the **SwarSetu** offline transceiver.

---

### 1. Android Project Basics

* **Language:** Kotlin (`2.4.10`) / Java
* **UI:** Jetpack Compose (Material 3)
* **Min SDK:** 29 (shared floor for BLE L2CAP CoC and Wi-Fi Aware NDP)
* **Target SDK:** 36
* **Build System:** Gradle Kotlin DSL (`build.gradle.kts` files)
* **AGP Version:** `9.3.1`
* **Kotlin Version:** `2.4.10`
* **SDK Compilation Configurations:**
  ```kotlin
  compileSdk = 37
  minSdk = 29
  targetSdk = 36
  ```

---

### 2. Current Project Structure

The complete directory tree of the project has been generated and saved to [`project-tree.txt`](file:///c:/Users/ashis/OneDrive/Desktop/Hackathon/SIH-2026/iTantra/project-tree.txt).

#### Code Packages Summary:
Under `app/src/main/java/app/swarsetu/`, the package layout is:
* `crash/` — Crash logs and reporting
* `data/` — Room DB, repository layers, and attachments
* `demo/` — Seed data generation
* `di/` — Koin dependency injection modules
* `identity/` — Peer nodes and crypto keys
* `mesh/` — CompositeMeshTransport, BLE (`mesh/bluetooth`), Wi-Fi Aware (`mesh/wifiaware`), Spool Relays (`mesh/spool`), and Foreground service (`MeshService.kt`)
* `moderation/` — On-device TFLite content moderation
* `notifications/` — Mesh notification dispatchers
* `review/` — Review dialog mechanisms
* `ui/` — Chat list, settings, and conversation view components
* `ui/voice/` — Audio recorder and player for recorded voice messages

*Note: There are currently **no** `tts/` or `audio/` root-level packages (except for recorded voice-note helpers in `ui/voice/`).*

---

### 3. What exactly is currently working?

* `[x] App launches` — Builds, compiles, and launches cleanly.
* `[x] Knit mesh works` — Yes, peer discovery and routing layer are fully active.
* `[x] BLE works` — Yes, advertising/connection is working.
* `[x] Wi-Fi Aware works` — Yes, NAN discovery and channel socket connections work.
* `[x] Messaging works` — Messaging UI (Nearby, direct messages, groups) works.
* `[x] Background service works` — `MeshService` runs as a foreground service.
* `[x] Microphone permission` — Requested during onboarding/voice message creation.
* `[x] Audio recording` — Fully functional for custom voice notes via `VoiceRecorder.kt`.
* `[ ] STT` — Not implemented yet.
* `[ ] TTS` — Not implemented yet.

*Note: Knit has been renamed and refactored to `app.swarsetu` package namespace, but the internal mesh protocols and connection interfaces remain unmodified.*

---

### 4. Current TTS Code

There is **no** existing TTS/Speech Synthesis code (`TextToSpeech`, `android.speech.tts`) in the codebase.
The audio playing logic is limited to playing recorded voice notes using `MediaPlayer` in:
* `app/src/main/java/app/swarsetu/ui/voice/VoicePlayer.kt`
* `app/src/main/java/app/swarsetu/data/ByteArrayMediaSource.kt` (custom `MediaDataSource` for memory streams)
* `app/src/main/java/app/swarsetu/data/VoiceAudio.kt` (normalization and volume peaks)

---

### 5. What should Phase 1 actually include?

Phase 1 will implement the following pipeline standalone:
```text
Text → TtsManager → Android TextToSpeech → Speaker
```

#### Included Features:
* All 10 language capability detection and manual language selection
* Standard initialization, playback synthesis, and interruption/stopping
* Android audio focus management (duck/pause and phone call handling)
* Basic message queueing (Normal messages queued; Alert messages immediately interrupt)
* On-device synthesis metrics tracking (Initialization latency, Time-to-First-Audio, duration, RTF)
* Standalone internal developer test screen (no STT or mesh dependencies needed yet)

---

### 6. The 10 Languages

* **Exact Language Codes:** Hindi (HI), Gujarati (GU), Marathi (MR), Kannada (KN), Malayalam (ML), Tamil (TA), Telugu (TE), Odia (OR), Bengali (BN), English (EN).
* **Selection:** Manual language selection in Phase 1 (strongly recommended to avoid early complexity).

---

### 7. Low-RAM Target

* **Minimum target:** 4 GB - 6 GB RAM. We design the model/engine initialization and lifecycle aggressively to ensure resources are released immediately when not in use.

---

### 8. Java/Kotlin Preference

* Kotlin only.

---

### 9. Audio Behavior

* **Normal message:** Queue and play in order of receipt.
* **New normal message while playing:** Queue it.
* **Alert message:** Immediately interrupt normal speech, play the alert (high priority).
* **Alert while another alert is playing:** Replace current alert with the new one (if newer) or queue.
* **Phone music/video playing:** Pause or duck depending on Android system policy.
* **Incoming phone call:** Immediately stop TTS playback and release audio focus.

---

### 10. Voice Notes Concept

* **Option C:** Implement both direct speaker playback and PCM/audio file generation. Exposing generated audio allows latency benchmarks (e.g. tracking synthesis vs. playback) and provides a clean integration boundary for future network transit.

---

### 11. Metrics

* **Yes,** include the metrics display panel (initialization time, Time-to-First-Audio (TTFA), playback latency, duration, and Real-Time Factor (RTF)).

---

### 12. Testing

* **Yes,** build a standalone developer dashboard screen within the settings/debug options to verify language detection, voice installation status, synthesis, and latency performance.

---

### 13. Offline Requirement

* **Option C:** Only use voices already installed on the device. Detect missing offline voice data and prompt the user with system settings links to download missing voice packages offline.

---

### 14. Current Branch/Repo State

```bash
$ git status
On branch main
Untracked files:
	project-tree.txt

nothing added to commit but untracked files present

$ git branch --show-current
main

$ git log -5 --oneline
5b1e8c2 style: fix ktlint violations
8dcecfb fix: make gradlew executable
00e3763 Base line code added
7565287 Fix formatting of the problem statement title
eb46548 Changed Title
```

---

### 15. Executing Coding Agent

* **Gemini CLI / Antigravity Agent**
