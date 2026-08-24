# SWARSE TU — TEAM DEVELOPMENT INSTRUCTIONS

> **Project:** Swarsetu
> **Problem Statement:** 26173
> **Title:** iTantra - Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links
> **Platform:** Android
> **Architecture:** Knit-based communication + Offline STT/TTS
> **Development Model:** 4-member GitHub team

---

# 1. PROJECT OBJECTIVE

Swarsetu is being developed to satisfy ISRO Problem Statement 26173.

The primary system must enable:

```text
Phone A
    ↓
Microphone
    ↓
Offline STT
    ↓
Pause Detection
    ↓
Sentence Formation
    ↓
Low-Bitrate Text Message
    ↓
Knit Transport
    ↓
Wi-Fi / Bluetooth
    ↓
Phone B
    ↓
Text Reception
    ↓
Offline TTS
    ↓
Speaker
```

The system must operate fully offline for the core STT/TTS communication pipeline.

The primary objective is NOT to build a generic messenger.

The primary objective is:

> **Low-latency, low-bitrate, offline multilingual voice communication using local STT and TTS.**

---

# 2. PROBLEM STATEMENT REQUIREMENTS

The implementation must remain aligned with PS 26173.

Required languages:

1. Hindi
2. Gujarati
3. Marathi
4. Kannada
5. Malayalam
6. Tamil
7. Telugu
8. Odia
9. Bengali
10. English

Required capabilities:

* Android application
* Offline STT
* Offline TTS
* Low-power operation
* Low/mid-range Android device support
* Wi-Fi/Bluetooth communication
* Pause/stoppage detection
* Sentence formation
* Low-bitrate communication
* Low latency
* RTF measurement
* Push-to-talk walkie-talkie mode
* Phone-like mode when PTT is disabled
* Voice-note style TTS playback
* Emergency/alert announcements
* Local execution without cloud STT/TTS APIs

---

# 3. PRIMARY ENGINEERING PRIORITIES

Always prioritize work in this order:

## P0 — Core

1. Offline STT
2. Offline TTS
3. 10-language support
4. Voice Activity Detection
5. Pause detection
6. Sentence formation
7. Low-bitrate message protocol
8. Knit integration
9. Push-to-talk mode
10. End-to-end STT → Network → TTS
11. Latency measurement
12. Accuracy measurement

## P1 — Required for strong final solution

1. Conversation/phone mode
2. Emergency alert mode
3. Model management
4. Low-RAM optimization
5. CPU optimization
6. Battery optimization
7. Network reconnect/retry
8. Packet-loss handling
9. Device compatibility testing
10. Performance dashboard

## P2 — Enhancements

1. Offline translation
2. Automatic language detection
3. Advanced noise robustness
4. Adaptive model selection
5. Advanced mesh capabilities
6. Additional UI features

---

# 4. DO NOT CHANGE THE CORE OBJECTIVE

Do NOT turn Swarsetu into:

* a generic chat application
* a social network
* a cloud communication application
* an Internet-dependent translator
* a normal voice-calling application
* a raw-audio streaming application

The core architecture must remain:

```text
Speech
 ↓
STT
 ↓
Text
 ↓
Low-Bitrate Transmission
 ↓
Text
 ↓
TTS
 ↓
Speech
```

Do NOT transmit raw voice/audio unless there is a clearly justified future feature.

---

# 5. KNIT IS THE NETWORKING BASE

Knit is being used as the communication foundation.

Use Knit for functionality that it already provides, such as:

* peer discovery
* BLE communication
* Wi-Fi Aware communication
* transport
* message delivery
* routing
* connection handling
* relevant security mechanisms
* store-and-forward where applicable

Do NOT unnecessarily rewrite Knit functionality.

However:

> Swarsetu application logic must NOT become tightly coupled to Knit internals.

Create an abstraction:

```text
SwarsetuTransport
        ↓
KnitTransport
        ↓
Knit
```

The rest of the application should communicate through `SwarsetuTransport`.

This keeps the networking implementation replaceable.

---

# 6. LICENSING RULE

Knit and its dependencies may have their own licenses.

Never:

* delete third-party licenses
* modify third-party copyright notices
* remove required attribution
* claim third-party code as original Swarsetu code
* replace third-party licenses without legal authorization

Before copying or substantially modifying third-party code:

1. Identify its license.
2. Check compatibility.
3. Preserve required notices.
4. Record the dependency/source.
5. Update third-party documentation if required.

Never solve a licensing problem by simply deleting `LICENSE` files.

---

# 7. SOURCE CODE OWNERSHIP

Each major component must have a clear owner.

## Member 1 — STT

Owns:

