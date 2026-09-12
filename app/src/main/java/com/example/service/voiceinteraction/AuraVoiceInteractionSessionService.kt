package com.example.service.voiceinteraction

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Service factory that instantiates AuraVoiceInteractionSession when assist requests are triggered.
 */
class AuraVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return AuraVoiceInteractionSession(this)
    }
}
