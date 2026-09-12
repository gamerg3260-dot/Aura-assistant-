package com.example.voice

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback

/**
 * High-performance, low-power on-device Wake Word Detector powered by the Picovoice Porcupine SDK.
 *
 * Runs continuous audio analysis with hardware acceleration and triggers instant callbacks
 * with near-zero battery consumption when the chosen keyword is spotted.
 */
class PorcupineWakeWordDetector(
    private val context: Context,
    private val accessKey: String,
    private val onWakeWordDetected: (keywordIndex: Int, keywordName: String) -> Unit,
    private val onError: (Exception) -> Unit = {}
) {
    companion object {
        const val DEFAULT_KEYWORD = "JARVIS"
        const val DEFAULT_SENSITIVITY = 0.6f

        val AVAILABLE_KEYWORDS: List<String> = listOf(
            "JARVIS",
            "PORCUPINE",
            "COMPUTER",
            "BUMBLEBEE",
            "ALEXA",
            "HEY_GOOGLE",
            "HEY_SIRI",
            "OK_GOOGLE",
            "PICOVOICE",
            "TERMINATOR",
            "GRAPEFRUIT",
            "GRASSHOPPER"
        )

        fun parseKeyword(name: String): Porcupine.BuiltInKeyword {
            return try {
                Porcupine.BuiltInKeyword.valueOf(name.uppercase().trim())
            } catch (e: Exception) {
                Porcupine.BuiltInKeyword.JARVIS
            }
        }
    }

    private val tag = "PorcupineDetector"
    private var porcupineManager: PorcupineManager? = null
    var isListening: Boolean = false
        private set

    var currentKeywordName: String = DEFAULT_KEYWORD
        private set

    var currentSensitivity: Float = DEFAULT_SENSITIVITY
        private set

    var lastErrorMessage: String? = null
        private set

    /**
     * Initializes the PorcupineManager with accessKey, keyword, and sensitivity.
     */
    fun initialize(
        keywordName: String = DEFAULT_KEYWORD,
        sensitivity: Float = DEFAULT_SENSITIVITY
    ): Boolean {
        if (accessKey.isBlank()) {
            lastErrorMessage = "AccessKey is blank. Please configure Picovoice AccessKey in Settings."
            Log.w(tag, lastErrorMessage!!)
            return false
        }

        currentKeywordName = keywordName
        currentSensitivity = sensitivity.coerceIn(0.1f, 1.0f)
        val builtInKeyword = parseKeyword(keywordName)

        return try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeyword(builtInKeyword)
                .setSensitivity(currentSensitivity)
                .build(context, PorcupineManagerCallback { keywordIndex ->
                    Log.i(tag, "Porcupine spotted wake-word: ${builtInKeyword.name} (index=$keywordIndex)")
                    onWakeWordDetected(keywordIndex, builtInKeyword.name)
                })
            lastErrorMessage = null
            Log.i(tag, "PorcupineManager initialized successfully (Keyword=${builtInKeyword.name}, Sensitivity=$currentSensitivity)")
            true
        } catch (e: PorcupineException) {
            lastErrorMessage = "Porcupine error: ${e.message}"
            Log.e(tag, "Failed to initialize PorcupineManager: ${e.message}", e)
            onError(e)
            false
        } catch (e: Throwable) {
            lastErrorMessage = "Initialization error: ${e.message}"
            Log.e(tag, "Unexpected error initializing Porcupine: ${e.message}", e)
            onError(Exception(e.message ?: "Porcupine initialization error", e))
            false
        }
    }

    /**
     * Starts listening for the wake word.
     */
    fun start(): Boolean {
        if (porcupineManager == null) {
            Log.w(tag, "Cannot start: PorcupineManager is not initialized.")
            return false
        }
        return try {
            porcupineManager?.start()
            isListening = true
            Log.d(tag, "PorcupineManager listening started.")
            true
        } catch (e: Throwable) {
            lastErrorMessage = "Failed to start listening: ${e.message}"
            Log.e(tag, lastErrorMessage!!, e)
            isListening = false
            onError(Exception(e.message ?: "Failed to start Porcupine listening", e))
            false
        }
    }

    /**
     * Stops listening.
     */
    fun stop() {
        try {
            porcupineManager?.stop()
            isListening = false
            Log.d(tag, "PorcupineManager listening stopped.")
        } catch (e: Throwable) {
            Log.e(tag, "Error stopping PorcupineManager: ${e.message}", e)
        }
    }

    /**
     * Releases hardware resources.
     */
    fun release() {
        stop()
        try {
            porcupineManager?.delete()
            porcupineManager = null
            Log.d(tag, "PorcupineManager resources released.")
        } catch (e: Throwable) {
            Log.e(tag, "Error releasing PorcupineManager: ${e.message}", e)
        }
    }
}
