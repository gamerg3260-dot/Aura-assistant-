package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences

class AssistantPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aura_assistant_preferences", Context.MODE_PRIVATE)

    var userName: String
        get() = prefs.getString("user_name", "User") ?: "User"
        set(value) = prefs.edit().putString("user_name", value).apply()

    var selectedLanguageCode: String
        get() = prefs.getString("selected_language", "en") ?: "en"
        set(value) = prefs.edit().putString("selected_language", value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("is_onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("is_onboarding_completed", value).apply()

    var isVoiceEnrolled: Boolean
        get() = prefs.getBoolean("is_voice_enrolled", false)
        set(value) = prefs.edit().putBoolean("is_voice_enrolled", value).apply()

    var voiceprintEmbeddingString: String
        get() = prefs.getString("voiceprint_embedding", "") ?: ""
        set(value) = prefs.edit().putString("voiceprint_embedding", value).apply()

    var voiceSimilarityThreshold: Float
        get() = prefs.getFloat("voice_similarity_threshold", 0.76f)
        set(value) = prefs.edit().putFloat("voice_similarity_threshold", value).apply()

    var customWakeWord: String
        get() = prefs.getString("custom_wake_word", "Hey Aura") ?: "Hey Aura"
        set(value) = prefs.edit().putString("custom_wake_word", value).apply()

    var isBackgroundServiceRunning: Boolean
        get() = prefs.getBoolean("is_background_service_running", false)
        set(value) = prefs.edit().putBoolean("is_background_service_running", value).apply()

    // Picovoice Porcupine Wake Word Engine Configuration
    var usePorcupineWakeWord: Boolean
        get() = prefs.getBoolean("use_porcupine_wake_word", true)
        set(value) = prefs.edit().putBoolean("use_porcupine_wake_word", value).apply()

    var picovoiceAccessKey: String
        get() = prefs.getString("picovoice_access_key", "") ?: ""
        set(value) = prefs.edit().putString("picovoice_access_key", value).apply()

    var selectedPorcupineKeyword: String
        get() = prefs.getString("selected_porcupine_keyword", "JARVIS") ?: "JARVIS"
        set(value) = prefs.edit().putString("selected_porcupine_keyword", value).apply()

    var porcupineSensitivity: Float
        get() = prefs.getFloat("porcupine_sensitivity", 0.6f)
        set(value) = prefs.edit().putFloat("porcupine_sensitivity", value).apply()

    var isLicenseValidated: Boolean
        get() = prefs.getBoolean("is_license_validated", false)
        set(value) = prefs.edit().putBoolean("is_license_validated", value).apply()

    var licenseKeyMasked: String
        get() = prefs.getString("license_key_masked", "") ?: ""
        set(value) = prefs.edit().putString("license_key_masked", value).apply()

    // 10 Feature Categories Toggles
    // 1. System Control
    var toggleSystemControlVolume: Boolean
        get() = prefs.getBoolean("toggle_sys_volume", true)
        set(value) = prefs.edit().putBoolean("toggle_sys_volume", value).apply()

    var toggleSystemControlBrightness: Boolean
        get() = prefs.getBoolean("toggle_sys_brightness", true)
        set(value) = prefs.edit().putBoolean("toggle_sys_brightness", value).apply()

    var toggleSystemControlWifiBt: Boolean
        get() = prefs.getBoolean("toggle_sys_wifi_bt", true)
        set(value) = prefs.edit().putBoolean("toggle_sys_wifi_bt", value).apply()

    var toggleSystemControlAppLauncher: Boolean
        get() = prefs.getBoolean("toggle_sys_app_launcher", true)
        set(value) = prefs.edit().putBoolean("toggle_sys_app_launcher", value).apply()

    // 2. Communication
    var toggleCommPhoneCalls: Boolean
        get() = prefs.getBoolean("toggle_comm_phone", true)
        set(value) = prefs.edit().putBoolean("toggle_comm_phone", value).apply()

    var toggleCommSms: Boolean
        get() = prefs.getBoolean("toggle_comm_sms", true)
        set(value) = prefs.edit().putBoolean("toggle_comm_sms", value).apply()

    var toggleCommWhatsapp: Boolean
        get() = prefs.getBoolean("toggle_comm_whatsapp", true)
        set(value) = prefs.edit().putBoolean("toggle_comm_whatsapp", value).apply()

    // 3. Info
    var toggleInfoWeather: Boolean
        get() = prefs.getBoolean("toggle_info_weather", true)
        set(value) = prefs.edit().putBoolean("toggle_info_weather", value).apply()

    var toggleInfoDeviceStatus: Boolean
        get() = prefs.getBoolean("toggle_info_device", true)
        set(value) = prefs.edit().putBoolean("toggle_info_device", value).apply()

    var toggleInfoLocation: Boolean
        get() = prefs.getBoolean("toggle_info_location", true)
        set(value) = prefs.edit().putBoolean("toggle_info_location", value).apply()

    // 4. Media
    var toggleMediaSpotify: Boolean
        get() = prefs.getBoolean("toggle_media_spotify", true)
        set(value) = prefs.edit().putBoolean("toggle_media_spotify", value).apply()

    var toggleMediaYoutube: Boolean
        get() = prefs.getBoolean("toggle_media_youtube", true)
        set(value) = prefs.edit().putBoolean("toggle_media_youtube", value).apply()

    // 5. Productivity
    var toggleProdReminders: Boolean
        get() = prefs.getBoolean("toggle_prod_reminders", true)
        set(value) = prefs.edit().putBoolean("toggle_prod_reminders", value).apply()

    var toggleProdTimers: Boolean
        get() = prefs.getBoolean("toggle_prod_timers", true)
        set(value) = prefs.edit().putBoolean("toggle_prod_timers", value).apply()

    var toggleProdMemory: Boolean
        get() = prefs.getBoolean("toggle_prod_memory", true)
        set(value) = prefs.edit().putBoolean("toggle_prod_memory", value).apply()

    // 6. Vision
    var toggleVisionCamera: Boolean
        get() = prefs.getBoolean("toggle_vision_camera", true)
        set(value) = prefs.edit().putBoolean("toggle_vision_camera", value).apply()

    var toggleVisionScreenReading: Boolean
        get() = prefs.getBoolean("toggle_vision_screen", true)
        set(value) = prefs.edit().putBoolean("toggle_vision_screen", value).apply()

    var toggleVisionObjectRecognition: Boolean
        get() = prefs.getBoolean("toggle_vision_objects", true)
        set(value) = prefs.edit().putBoolean("toggle_vision_objects", value).apply()

    // 7. Web
    var toggleWebGoogleSearch: Boolean
        get() = prefs.getBoolean("toggle_web_search", true)
        set(value) = prefs.edit().putBoolean("toggle_web_search", value).apply()

    // 8. Security
    var toggleSecurityAntiTheft: Boolean
        get() = prefs.getBoolean("toggle_sec_antitheft", false)
        set(value) = prefs.edit().putBoolean("toggle_sec_antitheft", value).apply()

    var toggleSecurityIntruderAlert: Boolean
        get() = prefs.getBoolean("toggle_sec_intruder", true)
        set(value) = prefs.edit().putBoolean("toggle_sec_intruder", value).apply()

    // 9. Calls
    var toggleCallsAnnouncer: Boolean
        get() = prefs.getBoolean("toggle_calls_announcer", true)
        set(value) = prefs.edit().putBoolean("toggle_calls_announcer", value).apply()

    var toggleCallsVoiceAnswer: Boolean
        get() = prefs.getBoolean("toggle_calls_voice_answer", true)
        set(value) = prefs.edit().putBoolean("toggle_calls_voice_answer", value).apply()

    // 10. Contacts
    var toggleContactsPriority: Boolean
        get() = prefs.getBoolean("toggle_contacts_priority", true)
        set(value) = prefs.edit().putBoolean("toggle_contacts_priority", value).apply()

    fun clearVoiceprint() {
        prefs.edit()
            .remove("is_voice_enrolled")
            .remove("voiceprint_embedding")
            .apply()
    }

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
