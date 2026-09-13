package com.example.util

import android.util.Log

/**
 * High-precision performance logger that tracks end-to-end latency across all stages
 * of the voice assistant processing pipeline:
 * [Speech Captured] -> [Transcription Complete] -> [API Request Sent] -> [API Response Received] -> [TTS Starts Speaking]
 */
object PipelinePerfLogger {
    private const val TAG = "PERF_TIMELINE"

    private var tSpeechCaptured: Long = 0L
    private var tTranscriptionComplete: Long = 0L
    private var tApiRequestSent: Long = 0L
    private var tApiResponseReceived: Long = 0L
    private var tTtsStartSpeaking: Long = 0L

    @Synchronized
    fun markSpeechCaptured() {
        tSpeechCaptured = System.currentTimeMillis()
        tTranscriptionComplete = 0L
        tApiRequestSent = 0L
        tApiResponseReceived = 0L
        tTtsStartSpeaking = 0L
        Log.i(TAG, "[PERF_TIMELINE] ⏱️ STAGE 1: Speech Captured at ${tSpeechCaptured}ms")
    }

    @Synchronized
    fun markTranscriptionComplete(text: String) {
        tTranscriptionComplete = System.currentTimeMillis()
        val dt = if (tSpeechCaptured > 0) tTranscriptionComplete - tSpeechCaptured else 0L
        Log.i(TAG, "[PERF_TIMELINE] ⏱️ STAGE 2: Transcription Complete in ${dt}ms | Recognized text: '$text'")
    }

    @Synchronized
    fun markApiRequestSent() {
        tApiRequestSent = System.currentTimeMillis()
        val dt = if (tTranscriptionComplete > 0) tApiRequestSent - tTranscriptionComplete else 0L
        Log.i(TAG, "[PERF_TIMELINE] ⏱️ STAGE 3: API Request Sent in ${dt}ms")
    }

    @Synchronized
    fun markApiResponseReceived(source: String) {
        tApiResponseReceived = System.currentTimeMillis()
        val dt = if (tApiRequestSent > 0) tApiResponseReceived - tApiRequestSent else 0L
        Log.i(TAG, "[PERF_TIMELINE] ⏱️ STAGE 4: API Response Received ($source) in ${dt}ms")
    }

    @Synchronized
    fun markTtsStartSpeaking() {
        tTtsStartSpeaking = System.currentTimeMillis()
        val dtTotal = if (tSpeechCaptured > 0) tTtsStartSpeaking - tSpeechCaptured else 0L
        val dtTts = if (tApiResponseReceived > 0) tTtsStartSpeaking - tApiResponseReceived else 0L
        
        val d1 = if (tSpeechCaptured > 0 && tTranscriptionComplete > 0) tTranscriptionComplete - tSpeechCaptured else 0L
        val d2 = if (tTranscriptionComplete > 0 && tApiRequestSent > 0) tApiRequestSent - tTranscriptionComplete else 0L
        val d3 = if (tApiRequestSent > 0 && tApiResponseReceived > 0) tApiResponseReceived - tApiRequestSent else 0L
        val d4 = if (tApiResponseReceived > 0 && tTtsStartSpeaking > 0) tTtsStartSpeaking - tApiResponseReceived else 0L

        Log.i(TAG, "[PERF_TIMELINE] ⏱️ STAGE 5: TTS Starts Speaking in ${dtTts}ms | TOTAL PIPELINE LATENCY: ${dtTotal}ms")
        Log.i(TAG, "[PERF_TIMELINE_SUMMARY] Breakdown: [Captured->Transcribed: ${d1}ms] -> [Transcribed->ApiSend: ${d2}ms] -> [ApiSend->ApiRecv: ${d3}ms] -> [ApiRecv->TtsStart: ${d4}ms] | TOTAL=${dtTotal}ms")
    }
}
