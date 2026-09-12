package com.example

import android.app.Application
import com.example.data.api.GeminiService
import com.example.data.database.AuraDatabase
import com.example.data.preferences.AssistantPreferences
import com.example.data.security.KeystoreManager
import com.example.system.DeviceController
import com.example.system.ToolExecutionModule
import com.example.voice.GeminiSpokenOutputManager
import com.example.voice.VoiceprintEngine

class AuraApplication : Application() {

    lateinit var database: AuraDatabase
        private set
    lateinit var preferences: AssistantPreferences
        private set
    lateinit var keystoreManager: KeystoreManager
        private set
    lateinit var voiceprintEngine: VoiceprintEngine
        private set
    lateinit var deviceController: DeviceController
        private set
    lateinit var toolExecutionModule: ToolExecutionModule
        private set
    lateinit var theftGuardManager: com.example.security.TheftGuardManager
        private set
    lateinit var geminiService: GeminiService
        private set
    lateinit var spokenOutputManager: GeminiSpokenOutputManager
        private set
    lateinit var customVoiceCloneManager: com.example.voice.CustomVoiceCloneManager
        private set
    lateinit var callManager: com.example.system.AuraCallManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AuraDatabase.getInstance(this)
        preferences = AssistantPreferences(this)
        keystoreManager = KeystoreManager(this)
        voiceprintEngine = VoiceprintEngine()
        deviceController = DeviceController(this)
        toolExecutionModule = ToolExecutionModule(this, preferences)
        theftGuardManager = com.example.security.TheftGuardManager(this)
        customVoiceCloneManager = com.example.voice.CustomVoiceCloneManager(this, preferences)
        geminiService = GeminiService(this, keystoreManager, preferences, deviceController, toolExecutionModule)
        spokenOutputManager = GeminiSpokenOutputManager(this, preferences.selectedLanguageCode)
        callManager = com.example.system.AuraCallManager(
            context = this,
            preferences = preferences,
            onIncomingCallDetected = { callerName, _ ->
                if (preferences.toggleCallsAnnouncer) {
                    spokenOutputManager.speakGeminiResponse("Incoming call from $callerName. Say Answer or Reject.")
                }
            }
        )
    }

    companion object {
        lateinit var instance: AuraApplication
            private set
    }
}
