# Problem Statement Title	
iTantra -Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links
=======
# SwarSetu

**SwarSetu** is an offline, serverless multilingual speech transceiver app for Android, designed to satisfy **ISRO Problem Statement 26173 (iTantra - Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links)**.

By integrating local, offline neural network models for Speech-to-Text (STT) and Text-to-Speech (TTS) with a decentralized dual-radio mesh networking layer (derived from the `Knit` transport), SwarSetu enables low-latency, low-bitrate, off-grid voice communication across nearby devices without internet, cellular service, or centralized infrastructure.

---

## 🚀 Core Pipeline Architecture

SwarSetu optimizes transmission bandwidth by transferring speech as highly compressed text tokens rather than raw audio streams:

```text
Phone A (Voice)
    ↓
[Microphone & Audio Capture]
    ↓
[Voice Activity Detection (VAD) & Pause Detection]
    ↓
[Local Offline Speech-to-Text (STT)]
    ↓
[Sentence Formation & Compression]
    ↓
[Low-Bitrate Text Message (CBOR/Knit Protocol)]
    ↓
[Mesh Transport (Wi-Fi Aware + Bluetooth LE)]
    ↓  (hop-by-hop relay)
[Mesh Transport (Wi-Fi Aware + Bluetooth LE)]
    ↓
Phone B (Text Received)
    ↓
[Local Offline Text-to-Speech (TTS)]
    ↓
[Synthesized Speech Playback]
    ↓
Speaker (Voice Outputs)
```

---

## ✨ Key Capabilities

1. **Multilingual Offline STT & TTS** — Local inference engines supporting 10 target languages:
   * Hindi (HI), Gujarati (GU), Marathi (MR), Kannada (KN), Malayalam (ML), Tamil (TA), Telugu (TE), Odia (OR), Bengali (BN), and English (EN).
2. **Dual-Radio Serverless Mesh** — Automatically discovers peers and relays encrypted messages hop-by-hop over **Wi-Fi Aware (NAN)** and **Bluetooth LE** simultaneously behind a single transport layer.
3. **Push-to-Talk (PTT) Walkie-Talkie Mode** — Simple press-to-hold recording with local pause detection and sentence boundary finalization before broadcasting.
4. **Conversation/Phone Mode** — Interactive duplex-style voice communication triggered by continuous pause detection.
5. **Emergency Announcements** — High-priority broadcast category that overrides standard audio states to deliver urgent alerts.
6. **Low-Bitrate & High-Performance** — Engineered to run on mid/low-range Android hardware with strict memory, CPU, and battery constraints.
7. **Performance Benchmarking** — Integrated dashboard measuring:
   * Word Error Rate (WER) and Real-Time Factor (RTF) for local STT/TTS.
   * End-to-end latency (Speech Input to Speech Output).
   * Storage and RAM footprints of the on-device models.

---

## 🛠️ Technology Stack

* **Min SDK:** Android 10 (API 29)
* **Target SDK:** Android 35
* **Core Language:** Kotlin + Coroutines Flow
* **UI Framework:** Jetpack Compose (Material 3)
* **Dependency Injection:** Koin DI
* **Local Persistence:** Room Database + SQLCipher (under-the-hood schema preserved)
* **Neural Models:** On-device TensorFlow Lite / ONNX-based execution

---

## 📥 Development and Build Setup

### Prerequisites
* JDK 21
* Android SDK (API 29+) configured in `local.properties`

### Command Line Building
To compile and assemble the debug variant:
```bash
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat assembleDebug
```

### Running Unit Tests
To execute all local unit tests (100% pass target):
```bash
./gradlew.bat :app:testDebugUnitTest
```

---

## 🔒 Security & Privacy

* **End-to-End Encryption** — DM and group message payloads are fully encrypted using an X3DH-style bootstrap and dynamic ratchet key rotation.
* **Sealed Receipts & Reactions** — Meta-actions are hidden and encrypted to prevent trace leakages over the mesh network.
* **At-Rest Encryption** — Local storage database is fully encrypted via SQLCipher using secure hardware-backed keychains.

---

## 📄 License and Attributions

SwarSetu is built on top of the open-source Knit project and maintains all upstream licenses, copyrights, and notices. Upstream codebases and libraries are appropriately attributed under their respective licenses.
>>>>>>> 4f7bef8 (first commit)
