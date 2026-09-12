package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Manager class responsible for handling and speaking Gemini LLM responses using Android TextToSpeech
 * with an optimized natural-sounding female voice configuration, markdown/code cleaning,
 * and visualizer RMS pulsing.
 */
class GeminiSpokenOutputManager(
    private val context: Context,
    private val defaultLanguageCode: String = "en"
) {
    private val tag = "GeminiSpokenOutput"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var activeCompletionCallback: (() -> Unit)? = null
    private var activeStartCallback: (() -> Unit)? = null
    private var visualizerJob: Job? = null

    // Voice Pitch & Rate configuration for natural female timbre
    private var targetPitch: Float = 1.14f
    private var targetSpeed: Float = 1.02f
    private var currentLanguageCode: String = defaultLanguageCode

    // Observability StateFlows
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentSpokenText = MutableStateFlow("")
    val currentSpokenText: StateFlow<String> = _currentSpokenText.asStateFlow()

    private val _activeVoiceName = MutableStateFlow("Default Female Voice")
    val activeVoiceName: StateFlow<String> = _activeVoiceName.asStateFlow()

    private val _speechRms = MutableStateFlow(0f)
    val speechRms: StateFlow<Float> = _speechRms.asStateFlow()

    private val _speechProgress = MutableStateFlow(0f)
    val speechProgress: StateFlow<Float> = _speechProgress.asStateFlow()

    init {
        initializeTtsEngine(defaultLanguageCode)
    }

    /**
     * Initializes the Android TextToSpeech engine with Assistant AudioAttributes and Female Voice settings.
     */
    fun initializeTtsEngine(languageCode: String = currentLanguageCode) {
        currentLanguageCode = languageCode
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.w(tag, "Error shutting down previous TTS instance: ${e.message}")
        }

        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                configureAssistantAudioAttributes()
                applyLanguageAndFemaleVoice(currentLanguageCode)
                setupUtteranceListener()
                Log.d(tag, "Android TTS engine initialized successfully.")
            } else {
                Log.e(tag, "TTS engine initialization failed with status code: $status")
                isInitialized = false
            }
        }
    }

    /**
     * Configures Assistant AudioAttributes for crystal-clear voice output on Android.
     */
    private fun configureAssistantAudioAttributes() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .build()
            textToSpeech?.setAudioAttributes(audioAttributes)
        } catch (e: Exception) {
            Log.w(tag, "Could not set AudioAttributes: ${e.message}")
        }
    }

    fun setPitch(pitch: Float) {
        targetPitch = pitch.coerceIn(0.5f, 2.0f)
        textToSpeech?.setPitch(targetPitch)
    }

    fun setSpeechRate(rate: Float) {
        targetSpeed = rate.coerceIn(0.5f, 2.0f)
        textToSpeech?.setSpeechRate(targetSpeed)
    }

    val currentPitch: Float get() = targetPitch
    val currentSpeed: Float get() = targetSpeed

    /**
     * Configures language locale and selects a high-quality natural female voice.
     */
    fun applyLanguageAndFemaleVoice(languageCode: String, pitch: Float = targetPitch, speed: Float = targetSpeed) {
        targetPitch = pitch
        targetSpeed = speed
        currentLanguageCode = languageCode

        val targetLocale = mapLanguageCodeToLocale(languageCode)
        val langResult = textToSpeech?.setLanguage(targetLocale)

        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(tag, "Locale $targetLocale not fully supported, falling back to Locale.US")
            textToSpeech?.language = Locale.US
        }

        textToSpeech?.setPitch(targetPitch)
        textToSpeech?.setSpeechRate(targetSpeed)

        findAndApplyBestFemaleVoice(targetLocale)
    }

    /**
     * Searches TTS voices for natural neural/female voices matching the target locale.
     */
    private fun findAndApplyBestFemaleVoice(targetLocale: Locale) {
        val tts = textToSpeech ?: return
        try {
            val voices = tts.voices
            if (!voices.isNullOrEmpty()) {
                // Priority 1: High quality, non-network-dependent female/wavenet voice matching target locale
                val matchingLocaleVoices = voices.filter {
                    it.locale.language == targetLocale.language
                }

                val candidateVoice = matchingLocaleVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase()
                    !voice.isNetworkConnectionRequired && isFemaleVoiceIdentifier(name)
                } ?: matchingLocaleVoices.firstOrNull { voice ->
                    isFemaleVoiceIdentifier(voice.name.lowercase())
                } ?: voices.firstOrNull { voice ->
                    !voice.isNetworkConnectionRequired && isFemaleVoiceIdentifier(voice.name.lowercase())
                } ?: matchingLocaleVoices.firstOrNull { voice ->
                    !voice.isNetworkConnectionRequired && voice.quality >= Voice.QUALITY_NORMAL
                }

                if (candidateVoice != null) {
                    tts.voice = candidateVoice
                    _activeVoiceName.value = candidateVoice.name
                    Log.d(tag, "Selected female voice: ${candidateVoice.name} (Locale: ${candidateVoice.locale})")
                } else {
                    _activeVoiceName.value = "Default Female Engine (${targetLocale.displayLanguage})"
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Voice query not supported on this TTS engine: ${e.message}")
        }
    }

    private fun isFemaleVoiceIdentifier(name: String): Boolean {
        return name.contains("female") ||
                name.contains("woman") ||
                name.contains("sfg") ||
                name.contains("en-us-x-sfg") ||
                name.contains("hi-in-x-hie") ||
                name.contains("hi-in-x-hic") ||
                name.contains("neural2-f") ||
                name.contains("wavenet-c") ||
                name.contains("wavenet-e") ||
                name.contains("wavenet-f") ||
                name.contains("female-1")
    }

    private fun setupUtteranceListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _speechProgress.value = 0.1f
                startVisualizerPulse()
                activeStartCallback?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _speechProgress.value = 1.0f
                stopVisualizerPulse()
                try {
                    activeCompletionCallback?.invoke()
                } catch (e: Exception) {
                    Log.e(tag, "Error in TTS completion callback: ${e.message}")
                }
                activeCompletionCallback = null
                activeStartCallback = null
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                stopVisualizerPulse()
                try {
                    activeCompletionCallback?.invoke()
                } catch (e: Exception) {
                    // Ignored
                }
                activeCompletionCallback = null
                activeStartCallback = null
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val totalLength = _currentSpokenText.value.length
                if (totalLength > 0) {
                    _speechProgress.value = (end.toFloat() / totalLength).coerceIn(0f, 1f)
                }
            }
        })
    }

    /**
     * Speaks the Gemini LLM response out loud after sanitizing markdown, code blocks, and symbols.
     */
    fun speakGeminiResponse(
        rawLlmResponse: String,
        onStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val sanitizedText = sanitizeForSpeech(rawLlmResponse)
        if (sanitizedText.isBlank()) {
            onComplete?.invoke()
            return
        }

        stop()

        activeStartCallback = onStart
        activeCompletionCallback = onComplete
        _currentSpokenText.value = sanitizedText

        if (!isInitialized || textToSpeech == null) {
            initializeTtsEngine(currentLanguageCode)
        }

        val utteranceId = "GEMINI_SPEECH_${UUID.randomUUID()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        _isSpeaking.value = true
        val result = textToSpeech?.speak(
            sanitizedText,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        )

        if (result != TextToSpeech.SUCCESS) {
            Log.e(tag, "speak() failed with result: $result")
            _isSpeaking.value = false
            stopVisualizerPulse()
            onComplete?.invoke()
        }
    }

    /**
     * Sanitizes raw Gemini LLM response text for natural, fluent spoken output.
     * Strips Markdown formatting (bold, italics, headers), bullets, code blocks, URLs, and emojis.
     */
    fun sanitizeForSpeech(rawText: String): String {
        if (rawText.isBlank()) return ""

        var cleaned = rawText

        // 1. Remove Markdown code blocks ``` ... ```
        cleaned = cleaned.replace(Regex("```[\\s\\S]*?```"), " Here is the code snippet. ")

        // 2. Remove inline code `...`
        cleaned = cleaned.replace(Regex("`([^`]+)`"), "$1")

        // 3. Remove Markdown URLs [text](url) -> "text"
        cleaned = cleaned.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")

        // 4. Remove raw URLs
        cleaned = cleaned.replace(Regex("https?://\\S+"), " web link ")

        // 5. Remove Markdown headers (# Header)
        cleaned = cleaned.replace(Regex("(?m)^#{1,6}\\s+"), "")

        // 6. Remove bold/italics symbols (*, **, _, __, ~~)
        cleaned = cleaned.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        cleaned = cleaned.replace(Regex("\\*([^*]+)\\*"), "$1")
        cleaned = cleaned.replace(Regex("__([^_]+)__"), "$1")
        cleaned = cleaned.replace(Regex("_([^_]+)_"), "$1")
        cleaned = cleaned.replace(Regex("~~([^~]+)~~"), "$1")

        // 7. Clean bullet list markers (*, -, •, 1., 2.) at start of line
        cleaned = cleaned.replace(Regex("(?m)^[\\s]*[\\*\\-\\•]\\s+"), "")
        cleaned = cleaned.replace(Regex("(?m)^[\\s]*\\d+\\.\\s+"), "")

        // 8. Replace technical symbols with spoken equivalents or spaces
        cleaned = cleaned.replace("&", " and ")
        cleaned = cleaned.replace("@", " at ")
        cleaned = cleaned.replace("/", " / ")
        cleaned = cleaned.replace(Regex("[<>{}\\[\\]|~^\\\\]"), " ")

        // 9. Remove emojis (Unicode symbols)
        cleaned = cleaned.replace(
            Regex("[\\p{So}\\p{Cn}\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}\\x{1F1E0}-\\x{1F1FF}]"),
            ""
        )

        // 10. Normalize multiple spaces and extra linebreaks
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        return cleaned
    }

    /**
     * Synthesizes audio RMS waveform pulses while TTS is speaking to drive UI orb/visualizer animations.
     */
    private fun startVisualizerPulse() {
        visualizerJob?.cancel()
        visualizerJob = scope.launch {
            var step = 0
            while (isActive && _isSpeaking.value) {
                step = (step + 1) % 10
                val simulatedRms = 0.35f + (step % 4) * 0.15f + (Math.random().toFloat() * 0.1f)
                _speechRms.value = simulatedRms.coerceIn(0.1f, 0.9f)
                delay(80)
            }
            _speechRms.value = 0f
        }
    }

    private fun stopVisualizerPulse() {
        visualizerJob?.cancel()
        _speechRms.value = 0f
    }

    /**
     * Stops current speech output immediately.
     */
    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping TTS: ${e.message}")
        }
        _isSpeaking.value = false
        stopVisualizerPulse()
        activeCompletionCallback = null
        activeStartCallback = null
    }

    /**
     * Maps language code to java.util.Locale
     */
    private fun mapLanguageCodeToLocale(languageCode: String): Locale {
        return when (languageCode.lowercase()) {
            "hi" -> Locale("hi", "IN")
            "hinglish" -> Locale("hi", "IN")
            "ta" -> Locale("ta", "IN")
            "te" -> Locale("te", "IN")
            "kn" -> Locale("kn", "IN")
            "mr" -> Locale("mr", "IN")
            "bn" -> Locale("bn", "IN")
            "gu" -> Locale("gu", "IN")
            "pa" -> Locale("pa", "IN")
            "ml" -> Locale("ml", "IN")
            "es" -> Locale("es", "ES")
            "fr" -> Locale("fr", "FR")
            "de" -> Locale("de", "DE")
            "ja" -> Locale.JAPANESE
            else -> Locale.US
        }
    }

    /**
     * Releases TTS resources when destroyed.
     */
    fun destroy() {
        stop()
        try {
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
        } catch (e: Exception) {
            Log.w(tag, "Error shutting down TTS engine: ${e.message}")
        }
    }
}
