package app.swarsetu.tts.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import android.util.Log

/**
 * Handles Android system Audio Focus requests for the TTS engine.
 *
 * Supports two modes:
 * - Normal: AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK on STREAM_MUSIC (allows other apps to duck)
 * - Alert: AUDIOFOCUS_GAIN on STREAM_ALARM at max volume (preempts other audio, non-interruptible)
 */
class TtsAudioFocusManager(
    context: Context,
    private val onAudioFocusLost: () -> Unit
) : OnAudioFocusChangeListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var normalFocusRequest: AudioFocusRequest? = null
    private var alertFocusRequest: AudioFocusRequest? = null
    private var isAlertMode = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Normal mode: allows other apps to duck
            val normalAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            normalFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(normalAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()

            // Alert mode: preempts other audio, non-interruptible, uses alarm stream
            val alertAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            alertFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(alertAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(this)
                .build()
        }
    }

    /**
     * Requests audio focus before starting speech.
     * @param isAlert If true, requests alarm-level focus (highest priority, non-interruptible).
     * @return true if focus was granted, false otherwise.
     */
    fun requestFocus(isAlert: Boolean = false): Boolean {
        isAlertMode = isAlert

        if (isAlert) {
            // Set alarm stream to max volume for alert playback
            setAlertVolume()
        }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = if (isAlert) alertFocusRequest else normalFocusRequest
            audioManager.requestAudioFocus(request!!)
        } else {
            @Suppress("DEPRECATION")
            val streamType = if (isAlert) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
            val focusGain = if (isAlert) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            audioManager.requestAudioFocus(this, streamType, focusGain)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Abandons audio focus after speech completes.
     */
    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = if (isAlertMode) alertFocusRequest else normalFocusRequest
            audioManager.abandonAudioFocusRequest(request!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
        isAlertMode = false
    }

    /**
     * Sets the alarm stream volume to maximum for alert playback.
     * This is the strongest volume behavior Android legitimately permits for alarm audio.
     */
    private fun setAlertVolume() {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentVolume < maxVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                Log.d(TAG, "Alert: set STREAM_ALARM volume from $currentVolume to $maxVolume")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set alert volume: ${e.message}")
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // If we lose focus, interrupt TTS.
                // In alert mode, we still respect system-level audio loss (e.g., phone call)
                // but resist ducking from other apps (AUDIOFOCUS_GAIN prevents that).
                onAudioFocusLost()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // We don't automatically resume TTS on focus gain.
            }
        }
    }

    companion object {
        private const val TAG = "TtsAudioFocusManager"
    }
}
