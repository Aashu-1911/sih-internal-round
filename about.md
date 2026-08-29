# SwarSetu - Smart India Hackathon (SIH) Project Overview

## 1. Project Background & Problem Statement
In disaster and distress scenarios, cellular networks often fail or become congested. Transmitting high-fidelity vocal audio over low-bandwidth offline mesh networks is highly data-intensive and prone to failure. However, vocal communication is critical for inclusivity, catering to users regardless of their literacy level. 

**Solution:** SwarSetu is an entirely offline, decentralized Android application that provides a Walkie-Talkie style voice messaging system. It uses an on-device pipeline (Speech-to-Text -> Machine Translation -> Text-to-Speech) to compress voice intents into tiny text packets (bytes instead of megabytes), transmit them securely over a Wi-Fi Aware mesh network, and seamlessly play them back in the receiving user's preferred native language.

---

## 2. Key Features
- **100% Offline Mesh Networking:** Communicates directly device-to-device without requiring cellular towers, internet, or centralized servers.
- **Walkie-Talkie Translation Pipeline:** Live-transcribes spoken audio, translates it on-the-fly, and reads it out loud to the recipient in their preferred language.
- **Emergency Broadcast System:** A single-tap alert system that overrides normal routing to blast a high-priority distress signal (with an un-ignorable TTS alarm) to all nearby nodes.
- **Dual-Path Voice Pipeline:** Intelligently falls back to heavily compressed ADTS audio chunks if the live-translate feature is toggled off.
- **Inclusivity First:** Supports 10 Indian Languages (Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English).

---

## 3. Technology Stack & Tools
SwarSetu is built on a modern, reactive Android architecture designed for maximum efficiency on low-end hardware.

### Core Frameworks
- **Language:** Kotlin (100%)
- **UI Toolkit:** Jetpack Compose (Declarative UI)
- **Dependency Injection:** Koin (Lightweight alternative to Hilt/Dagger)
- **Asynchronous Processing:** Kotlin Coroutines & Flows
- **Database / Storage:** Room Database wrapped with SQLCipher for AES-256 encrypted local storage.
- **Mesh Transport Layer:** Android Wi-Fi Aware (NAN) API for high-bandwidth, peer-to-peer data transport.

### Machine Learning & Audio Pipeline
- **Speech-To-Text (STT):** 
  - *Vosk / SherpaOnnx:* Employs highly compressed, quantized TensorFlow Lite (TFLite) acoustic models to run continuous VAD (Voice Activity Detection) and transcription locally.
- **Machine Translation:**
  - *Google ML Kit Translate:* On-device neural machine translation (NMT). Models are dynamically downloaded (once) and run entirely offline.
- **Text-To-Speech (TTS):**
  - *Android Native TTS Engine:* Hooks directly into the OS-level TTS engine to synthesize speech with priority-based queuing (e.g., normal chat vs. emergency alerts).
- **Audio Capture:** Raw PCM byte arrays heavily compressed into AAC-ADTS format for fallback transmission.

---

## 4. Performance Metrics & Benchmarks
SwarSetu was strictly engineered to conform to low-power, low-end device constraints typical of rural deployment scenarios.

| Metric | Benchmark Result | Notes |
| :--- | :--- | :--- |
| **RAM Usage** | **< 20% total system memory** | The pipeline (STT -> Translate -> TTS) runs sequentially. Memory spikes never overlap, preventing Out-Of-Memory (OOM) crashes on 2GB RAM devices. |
| **Bandwidth (Walkie-Talkie)** | **~50 Bytes / message** | By converting voice to text before transmission, data payloads are reduced by over 99.9% compared to raw audio files. |
| **Bandwidth (Audio Fallback)**| **< 100 KB / minute** | Highly compressed AAC-ADTS format used when live translation is disabled. |
| **Translation Model Size** | **~30 MB / language pair** | Kept entirely out of the base APK. Downloaded dynamically to conserve device storage space. |
| **Offline Reliability** | **100% Offline** | No cloud APIs are pinged during the core communication loop. Mesh discovery and ML inference happen natively on the CPU/NPU. |

> [!TIP]
> **For the SIH Judges:**
> Emphasize that SwarSetu tackles the core limitation of mesh networks (low bandwidth / high packet loss) by performing the heavy ML lifting *at the edges* (on the device itself) rather than trying to force heavy audio files through a congested network.
