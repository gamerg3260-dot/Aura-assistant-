package com.example.service.voiceinteraction

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.MainActivity

/**
 * Trampoline activity responding to android.intent.action.ASSIST and android.intent.action.VOICE_COMMAND.
 * When invoked by the system assist gesture, corner swipe, or hardware assistant button,
 * it immediately activates Aura to listen for the user's voice command like Google Assistant.
 */
class AuraAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val assistantIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("AUTO_TRIGGER_LISTEN", true)
            putExtra("FROM_DEFAULT_ASSISTANT", true)
            putExtra("OWNER_VERIFIED", true)
        }
        startActivity(assistantIntent)
        finish()
    }
}
