package com.example.voice

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.preferences.AssistantPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CustomVoiceProfile(
    val id: String,
    val name: String,
    val audioPath: String,
    val durationMs: Long,
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)

class CustomVoiceCloneManager(
    private val context: Context,
    private val preferences: AssistantPreferences
) {
    private val tag = "CustomVoiceCloneMgr"
    private val voicesDir = File(context.filesDir, "custom_voices").apply { if (!exists()) mkdirs() }

    private val _isCustomVoiceEnabled = MutableStateFlow(preferences.isCustomVoiceEnabled)
    val isCustomVoiceEnabled: StateFlow<Boolean> = _isCustomVoiceEnabled.asStateFlow()

    private val _profiles = MutableStateFlow<List<CustomVoiceProfile>>(emptyList())
    val profiles: StateFlow<List<CustomVoiceProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<CustomVoiceProfile?>(null)
    val activeProfile: StateFlow<CustomVoiceProfile?> = _activeProfile.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlayingSample = MutableStateFlow(false)
    val isPlayingSample: StateFlow<Boolean> = _isPlayingSample.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var samplePlayer: MediaPlayer? = null

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        val jsonStr = preferences.customVoicesJson
        val list = mutableListOf<CustomVoiceProfile>()
        if (jsonStr.isNotBlank()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val audioPath = obj.getString("audioPath")
                    val durationMs = obj.optLong("durationMs", 0L)
                    val pitch = obj.optDouble("pitch", 1.0).toFloat()
                    val speed = obj.optDouble("speed", 1.0).toFloat()
                    val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                    if (File(audioPath).exists()) {
                        list.add(CustomVoiceProfile(id, name, audioPath, durationMs, pitch, speed, createdAt))
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error parsing custom voice profiles JSON: ${e.message}")
            }
        }
        _profiles.value = list

        val activeId = preferences.activeCustomVoiceId
        val active = list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
        _activeProfile.value = active
        if (active != null && active.id != activeId) {
            preferences.activeCustomVoiceId = active.id
        }
    }

    private fun saveProfiles(list: List<CustomVoiceProfile>) {
        _profiles.value = list
        try {
            val array = JSONArray()
            for (p in list) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("audioPath", p.audioPath)
                    put("durationMs", p.durationMs)
                    put("pitch", p.pitch.toDouble())
                    put("speed", p.speed.toDouble())
                    put("createdAt", p.createdAt)
                }
                array.put(obj)
            }
            preferences.customVoicesJson = array.toString()
        } catch (e: Exception) {
            Log.e(tag, "Error saving custom voice profiles JSON: ${e.message}")
        }
    }

    fun toggleCustomVoice(enabled: Boolean) {
        preferences.isCustomVoiceEnabled = enabled
        _isCustomVoiceEnabled.value = enabled
    }

    fun selectProfile(id: String) {
        val target = _profiles.value.firstOrNull { it.id == id } ?: return
        preferences.activeCustomVoiceId = target.id
        _activeProfile.value = target
        preferences.speechPitch = target.pitch
        preferences.speechRate = target.speed
    }

    fun importVoiceSampleFromUri(uri: Uri, voiceName: String): CustomVoiceProfile? {
        try {
            val id = UUID.randomUUID().toString().take(8)
            val fileName = "custom_voice_$id.mp3"
            val destFile = File(voicesDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val durationMs = getAudioDuration(destFile.absolutePath)
            val name = if (voiceName.isBlank()) "Voice Sample ${profiles.value.size + 1}" else voiceName
            val profile = CustomVoiceProfile(
                id = id,
                name = name,
                audioPath = destFile.absolutePath,
                durationMs = durationMs,
                pitch = 1.0f,
                speed = 1.0f
            )

            val updated = _profiles.value + profile
            saveProfiles(updated)
            selectProfile(profile.id)
            toggleCustomVoice(true)
            Log.i(tag, "Successfully imported custom voice sample '${profile.name}' (${durationMs}ms)")
            return profile
        } catch (e: Exception) {
            Log.e(tag, "Failed to import custom voice sample: ${e.message}", e)
            return null
        }
    }

    fun startRecordingLiveVoiceSample(): File? {
        try {
            stopSamplePlayback()
            val id = UUID.randomUUID().toString().take(8)
            val file = File(voicesDir, "recorded_voice_$id.m4a")
            currentRecordingFile = file

            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            _isRecording.value = true
            Log.i(tag, "Started live voice sample recording to ${file.name}")
            return file
        } catch (e: Exception) {
            Log.e(tag, "Error starting voice recording: ${e.message}", e)
            _isRecording.value = false
            return null
        }
    }

    fun stopRecordingLiveVoiceSample(voiceName: String): CustomVoiceProfile? {
        if (!_isRecording.value) return null
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            _isRecording.value = false

            val file = currentRecordingFile ?: return null
            if (!file.exists() || file.length() == 0L) return null

            val durationMs = getAudioDuration(file.absolutePath)
            val id = UUID.randomUUID().toString().take(8)
            val name = if (voiceName.isBlank()) "Live Record ${profiles.value.size + 1}" else voiceName
            val profile = CustomVoiceProfile(
                id = id,
                name = name,
                audioPath = file.absolutePath,
                durationMs = durationMs,
                pitch = 1.05f,
                speed = 1.0f
            )

            val updated = _profiles.value + profile
            saveProfiles(updated)
            selectProfile(profile.id)
            toggleCustomVoice(true)
            Log.i(tag, "Stopped recording and created custom voice profile '${profile.name}'")
            return profile
        } catch (e: Exception) {
            Log.e(tag, "Error stopping voice recording: ${e.message}", e)
            _isRecording.value = false
            return null
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        _isRecording.value = false
        currentRecordingFile?.delete()
        currentRecordingFile = null
    }

    fun playProfileSample(profile: CustomVoiceProfile, onComplete: (() -> Unit)? = null) {
        stopSamplePlayback()
        val file = File(profile.audioPath)
        if (!file.exists()) return

        try {
            val player = MediaPlayer().apply {
                setDataSource(profile.audioPath)
                prepare()
                setOnCompletionListener {
                    _isPlayingSample.value = false
                    onComplete?.invoke()
                }
                start()
            }
            samplePlayer = player
            _isPlayingSample.value = true
        } catch (e: Exception) {
            Log.e(tag, "Error playing custom voice sample: ${e.message}")
            _isPlayingSample.value = false
        }
    }

    fun stopSamplePlayback() {
        try {
            samplePlayer?.stop()
            samplePlayer?.release()
        } catch (_: Exception) {}
        samplePlayer = null
        _isPlayingSample.value = false
    }

    fun deleteProfile(id: String) {
        val target = _profiles.value.firstOrNull { it.id == id } ?: return
        try {
            File(target.audioPath).delete()
        } catch (_: Exception) {}

        val updated = _profiles.value.filter { it.id != id }
        saveProfiles(updated)

        if (_activeProfile.value?.id == id) {
            val newActive = updated.firstOrNull()
            _activeProfile.value = newActive
            preferences.activeCustomVoiceId = newActive?.id ?: ""
            if (newActive == null) {
                toggleCustomVoice(false)
            }
        }
    }

    fun updateProfileTuning(id: String, pitch: Float, speed: Float) {
        val list = _profiles.value.map { p ->
            if (p.id == id) {
                p.copy(pitch = pitch, speed = speed)
            } else p
        }
        saveProfiles(list)
        if (_activeProfile.value?.id == id) {
            _activeProfile.value = _activeProfile.value?.copy(pitch = pitch, speed = speed)
            preferences.speechPitch = pitch
            preferences.speechRate = speed
        }
    }

    private fun getAudioDuration(path: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLongOrNull() ?: 3000L
        } catch (_: Exception) {
            3000L
        }
    }

    fun destroy() {
        cancelRecording()
        stopSamplePlayback()
    }
}
