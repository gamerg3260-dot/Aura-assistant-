package com.example.data.model

data class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val speechLocaleTag: String
) {
    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            LanguageOption("en", "English", "English", "en-US"),
            LanguageOption("hi", "Hindi", "हिन्दी", "hi-IN"),
            LanguageOption("hinglish", "Hinglish", "Hinglish", "hi-IN"),
            LanguageOption("ta", "Tamil", "தமிழ்", "ta-IN"),
            LanguageOption("te", "Telugu", "తెలుగు", "te-IN"),
            LanguageOption("kn", "Kannada", "ಕನ್ನಡ", "kn-IN"),
            LanguageOption("mr", "Marathi", "मराठी", "mr-IN"),
            LanguageOption("bn", "Bengali", "বাংলা", "bn-IN"),
            LanguageOption("gu", "Gujarati", "ગુજરાતી", "gu-IN"),
            LanguageOption("pa", "Punjabi", "ਪੰਜਾਬੀ", "pa-IN"),
            LanguageOption("ml", "Malayalam", "മലയാളം", "ml-IN"),
            LanguageOption("es", "Spanish", "Español", "es-ES"),
            LanguageOption("fr", "French", "Français", "fr-FR"),
            LanguageOption("de", "German", "Deutsch", "de-DE"),
            LanguageOption("ja", "Japanese", "日本語", "ja-JP")
        )
    }
}

enum class AssistantListeningState {
    STANDBY,
    WAKE_WORD_LISTENING,
    SPEAKER_VERIFYING,
    SPEECH_LISTENING,
    PROCESSING_LLM,
    EXECUTING_ACTION,
    SPEAKING,
    VOICE_MISMATCH_ALERT,
    ERROR
}

data class DeviceStats(
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val ramUsedMb: Long = 0,
    val ramTotalMb: Long = 0,
    val storageFreeGb: Double = 0.0,
    val storageTotalGb: Double = 0.0,
    val locationCity: String = "Detecting Location...",
    val isOnline: Boolean = true
)

data class CalendarEventItem(
    val title: String,
    val timeFormatted: String,
    val location: String? = null
)

data class ToolExecutionResult(
    val toolName: String,
    val summary: String,
    val success: Boolean,
    val details: String? = null
)