```text
stt/
```

Responsibilities:

* offline STT
* STT models
* 10 languages
* partial transcription
* final transcription
* STT model management
* STT benchmarks
* WER
* STT latency
* STT RAM/CPU

---

## Member 2 — TTS / Audio

Owns:

```text
tts/
audio/
```

Responsibilities:

* offline TTS
* 10 languages
* TTS model management
* audio playback
* TTS latency
* RTF
* audio focus
* speaker handling
* emergency audio behavior

---

## Member 3 — Networking

Owns:

```text
network/
protocol/
```

Responsibilities:

* Knit integration
* `SwarsetuTransport`
* message serialization
* message IDs
* sequence numbers
* ACK
* retry
* ordering
* connection state
* packet validation
* low-bitrate protocol

---

## Member 4 — Voice / PTT / Benchmarking

Owns:

```text
voice/
benchmark/
```

Responsibilities:

* microphone control
* VAD
* pause detection
* utterance management
* sentence finalization
* PTT
* conversation control
* end-to-end latency
* integration testing
* performance testing

---

# 8. DO NOT MODIFY OTHER MEMBERS' MODULES WITHOUT DISCUSSION

Before changing another member's module:

1. Contact the owner.
2. Explain why the change is required.
3. Agree on the interface/change.
4. Make the change in a separate branch if necessary.

Avoid silently modifying another person's implementation.

---

# 9. SHARED INTERFACES MUST REMAIN STABLE

The team must agree on stable interfaces.

## STT

Conceptually:

```text
Audio → SttResult
```

Example:

```text
SttEngine
 ├── initialize(language)
 ├── start()
 ├── process(audio)
 ├── stop()
 └── release()
```

---

## TTS

Conceptually:

```text
Text → Audio
```

Example:

```text
TtsEngine
 ├── initialize(language)
 ├── synthesize(text)
 ├── play(audio)
 └── release()
```

---

## Transport

Conceptually:

```text
SwarsetuMessage → Transport
Transport → SwarsetuMessage
```

Example:

```text
SwarsetuTransport
 ├── connect()
 ├── disconnect()
 ├── send(message)
 ├── observeMessages()
 └── connectionState()
```

---

## Voice

Conceptually:

```text
Microphone → Utterance
```

The voice layer must not directly depend on Knit.

---

# 10. MESSAGE FORMAT

The application-level message must remain compact.

Minimum fields:

```text
messageId
senderId
timestamp
language
type
priority
sequenceNumber
payload
```

Initial message types:

```text
SENTENCE
ACK
CONTROL
ALERT
PTT_START
PTT_END
```

Do not add unnecessary metadata.

The project is specifically intended for low-bitrate communication.

---

# 11. DO NOT SEND RAW AUDIO IN THE CORE PIPELINE

The primary communication method is:

```text
Audio
 ↓
STT
 ↓
Text
 ↓
Network
 ↓
Text
 ↓
TTS
 ↓
Audio
```

Do not replace this with:

```text
Audio
 ↓
Network
 ↓
Audio
```

unless a separate requirement explicitly calls for it.

---

# 12. OFFLINE REQUIREMENT

Core STT and TTS must work without Internet access.

Do NOT introduce:

* Google Speech API
* Google Translate API
* OpenAI API
* cloud STT
* cloud TTS
* cloud translation
* remote inference
* mandatory Firebase communication

Internet must not be required for the primary communication loop.

---

# 13. MODEL MANAGEMENT

Never load all language models into RAM unnecessarily.

Preferred approach:

```text
Language selected
       ↓
Load required STT/TTS models
       ↓
Use
       ↓
Cache or unload
```

Optimize for low/mid-range Android devices.

Always measure:

* model size
* APK size
* RAM
* CPU
* startup time
* inference time
* battery usage

---

# 14. LANGUAGE SUPPORT

The application must eventually support:

```text
HI — Hindi
GU — Gujarati
MR — Marathi
KN — Kannada
ML — Malayalam
TA — Tamil
TE — Telugu
OR — Odia
BN — Bengali
EN — English
```

Do not use inconsistent language codes across modules.

Create one shared language enum/configuration.

---

# 15. LANGUAGE SELECTION

For P0:

Use manual language selection.

Example:

```text
Select Language

Hindi
Gujarati
Marathi
Kannada
Malayalam
Tamil
Telugu
Odia
Bengali
English
```

Automatic language detection is P2.

Do not delay P0 waiting for automatic detection.

---

# 16. VOICE ACTIVITY AND PAUSE DETECTION

The voice pipeline must support:

```text
Idle
 ↓
Speech detected
 ↓
Recording
 ↓
Pause detected
 ↓
Finalize utterance
 ↓
STT
```

