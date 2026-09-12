package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class AuraSpeechManager(
    private val context: Context,
    private val onSpeechResult: (String) -> Unit,
    private val onRmsChangedCallback: (Float) -> Unit = {},
    private val onErrorCallback: ((Int) -> Unit)? = null
) {
    private val tag = "AuraSpeechManager"

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    init {
        initTts("en")
    }

    fun initTts(languageCode: String) {
        textToSpeech?.stop()
        textToSpeech?.shutdown()

        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                applyLanguage(languageCode)
            } else {
                Log.w(tag, "TTS init failed with status $status")
            }
        }

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })
    }

    fun applyLanguage(languageCode: String) {
        val targetLocale = when (languageCode.lowercase()) {
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

        val result = textToSpeech?.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(tag, "Locale $targetLocale not fully supported, falling back to US English")
            textToSpeech?.language = Locale.US
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) return
        stopListening()

        if (!isTtsInitialized || textToSpeech == null) {
            // Re-init if needed
            initTts("en")
        }

        _isSpeaking.value = true
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "AURA_UTTERANCE_${System.currentTimeMillis()}")
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "AURA_MSG")
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
    }

    fun startListening(languageCode: String = "en") {
        stopSpeaking()
        if (_isListening.value) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(tag, "Speech recognition not available on this device")
            _isListening.value = false
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _audioRms.value = normalized
                        onRmsChangedCallback(normalized)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        Log.d(tag, "SpeechRecognizer error: $error")
                        onErrorCallback?.invoke(error)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull()?.trim()
                        if (!spokenText.isNullOrBlank()) {
                            onSpeechResult(spokenText)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                val langTag = when (languageCode) {
                    "hi", "hinglish" -> "hi-IN"
                    "ta" -> "ta-IN"
                    "te" -> "te-IN"
                    "kn" -> "kn-IN"
                    "mr" -> "mr-IN"
                    "bn" -> "bn-IN"
                    else -> "en-US"
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start listening", e)
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            _isListening.value = false
            _audioRms.value = 0f
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            // Ignored
        }
    }
}
