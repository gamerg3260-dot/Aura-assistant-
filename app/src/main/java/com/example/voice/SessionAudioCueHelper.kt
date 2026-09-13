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

    fun playSessionStartCue(context: Context) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            Log.d(TAG, "Played session start audio cue.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play session start cue: ${e.message}")
        }
    }

    fun playSessionEndCue(context: Context) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
            toneGen.startTone(ToneGenerator.TONE_PROP_PROMPT, 140)
            Log.d(TAG, "Played session end audio cue.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play session end cue: ${e.message}")
        }
    }
}