Avoid fixed-length recording wherever possible.

The pause threshold must be configurable and benchmarked.

Do not assume a particular pause duration is universally optimal.

---

# 17. SENTENCE FORMATION

STT partial results must not automatically become network messages.

Example:

```text
Partial:
"मैं"

Partial:
"मैं कल"

Partial:
"मैं कल स्टेशन"

Final:
"मैं कल स्टेशन जा रहा हूँ।"
```

Transmit the final sentence unless streaming is explicitly required.

---

# 18. PUSH-TO-TALK

PTT is a P0 feature.

Expected behavior:

```text
Press/Hold
 ↓
Start microphone
 ↓
STT
 ↓
Pause detection
 ↓
Finalize sentence
 ↓
Transmit
```

The interface must clearly show:

* recording
* processing
* sending
* connected/disconnected
* received
* speaking

---

# 19. CONVERSATION MODE

Conversation/phone-like mode is P1.

Expected behavior:

```text
A speaks
 ↓
Pause
 ↓
STT
 ↓
Transmit
 ↓
B TTS
 ↓
B responds
```

Do not implement this before the PTT pipeline is stable.

---

# 20. EMERGENCY ALERT MODE

Emergency alerts must have a high-priority message type.

Example:

```text
type = ALERT
priority = CRITICAL
```

Receiver behavior:

```text
Receive alert
 ↓
Prioritize
 ↓
Appropriate audio behavior
 ↓
TTS
```

Do not assume the application can bypass Android's system audio/security restrictions.

Test on real devices.

---

# 21. PERFORMANCE IS A FIRST-CLASS FEATURE

The project is evaluated on performance.

Every developer must consider:

* latency
* RAM
* CPU
* storage
* battery
* model size

Do not accept an implementation merely because it works.

It must also be measurable.

---

# 22. REQUIRED BENCHMARKS

## STT

Measure:

```text
WER
Model size
RAM
CPU
Inference latency
RTF
```

## TTS

Measure:

```text
Model size
RAM
CPU
First-audio latency
RTF
```

## Network

Measure:

```text
Payload size
Transmission latency
Packet loss
Retry time
```

## End-to-end

Measure:

```text
Speech start
 ↓
STT complete
 ↓
Message sent
 ↓
Message received
 ↓
TTS starts
 ↓
First audio
```

---

# 23. DO NOT INVENT BENCHMARK RESULTS

Never put fake numbers into:

* README
* presentations
* reports
* dashboards
* source comments
* documentation

If a benchmark has not been measured, write:

```text
TBD
```

or:

```text
Not measured yet
```

Only publish actual measurements.

---

# 24. TEST ON REAL DEVICES

The final solution must not be validated only on an emulator.

Test:

* low-end Android
* mid-range Android
* different Android versions
* different microphones
* different speakers
* Bluetooth
* Wi-Fi
* poor connectivity
* disconnect/reconnect

Maintain a device test table.

---

# 25. GITHUB BRANCHING RULES

Never develop directly on `main`.

Use:

```text
main
│
├── feature/stt
├── feature/tts
├── feature/network
└── feature/voice
```

For later work:

```text
feature/stt-optimization
feature/tts-alert
feature/network-reliability
feature/conversation-mode
```

---

# 26. PULL REQUEST RULE

Every feature must go through a Pull Request.

A PR must explain:

```text
What changed?
Why was it changed?
How was it tested?
Does it affect another module?
```

Do not merge untested code into `main`.

---

# 27. COMMIT RULES

Use clear commit messages.

Preferred format:

```text
feat(stt): add Marathi offline inference
feat(tts): add Hindi TTS
feat(network): add sentence message protocol
feat(voice): add pause detection
fix(stt): resolve model loading failure
fix(network): handle reconnect
perf(stt): reduce model memory usage
test(tts): add synthesis benchmark
docs: update architecture
```

Avoid:

```text
update
changes
final
final2
working
test
abc
```

---

# 28. DO NOT COMMIT GENERATED FILES

Do not commit:

* build directories
* APKs
* temporary files
* IDE caches
* generated intermediates
* local machine configuration
* secrets
* keystores
* passwords

Unless explicitly required by the project.

---

# 29. LARGE MODEL FILES

Large ML models must be handled deliberately.

Before committing a model:

1. Check its size.
2. Check Git LFS requirements.
3. Confirm repository policy.
4. Confirm license.
5. Confirm the model can be redistributed.
6. Confirm GitHub Actions can obtain it.
7. Verify a fresh clone can build the application.

Never push a broken/missing LFS pointer.

---

# 30. NO SECRETS IN GITHUB

