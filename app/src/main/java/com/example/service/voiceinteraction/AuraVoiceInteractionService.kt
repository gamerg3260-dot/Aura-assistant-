package com.example.service.voiceinteraction

import android.content.ComponentName
import android.content.Context
import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * System-level VoiceInteractionService that registers Aura as a system-recognized
 * Default Digital Assistant in Android Settings -> Apps -> Default apps -> Digital assistant app.
 */
class AuraVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "AuraVoiceInteraction"

        /**
         * Checks whether Aura AI is currently set by the user as the device's Default Digital Assistant.
         */
        fun isSelectedAsDefaultAssistant(context: Context): Boolean {
            return try {
                isActiveService(
                    context,
                    ComponentName(context, AuraVoiceInteractionService::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed checking active digital assistant status: ${e.message}")
                false
            }
        }
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "AuraVoiceInteractionService onReady: Aura AI is now active as the Default Digital Assistant!")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(TAG, "AuraVoiceInteractionService onShutdown")
    }
}
