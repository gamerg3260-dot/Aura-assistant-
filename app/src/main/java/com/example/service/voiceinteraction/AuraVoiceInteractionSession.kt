package com.example.service.voiceinteraction

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import com.example.MainActivity

/**
 * Handles active voice interaction sessions initiated when the user triggers the assist gesture
 * (holding the home button, power button assist, or corner swipe) as the Default Digital Assistant.
 */
class AuraVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val tag = "AuraVoiceSession"

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(tag, "Assist gesture/hotword triggered VoiceInteractionSession with flags=$showFlags")

        // Immediately launch Aura AI in auto-listen mode, just like Google Assistant
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("AUTO_TRIGGER_LISTEN", true)
            putExtra("FROM_DEFAULT_ASSISTANT", true)
            putExtra("OWNER_VERIFIED", true)
        }

        try {
            startAssistantActivity(launchIntent)
        } catch (e: Exception) {
            Log.w(tag, "startAssistantActivity fallback: ${e.message}")
            context.startActivity(launchIntent)
        }
        hide()
    }

    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        super.onHandleAssist(data, structure, content)
        Log.d(tag, "Received system assist context data")
    }
}