Never commit:

```text
API keys
passwords
private keys
keystores
Firebase secrets
signing credentials
tokens
```

Use GitHub Actions Secrets for CI/CD credentials.

Remember:

> The core application must remain offline even if no secrets are available.

---

# 31. BUILD REQUIREMENT

Before creating a PR:

```text
./gradlew assembleDebug
```

must succeed unless the PR is specifically documentation-only or otherwise exempt.

Run relevant tests:

```text
./gradlew test
```

and other project-specific checks as appropriate.

Do not claim that something works without testing it.

---

# 32. INTEGRATION ORDER

P0 integration must happen in this order:

```text
STT works
 ↓
TTS works
 ↓
Knit text communication works
 ↓
VAD works
 ↓
Sentence formation works
 ↓
PTT works
 ↓
STT + Knit + TTS works
 ↓
10 languages
 ↓
Performance optimization
```

Do not attempt the complete system on day one.

---

# 33. DEFINITION OF DONE

A feature is NOT complete merely because code was written.

A feature is complete only when:

* code compiles
* relevant tests pass
* feature was manually tested where required
* performance was measured where relevant
* documentation was updated
* no unrelated functionality was broken
* PR has been reviewed
* integration works with the other modules

---

# 34. AI CODING AGENT RULES

Any AI coding agent working on this repository MUST:

1. Read this file before making changes.
2. Understand the existing architecture before modifying it.
3. Never rewrite large portions of the project without justification.
4. Never remove working functionality unnecessarily.
5. Never change another module's public interface without explaining the impact.
6. Never remove licenses/copyright notices belonging to third parties.
7. Never introduce cloud APIs into the offline core.
8. Never commit secrets.
9. Never fabricate benchmark results.
10. Never modify unrelated files unnecessarily.
11. Run the relevant build/tests after changes.
12. Report exactly what was changed.
13. Report files modified.
14. Report tests/build commands executed.
15. Report any remaining problems.
16. Preserve backward compatibility wherever practical.
17. Prefer small, reviewable changes over huge rewrites.
18. Do not silently change the architecture.
19. Ask for clarification before making a destructive architectural decision.
20. Treat this document as the project's persistent engineering rules.

---

# 35. BEFORE EVERY CODE CHANGE

The developer/AI should answer internally:

```text
1. Which module does this belong to?
2. Who owns that module?
3. Does an existing interface already exist?
4. Can this be implemented without changing other modules?
5. Does this introduce a new dependency?
6. Is the dependency open-source and compatible?
7. Does this affect offline operation?
8. Does this increase APK/RAM/CPU usage?
9. How will this be tested?
```

---

# 36. WHEN MODIFYING EXISTING CODE

Always:

```text
Inspect
 ↓
Understand
 ↓
Plan
 ↓
Modify
 ↓
Build
 ↓
Test
 ↓
Review
```

Never:

```text
Search/replace
 ↓
Hope it works
```

---

# 37. NO BLIND GLOBAL REPLACEMENTS

Do not perform blind replacements such as:

```text
replace every "Uktam" with "Swarsetu"
```

or:

```text
replace every "uktam" with "swarsetu"
```

First determine whether the occurrence is:

* project branding
* package name
* third-party code
* dependency
* URL
* generated content
* historical reference
* legal notice

Then change only what is appropriate.

---

# 38. CURRENT DEVELOPMENT STRATEGY

The team must follow:

```text
P0
 ↓
Stable End-to-End Demo
 ↓
P1
 ↓
Optimization
 ↓
Testing
 ↓
P2 Enhancements
```

Do not start P2 features while P0 is broken.

---

# 39. CORE SUCCESS CRITERION

The first major milestone is:

> Two Android phones successfully communicate completely offline.

Expected flow:

```text
PHONE A

Speak
 ↓
VAD
 ↓
Offline STT
 ↓
Sentence
 ↓
Low-bitrate message
 ↓
Knit
 ↓
Wi-Fi/Bluetooth


PHONE B

Knit
 ↓
Message
 ↓
Sentence
 ↓
Offline TTS
 ↓
Speaker
```

This must work reliably before expanding the system.

---

# 40. FINAL TEAM RULE

When in doubt, prioritize:

```text
Correctness
    ↓
Offline operation
    ↓
Latency
    ↓
Accuracy
    ↓
Memory/CPU
    ↓
Reliability
    ↓
Maintainability
    ↓
UI polish
    ↓
Extra features
```

The goal is not to have the most features.

The goal is to have a **small, reliable, measurable, fully offline multilingual neural transceiver that directly satisfies PS 26173.**

---

# END OF TEAM INSTRUCTIONS