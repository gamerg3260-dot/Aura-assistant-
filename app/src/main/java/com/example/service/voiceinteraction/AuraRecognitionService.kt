package com.example.service.voiceinteraction

import android.content.Intent
import android.speech.RecognitionService

/**
 * Speech RecognitionService referenced by interaction_service.xml
 * enabling system-level voice interaction compliance.
 */
class AuraRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Recognition callback hook
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}
