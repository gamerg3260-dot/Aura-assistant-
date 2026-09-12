package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * High-quality Google Cloud Text-to-Speech synthesis service.
 * Synthesizes natural female speech using Neural2/WaveNet voices when
 * high-quality on-device TTS voices are unavailable.
 */
class GoogleCloudTtsService(private val context: Context) {
    private val tag = "GoogleCloudTtsService"

    private var mediaPlayer: MediaPlayer? = null
    private var visualizerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    suspend fun synthesizeAndPlay(
        text: String,
        languageCode: String,
        apiKey: String,
        onStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onRmsUpdate: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (text.isBlank() || apiKey.isBlank()) {
            Log.w(tag, "[CLOUD_TTS] Text or API key is blank. Skipping Cloud TTS synthesis.")
            return@withContext false
        }

        stop()

        val (langCode, primaryVoice) = selectCloudVoice(languageCode)
        Log.i(tag, "[CLOUD_TTS] Initiating Neural2/WaveNet synthesis with voice '$primaryVoice' ($langCode) for text: '${text.take(50)}...'")

        val audioBase64 = callTtsApi(text, langCode, primaryVoice, apiKey)
            ?: callTtsApi(text, langCode, getFallbackVoice(langCode), apiKey)

        if (audioBase64.isNullOrBlank()) {
            Log.w(tag, "[CLOUD_TTS] Cloud TTS API returned no audio content.")
            return@withContext false
        }

        try {
            val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "aura_cloud_tts_temp.mp3")
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
            }

            withContext(Dispatchers.Main) {
                playAudioFile(tempFile, onStart, onComplete, onRmsUpdate)
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(tag, "[CLOUD_TTS] Error playing synthesized audio file: ${e.message}", e)
            return@withContext false
        }
    }

    private fun callTtsApi(
        text: String,
        languageCode: String,
        voiceName: String,
        apiKey: String
    ): String? {
        try {
            val url = "https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey"

            val jsonBody = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("text", text)
                })
                put("voice", JSONObject().apply {
                    put("languageCode", languageCode)
                    put("name", voiceName)
                    put("ssmlGender", "FEMALE")
                })
                put("audioConfig", JSONObject().apply {
                    put("audioEncoding", "MP3")
                    put("speakingRate", 1.0)
                    put("pitch", 0.0)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""

            if (response.isSuccessful && responseBodyString.isNotBlank()) {
                val jsonResponse = JSONObject(responseBodyString)
                val audioContent = jsonResponse.optString("audioContent")
                if (audioContent.isNotBlank()) {
                    Log.i(tag, "[CLOUD_TTS] Successfully synthesized ${audioContent.length} chars of MP3 base64 audio with voice '$voiceName'")
                    return audioContent
                }
            } else {
                Log.w(tag, "[CLOUD_TTS] API error HTTP ${response.code}: $responseBodyString")
            }
        } catch (e: Exception) {
            Log.e(tag, "[CLOUD_TTS] Network request failed for voice '$voiceName': ${e.message}")
        }
        return null
    }

    private fun playAudioFile(
        file: File,
        onStart: (() -> Unit)?,
        onComplete: (() -> Unit)?,
        onRmsUpdate: ((Float) -> Unit)?
    ) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    onStart?.invoke()
                    startRmsPulse(onRmsUpdate)
                    Log.i(tag, "[CLOUD_TTS] Audio playback started.")
                }
                setOnCompletionListener { mp ->
                    stopRmsPulse(onRmsUpdate)
                    mp.release()
                    mediaPlayer = null
                    file.delete()
                    onComplete?.invoke()
                    Log.i(tag, "[CLOUD_TTS] Audio playback completed.")
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(tag, "[CLOUD_TTS] MediaPlayer error: what=$what, extra=$extra")
                    stopRmsPulse(onRmsUpdate)
                    mp.release()
                    mediaPlayer = null
                    file.delete()
                    onComplete?.invoke()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(tag, "[CLOUD_TTS] Failed to initialize MediaPlayer: ${e.message}", e)
            file.delete()
            onComplete?.invoke()
        }
    }

    private fun startRmsPulse(onRmsUpdate: ((Float) -> Unit)?) {
        visualizerJob?.cancel()
        visualizerJob = scope.launch {
            var step = 0
            while (isActive && isPlaying()) {
                step = (step + 1) % 10
                val simulatedRms = 0.35f + (step % 4) * 0.15f + (Math.random().toFloat() * 0.1f)
                onRmsUpdate?.invoke(simulatedRms.coerceIn(0.1f, 0.9f))
                delay(80)
            }
            onRmsUpdate?.invoke(0f)
        }
    }

    private fun stopRmsPulse(onRmsUpdate: ((Float) -> Unit)?) {
        visualizerJob?.cancel()
        onRmsUpdate?.invoke(0f)
    }

    fun stop() {
        try {
            visualizerJob?.cancel()
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(tag, "[CLOUD_TTS] Error stopping MediaPlayer: ${e.message}")
        }
    }

    private fun selectCloudVoice(languageCode: String): Pair<String, String> {
        return when (languageCode.lowercase()) {
            "hi", "hinglish" -> Pair("hi-IN", "hi-IN-Neural2-A")
            "en", "en-in" -> Pair("en-IN", "en-IN-Neural2-A")
            "ta" -> Pair("ta-IN", "ta-IN-Wavenet-A")
            "te" -> Pair("te-IN", "te-IN-Standard-A")
            "kn" -> Pair("kn-IN", "kn-IN-Standard-A")
            "mr" -> Pair("mr-IN", "mr-IN-Wavenet-A")
            "bn" -> Pair("bn-IN", "bn-IN-Wavenet-A")
            "gu" -> Pair("gu-IN", "gu-IN-Standard-A")
            "pa" -> Pair("pa-IN", "pa-IN-Standard-A")
            "ml" -> Pair("ml-IN", "ml-IN-Standard-A")
            "es" -> Pair("es-ES", "es-ES-Neural2-A")
            "fr" -> Pair("fr-FR", "fr-FR-Neural2-A")
            "de" -> Pair("de-DE", "de-DE-Neural2-F")
            "ja" -> Pair("ja-JP", "ja-JP-Neural2-B")
            else -> Pair("en-US", "en-US-Neural2-F")
        }
    }

    private fun getFallbackVoice(languageCode: String): String {
        return when (languageCode.lowercase()) {
            "hi", "hinglish" -> "hi-IN-Wavenet-A"
            "en", "en-in" -> "en-IN-Wavenet-A"
            else -> "en-US-Wavenet-F"
        }
    }
}
