package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.preferences.AssistantPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d("BootReceiver", "Received action: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val preferences = AssistantPreferences(context)
            if (preferences.isBackgroundServiceRunning) {
                try {
                    AuraVoiceService.start(context)
                    Log.d("BootReceiver", "Successfully restarted AuraVoiceService on boot")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to start AuraVoiceService on boot", e)
                }
            }
        }
    }
}
