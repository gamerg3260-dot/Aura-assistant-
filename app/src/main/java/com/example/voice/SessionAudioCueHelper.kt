package com.example.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Audio cue manager producing short, pleasant tones when entering/exiting an active conversation session.
 */
object SessionAudioCueHelper {
    private const val TAG = "SessionAudioCue"
    private var sharedToneGenerator: ToneGenerator? = null

    @Synchronized
    private fun getToneGenerator(): ToneGenerator? {
        if (sharedToneGenerator == null) {
            try {
                sharedToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize shared ToneGenerator: ${e.message}")
            }
        }
        return sharedToneGenerator
    }

    fun playSessionStartCue(context: Context) {
        try {
            getToneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            Log.d(TAG, "Played session start audio cue via shared ToneGenerator.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play session start cue: ${e.message}")
        }
    }

    fun playSessionEndCue(context: Context) {
        try {
            getToneGenerator()?.startTone(ToneGenerator.TONE_PROP_PROMPT, 140)
            Log.d(TAG, "Played session end audio cue via shared ToneGenerator.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play session end cue: ${e.message}")
        }
    }

    /**
     * Call when application or service is destroyed to clean up resources.
     */
    @Synchronized
    fun release() {
        try {
            sharedToneGenerator?.release()
            sharedToneGenerator = null
            Log.d(TAG, "Shared ToneGenerator released successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing shared ToneGenerator: ${e.message}")
        }
    }
}
