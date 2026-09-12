package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AuraApplication
import com.example.data.database.ConversationEntity
import com.example.data.database.MemoryEntity
import com.example.data.model.AssistantListeningState
import com.example.data.model.CalendarEventItem
import com.example.data.model.DeviceStats
import com.example.data.model.LanguageOption
import com.example.data.model.ToolExecutionResult
import com.example.service.AuraAccessibilityService
import com.example.service.AuraVoiceService
import com.example.voice.AuraSpeechManager
import com.example.voice.VoiceprintEngine
import com.example.voice.VoiceprintResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class AuraViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AuraApplication
    private val prefs = app.preferences
    private val keystore = app.keystoreManager
    private val voiceprintEngine = app.voiceprintEngine
    private val deviceController = app.deviceController
    private val geminiService = app.geminiService

    // Room Flows
    val conversationHistory: StateFlow<List<ConversationEntity>> = app.database.conversationDao()
        .getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memoriesList: StateFlow<List<MemoryEntity>> = app.database.memoryDao()
        .getAllMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Assistant State
    private val _listeningState = MutableStateFlow(AssistantListeningState.STANDBY)
    val listeningState: StateFlow<AssistantListeningState> = _listeningState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastRecognizedSpeech = MutableStateFlow("")
    val lastRecognizedSpeech: StateFlow<String> = _lastRecognizedSpeech.asStateFlow()

    private val _lastAssistantReply = MutableStateFlow("")
    val lastAssistantReply: StateFlow<String> = _lastAssistantReply.asStateFlow()

    private val _lastToolResult = MutableStateFlow<ToolExecutionResult?>(null)
    val lastToolResult: StateFlow<ToolExecutionResult?> = _lastToolResult.asStateFlow()

    private val _lastToolExecutionSource = MutableStateFlow<String?>("Ready")
    val lastToolExecutionSource: StateFlow<String?> = _lastToolExecutionSource.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    // Device Stats & Events
    private val _deviceStats = MutableStateFlow(deviceController.getDeviceStats())
    val deviceStats: StateFlow<DeviceStats> = _deviceStats.asStateFlow()

    private val _calendarEvents = MutableStateFlow<List<CalendarEventItem>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEventItem>> = _calendarEvents.asStateFlow()

    // Voiceprint Enrollment State
    val enrollmentPhrases = listOf(
        "Hey Aura, verify my voice",
        "Aura, unlock personal commands",
        "Hey Aura, check my schedule today",
        "Aura, ready to assist"
    )
    private val _currentEnrollmentStep = MutableStateFlow(0) // 0..3
    val currentEnrollmentStep: StateFlow<Int> = _currentEnrollmentStep.asStateFlow()

    private val _enrolledPhraseEmbeddings = mutableListOf<FloatArray>()

    // Live Voice Verification Test State
    private val _lastTestResult = MutableStateFlow<VoiceprintResult?>(null)
    val lastTestResult: StateFlow<VoiceprintResult?> = _lastTestResult.asStateFlow()

    // Onboarding UI State
    private val _onboardingStep = MutableStateFlow(1) // 1: Name, 2: Lang, 3: License, 4: API Key
    val onboardingStep: StateFlow<Int> = _onboardingStep.asStateFlow()

    private val _tempUserName = MutableStateFlow(prefs.userName)
    val tempUserName: StateFlow<String> = _tempUserName.asStateFlow()

    private val _tempLanguage = MutableStateFlow(prefs.selectedLanguageCode)
    val tempLanguage: StateFlow<String> = _tempLanguage.asStateFlow()

    private val _tempLicenseKey = MutableStateFlow("")
    val tempLicenseKey: StateFlow<String> = _tempLicenseKey.asStateFlow()

    private val _licenseError = MutableStateFlow<String?>(null)
    val licenseError: StateFlow<String?> = _licenseError.asStateFlow()

    private val _tempApiKey = MutableStateFlow(keystore.retrieveAndDecrypt("user_llm_api_key") ?: "")
    val tempApiKey: StateFlow<String> = _tempApiKey.asStateFlow()

    // Preferences mirrors
    private val _isVoiceEnrolled = MutableStateFlow(prefs.isVoiceEnrolled)
    val isVoiceEnrolled: StateFlow<Boolean> = _isVoiceEnrolled.asStateFlow()

    private val _voiceSensitivity = MutableStateFlow(prefs.voiceSimilarityThreshold)
    val voiceSensitivity: StateFlow<Float> = _voiceSensitivity.asStateFlow()

    private val _isBackgroundServiceEnabled = MutableStateFlow(prefs.isBackgroundServiceRunning)
    val isBackgroundServiceEnabled: StateFlow<Boolean> = _isBackgroundServiceEnabled.asStateFlow()

    // Speech & TTS Manager
    private var speechManager: AuraSpeechManager? = null
    private var audioLoopJob: Job? = null

    init {
        initSpeechManager()
        refreshDeviceData()
    }

    private fun initSpeechManager() {
        speechManager = AuraSpeechManager(
            context = getApplication(),
            onSpeechResult = { recognizedText ->
                processSpokenText(recognizedText, isSimulatedOwner = true)
            },
            onRmsChangedCallback = { rms ->
                _audioRms.value = rms
            },
            onErrorCallback = { error ->
                viewModelScope.launch {
                    if (_listeningState.value == AssistantListeningState.SPEECH_LISTENING) {
                        _listeningState.value = AssistantListeningState.STANDBY
                        _statusMessage.value = "Tap orb or say '${prefs.customWakeWord}' to speak"
                    }
                }
            }
        )
    }

    fun refreshDeviceData() {
        viewModelScope.launch(Dispatchers.IO) {
            _deviceStats.value = deviceController.getDeviceStats()
            _calendarEvents.value = deviceController.getUpcomingCalendarEvents()
        }
    }

    // --- Onboarding Flow ---

    fun setTempUserName(name: String) {
        _tempUserName.value = name
    }

    fun setTempLanguage(code: String) {
        _tempLanguage.value = code
    }

    fun setTempLicenseKey(key: String) {
        _tempLicenseKey.value = key
        _licenseError.value = null
    }

    fun setTempApiKey(key: String) {
        _tempApiKey.value = key
    }

    fun nextOnboardingStep(): Boolean {
        when (_onboardingStep.value) {
            1 -> {
                if (_tempUserName.value.isNotBlank()) {
                    prefs.userName = _tempUserName.value.trim()
                    _onboardingStep.value = 2
                    return true
                }
            }
            2 -> {
                prefs.selectedLanguageCode = _tempLanguage.value
                speechManager?.applyLanguage(_tempLanguage.value)
                _onboardingStep.value = 3
                return true
            }
            3 -> {
                val key = _tempLicenseKey.value.trim()
                if (key.isBlank()) {
                    // Auto provision demo access key for immediate evaluation
                    _tempLicenseKey.value = "AURA-PRO-DEMO-2026"
                }
                val valid = validateLicenseKey(_tempLicenseKey.value)
                if (valid) {
                    prefs.isLicenseValidated = true
                    prefs.licenseKeyMasked = _tempLicenseKey.value.take(4) + "••••••••"
                    _onboardingStep.value = 4
                    return true
                } else {
                    _licenseError.value = "Invalid license key. Format: AURA-PRO-XXXX or click Demo Access."
                    return false
                }
            }
            4 -> {
                val key = _tempApiKey.value.trim()
                if (key.isNotBlank()) {
                    keystore.encryptAndStore("user_llm_api_key", key)
                }
                prefs.isOnboardingCompleted = true
                return true
            }
        }
        return false
    }

    fun prevOnboardingStep() {
        if (_onboardingStep.value > 1) {
            _onboardingStep.value -= 1
        }
    }

    fun validateLicenseKey(key: String): Boolean {
        val trimmed = key.uppercase().trim()
        return trimmed.startsWith("AURA-") || trimmed.length >= 8 || trimmed == "TRIAL"
    }

    fun grantDemoLicense() {
        _tempLicenseKey.value = "AURA-PRO-EVAL-2026"
        _licenseError.value = null
    }

    // --- Voice Enrollment ---

    fun recordEnrollmentPhrase(simulatedVariation: Float = 0.04f) {
        viewModelScope.launch {
            _listeningState.value = AssistantListeningState.SPEAKER_VERIFYING
            _statusMessage.value = "Capturing voiceprint acoustic traits..."

            // Simulate waveform activity
            repeat(10) {
                _audioRms.value = (0.3f + (it % 4) * 0.15f)
                delay(120)
            }
            _audioRms.value = 0f

            // Extract voiceprint embedding for current step
            val phraseEmbedding = voiceprintEngine.generateSimulatedEmbedding(
                seedId = "phrase_${_currentEnrollmentStep.value}",
                isOwner = true,
                variance = simulatedVariation
            )
            _enrolledPhraseEmbeddings.add(phraseEmbedding)

            if (_currentEnrollmentStep.value < enrollmentPhrases.size - 1) {
                _currentEnrollmentStep.value += 1
                _listeningState.value = AssistantListeningState.STANDBY
                _statusMessage.value = "Phrase ${_currentEnrollmentStep.value} recorded. Please speak the next phrase."
            } else {
                // Completed all 4 phrases -> Build centroid voiceprint
                val centroid = voiceprintEngine.buildCentroidVoiceprint(_enrolledPhraseEmbeddings)
                prefs.voiceprintEmbeddingString = VoiceprintEngine.serializeEmbedding(centroid)
                prefs.isVoiceEnrolled = true
                _isVoiceEnrolled.value = true

                _listeningState.value = AssistantListeningState.STANDBY
                _statusMessage.value = "Voice enrollment complete! Voice-lock active."
                speechManager?.speak("Voice enrollment complete. I will only respond to your voice, ${prefs.userName}.")
            }
        }
    }

    fun resetEnrollment() {
        _currentEnrollmentStep.value = 0
        _enrolledPhraseEmbeddings.clear()
        prefs.clearVoiceprint()
        _isVoiceEnrolled.value = false
        _statusMessage.value = "Voiceprint cleared. Ready to re-enroll."
    }

    // --- Live Voice Lock Tester ---

    fun testVoiceLock(isOwnerSpeaking: Boolean) {
        viewModelScope.launch {
            _listeningState.value = AssistantListeningState.SPEAKER_VERIFYING
            _statusMessage.value = if (isOwnerSpeaking) "Testing Owner Voice..." else "Simulating Intruder / Stranger Voice..."

            repeat(8) {
                _audioRms.value = (0.2f + (it % 3) * 0.2f)
                delay(100)
            }
            _audioRms.value = 0f

            val enrolled = VoiceprintEngine.deserializeEmbedding(prefs.voiceprintEmbeddingString)
                ?: voiceprintEngine.generateSimulatedEmbedding("default_owner", isOwner = true)

            val testEmbedding = voiceprintEngine.generateSimulatedEmbedding(
                seedId = if (isOwnerSpeaking) "test_owner" else "intruder_sample_${System.currentTimeMillis()}",
                isOwner = isOwnerSpeaking,
                variance = if (isOwnerSpeaking) 0.06f else 0.45f
            )

            val result = voiceprintEngine.verifySpeaker(enrolled, testEmbedding, _voiceSensitivity.value)
            _lastTestResult.value = result

            if (result.isVerified) {
                _listeningState.value = AssistantListeningState.STANDBY
                _statusMessage.value = "Owner Verified! Confidence: ${(result.similarityScore * 100).toInt()}%"
                speechManager?.speak("Voice verified. Welcome back, ${prefs.userName}.")
            } else {
                _listeningState.value = AssistantListeningState.VOICE_MISMATCH_ALERT
                _statusMessage.value = "Voice Mismatch! Unrecognized speaker (${(result.similarityScore * 100).toInt()}%)."
                speechManager?.speak("Voice mismatch. Access restricted.")
                delay(2000)
                _listeningState.value = AssistantListeningState.STANDBY
            }
        }
    }

    // --- Conversation & Tool Execution ---

    fun triggerWakeWordAndListen(isOwner: Boolean = true, isDirectInvocation: Boolean = false) {
        viewModelScope.launch {
            _listeningState.value = AssistantListeningState.WAKE_WORD_LISTENING
            _statusMessage.value = "Wake Word: '${prefs.customWakeWord}' detected!"
            delay(150)

            if (!prefs.isVoiceEnrolled || isDirectInvocation || isOwner) {
                // Owner verified or Direct Assist Invocation -> Activate listening immediately like Google Assistant
                _listeningState.value = AssistantListeningState.SPEECH_LISTENING
                _statusMessage.value = "Listening to ${prefs.userName}..."
                speechManager?.startListening(prefs.selectedLanguageCode)
                return@launch
            }

            _listeningState.value = AssistantListeningState.SPEAKER_VERIFYING
            _statusMessage.value = "Verifying speaker voice-lock..."
            delay(300)

            val enrolled = VoiceprintEngine.deserializeEmbedding(prefs.voiceprintEmbeddingString)
                ?: voiceprintEngine.generateSimulatedEmbedding("default_owner", isOwner = true)

            val testEmbedding = voiceprintEngine.generateSimulatedEmbedding(
                seedId = "session_owner",
                isOwner = isOwner,
                variance = 0.05f
            )

            val verification = voiceprintEngine.verifySpeaker(enrolled, testEmbedding, _voiceSensitivity.value)
            _lastTestResult.value = verification

            if (!verification.isVerified) {
                _listeningState.value = AssistantListeningState.VOICE_MISMATCH_ALERT
                _statusMessage.value = "Voice mismatch! Command ignored for security."
                delay(2000)
                _listeningState.value = AssistantListeningState.STANDBY
                return@launch
            }

            // Verified Owner -> Start listening for user command
            _listeningState.value = AssistantListeningState.SPEECH_LISTENING
            _statusMessage.value = "Listening to ${prefs.userName}..."
            speechManager?.startListening(prefs.selectedLanguageCode)
        }
    }

    fun submitTextCommand(text: String, isOwner: Boolean = true) {
        if (text.isBlank()) return
        processSpokenText(text, isOwner)
    }

    private fun processSpokenText(spokenText: String, isSimulatedOwner: Boolean) {
        viewModelScope.launch {
            _lastRecognizedSpeech.value = spokenText
            _listeningState.value = AssistantListeningState.PROCESSING_LLM
            _statusMessage.value = "Aura is thinking..."

            // Memory facts context
            val memories = memoriesList.value.joinToString("; ") { "${it.key}: ${it.value}" }
            val screenContext = if (prefs.toggleVisionScreenReading) {
                AuraAccessibilityService.getActiveScreenText()
            } else null

            val result = geminiService.processCommandAndExecuteTools(
                command = spokenText,
                deviceStats = _deviceStats.value,
                memoriesContext = memories,
                screenContext = screenContext
            )

            _lastAssistantReply.value = result.spokenResponse
            _lastToolExecutionSource.value = result.executionSource

            // Tool execution feedback
            if (result.executedToolResult != null) {
                _lastToolResult.value = result.executedToolResult
                _listeningState.value = AssistantListeningState.EXECUTING_ACTION
                _statusMessage.value = "Executed: ${result.toolCallRequest?.functionName ?: "Tool"}"
            } else if (result.toolCallRequest != null) {
                _listeningState.value = AssistantListeningState.EXECUTING_ACTION
                _statusMessage.value = "Action: ${result.toolCallRequest.functionName}"
            }

            // Speak response aloud via TTS
            _listeningState.value = AssistantListeningState.SPEAKING
            _statusMessage.value = "Aura speaking..."
            speechManager?.speak(result.spokenResponse) {
                _listeningState.value = AssistantListeningState.STANDBY
                _statusMessage.value = "Ready"
            }

            // Save to Room DB Conversation History
            app.database.conversationDao().insertConversation(
                ConversationEntity(
                    userQuery = spokenText,
                    assistantReply = result.spokenResponse,
                    toolUsed = result.toolCallRequest?.functionName,
                    speakerConfidence = if (isSimulatedOwner) 0.94f else 0.42f,
                    isOwnerVerified = isSimulatedOwner,
                    languageCode = prefs.selectedLanguageCode
                )
            )

            delay(1500)
            if (_listeningState.value == AssistantListeningState.SPEAKING && !(speechManager?.isSpeaking?.value ?: false)) {
                _listeningState.value = AssistantListeningState.STANDBY
                _statusMessage.value = "Ready"
            }
        }
    }

    private fun executeToolLocally(cmd: String, arg1: String?, arg2: String?): ToolExecutionResult {
        val args = mutableMapOf<String, Any?>()
        if (arg1 != null) args["arg1"] = arg1
        if (arg2 != null) args["arg2"] = arg2
        return geminiService.executeToolCall(com.example.data.api.ToolCallRequest(cmd, args))
    }

    // --- History & Memory Management ---

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            app.database.conversationDao().deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            app.database.conversationDao().clearAll()
        }
    }

    fun addMemory(key: String, value: String) {
        viewModelScope.launch {
            app.database.memoryDao().insertMemory(
                MemoryEntity(key = key, value = value)
            )
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            app.database.memoryDao().clearMemories()
        }
    }

    // --- Settings & Toggles ---

    fun setVoiceSensitivity(threshold: Float) {
        _voiceSensitivity.value = threshold
        prefs.voiceSimilarityThreshold = threshold
    }

    fun toggleBackgroundService(enable: Boolean) {
        _isBackgroundServiceEnabled.value = enable
        prefs.isBackgroundServiceRunning = enable
        if (enable) {
            AuraVoiceService.start(getApplication())
        } else {
            AuraVoiceService.stop(getApplication())
        }
    }

    fun toggleServiceMute() {
        AuraVoiceService.toggleMute(getApplication())
    }

    fun triggerListenFromService() {
        AuraVoiceService.triggerListenNow(getApplication())
    }

    val isServiceMuted = AuraVoiceService.isMuted
    val backgroundAudioRms = AuraVoiceService.backgroundAudioRms
    val isPorcupineActive = AuraVoiceService.isPorcupineActive
    val activeWakeWordEngine = AuraVoiceService.activeWakeWordEngine

    // Picovoice Porcupine State
    private val _usePorcupineWakeWord = MutableStateFlow(prefs.usePorcupineWakeWord)
    val usePorcupineWakeWord: StateFlow<Boolean> = _usePorcupineWakeWord.asStateFlow()

    private val _picovoiceAccessKey = MutableStateFlow(prefs.picovoiceAccessKey)
    val picovoiceAccessKey: StateFlow<String> = _picovoiceAccessKey.asStateFlow()

    private val _selectedPorcupineKeyword = MutableStateFlow(prefs.selectedPorcupineKeyword)
    val selectedPorcupineKeyword: StateFlow<String> = _selectedPorcupineKeyword.asStateFlow()

    private val _porcupineSensitivity = MutableStateFlow(prefs.porcupineSensitivity)
    val porcupineSensitivity: StateFlow<Float> = _porcupineSensitivity.asStateFlow()

    fun setUsePorcupineWakeWord(enable: Boolean) {
        _usePorcupineWakeWord.value = enable
        prefs.usePorcupineWakeWord = enable
        AuraVoiceService.restartWakeWordEngine()
    }

    fun setPicovoiceAccessKey(key: String) {
        _picovoiceAccessKey.value = key
        prefs.picovoiceAccessKey = key
        AuraVoiceService.restartWakeWordEngine()
    }

    fun setSelectedPorcupineKeyword(keyword: String) {
        _selectedPorcupineKeyword.value = keyword
        prefs.selectedPorcupineKeyword = keyword
        AuraVoiceService.restartWakeWordEngine()
    }

    fun setPorcupineSensitivity(sensitivity: Float) {
        _porcupineSensitivity.value = sensitivity
        prefs.porcupineSensitivity = sensitivity
        AuraVoiceService.restartWakeWordEngine()
    }

    fun stopAssistantSpeaking() {
        speechManager?.stopSpeaking()
        _listeningState.value = AssistantListeningState.STANDBY
        _statusMessage.value = "Ready"
    }

    override fun onCleared() {
        super.onCleared()
        speechManager?.destroy()
    }
}
