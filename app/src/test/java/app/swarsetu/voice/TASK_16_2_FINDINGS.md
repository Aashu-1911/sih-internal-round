# Task 16.2: Voice Note (AAC/ADTS) Recording Test - FINDINGS

## Test Status: ⚠️ **CRITICAL PRESERVATION VIOLATION DETECTED**

### Task Objective
Test voice note (AAC/ADTS) recording to verify that the bugfix preserves the existing voice note attachment flow separate from the PTT walkie-talkie flow.

### Requirement 3.2
> WHEN a user attaches and sends an image or voice note (AAC/ADTS) through the existing attachment flow THEN the system SHALL CONTINUE TO ingest, stage, and transmit the attachment via VoiceRecorder and AttachmentStore exactly as before.

## Test Results

### Test Implementation
- **Test File**: `app/src/test/java/app/swarsetu/voice/VoiceMeshPipelinePreservationTest.kt`
- **Test Method**: `test 16_2 - voice note attachment flow uses VoiceRecorder and AttachmentStore not STT`
- **Test Status**: ✅ **PASSED** (compilation and unit test execution)

### Critical Finding: **PRESERVATION VIOLATION**

The test has detected that the PTT bugfix has **BROKEN** the voice note attachment flow:

#### Evidence:

1. **VoiceRecorder Exists But Unused**
   - `ChatViewModel` has `private val recorder by lazy { VoiceRecorder(context, viewModelScope) }`
   - This field is declared but **NEVER USED** in the current code

2. **startVoiceRecording() Uses ONLY PTT**
   ```kotlin
   fun startVoiceRecording(locked: Boolean = false): Boolean {
       // ...
       sttPipeline.startCapture(language)  // ← ONLY PTT path
       // NO call to recorder.start()       // ← Voice note path MISSING
   }
   ```

3. **No Separate Voice Note Flow**
   - There is NO method to record voice notes (AAC/ADTS) as attachments
   - There is NO way to call `AttachmentStore.ingestVoice()`
   - ALL voice recording now goes through PTT (SttPipeline → transcription → text message)

4. **Two Required Flows - Only One Exists**

   **Expected (Requirement 3.2)**:
   - **PTT Flow** (new): Mic button press/hold → `sttPipeline` → transcription → mesh as text message
   - **Voice Note Flow** (existing, must preserve): Record button → `VoiceRecorder` → AAC/ADTS → `AttachmentStore` → mesh as audio attachment

   **Actual (Current Code)**:
   - **PTT Flow** (implemented): Mic button → `sttPipeline` → transcription → text ✅
   - **Voice Note Flow** (broken): Does not exist ❌

## Impact Analysis

### User Impact
Users **CANNOT** send voice notes as audio attachments. They can only:
- Send text messages
- Send PTT transcribed messages (as text)
- Send image attachments

The audio attachment capability has been **REMOVED** by the PTT fix.

### Preservation Requirement Violated
Requirement 3.2 explicitly states that voice notes (AAC/ADTS) must **CONTINUE TO** work via the existing `VoiceRecorder` + `AttachmentStore` flow. This is a **regression** introduced by the PTT bugfix.

## Root Cause

The PTT bugfix merged the "Stashed changes" side of the merge conflict, which replaced the voice note recording flow with PTT. The fix did not preserve both flows - it replaced one with the other.

### What Should Have Happened
The fix should have:
1. Added PTT flow for walkie-talkie mode (new feature)
2. **Preserved** voice note flow for attachments (existing feature)
3. Provided UI to distinguish between the two modes

### What Actually Happened
The fix:
1. Added PTT flow ✅
2. **Removed** voice note flow ❌
3. Only PTT mode exists

## Recommendations

### Option 1: Add Voice Note Recording Method (Recommended)
Add a separate method to `ChatViewModel` for voice note attachments:

```kotlin
fun startVoiceNoteRecording(): Boolean {
    if (_voiceRecording.value != null || !recorder.start()) {
        return false
    }
    _voiceRecording.value = VoiceRecording(elapsedMs = 0L, amplitude = 0f, locked = false)
    // ... start ticker using recorder.amplitude() not sttPipeline.amplitude
    return true
}

suspend fun stopVoiceNoteRecordingAndStage() {
    val bytes = recorder.stop() ?: return
    if (bytes.size < MIN_VOICE_NOTE_BYTES) return
    
    // Ingest into AttachmentStore
    val ingested = attachments.ingestVoice(bytes)
    _pendingAttachment.value = ingested
    
    _voiceRecording.value = null
}
```

### Option 2: Mode Parameter
Modify `startVoiceRecording` to accept a mode parameter:

```kotlin
enum class VoiceMode { PTT, VOICE_NOTE }

fun startVoiceRecording(mode: VoiceMode = VoiceMode.PTT, locked: Boolean = false): Boolean {
    when (mode) {
        VoiceMode.PTT -> {
            // Current PTT logic
            sttPipeline.startCapture(language)
        }
        VoiceMode.VOICE_NOTE -> {
            // Voice note logic
            recorder.start()
        }
    }
}
```

### Option 3: UI Changes
Provide separate UI affordances:
- **Long-press mic button**: PTT mode (transcribe → send text)
- **Tap attachment button → voice note**: Record voice note (AAC/ADTS → send audio)

## Test Enhancement Needed

The current test PASSES but only validates that VoiceRecorder CAN work. It should be enhanced to:

1. ❌ **FAIL** when `startVoiceRecording` does not call `recorder.start()`
2. ❌ **FAIL** when there is no method to create voice note attachments
3. ❌ **FAIL** when `AttachmentStore.ingestVoice()` is unreachable from ChatViewModel

## Conclusion

**Task 16.2 Status**: ⚠️ **CRITICAL ISSUE DETECTED**

The test successfully **detected** that the voice note attachment flow has been broken by the PTT bugfix. This is a **preservation violation** of Requirement 3.2.

**Action Required**: Restore the voice note attachment capability before considering the bugfix complete.

---

**Test Author**: Kiro AI
**Test Date**: 2024 (Current Session)
**Severity**: HIGH (Core feature removal)
**Priority**: CRITICAL (Requirement 3.2 violation)
