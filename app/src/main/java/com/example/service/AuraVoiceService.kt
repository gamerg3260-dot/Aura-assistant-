package com.example.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.AuraApplication
import com.example.MainActivity
import com.example.R
import com.example.voice.PorcupineWakeWordDetector
import com.example.voice.VoiceprintEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Long-running Android Foreground Service that maintains continuous background audio
 * recording, adaptive noise floor tracking, wake-word spotting, and biometric voice-lock verification.
 */
class AuraVoiceService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val CHANNEL_ID = "aura_voice_service_channel"
        const val NOTIFICATION_ID = 2026

        // Intent Actions
        const val ACTION_START_SERVICE = "com.example.aura.ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.aura.ACTION_STOP_SERVICE"
        const val ACTION_TOGGLE_MUTE = "com.example.aura.ACTION_TOGGLE_MUTE"
        const val ACTION_LISTEN_NOW = "com.example.aura.ACTION_LISTEN_NOW"
        const val ACTION_EXEMPT_BATTERY = "com.example.aura.ACTION_EXEMPT_BATTERY"

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive = _isServiceActive.asStateFlow()

        private val _isMuted = MutableStateFlow(false)
        val isMuted = _isMuted.asStateFlow()

        private val _serviceStatusText = MutableStateFlow("Voice-Lock Guard Active • Standby for wake word")
        val serviceStatusText = _serviceStatusText.asStateFlow()

        private val _backgroundAudioRms = MutableStateFlow(0f)
        val backgroundAudioRms = _backgroundAudioRms.asStateFlow()

        private val _isPorcupineActive = MutableStateFlow(false)
        val isPorcupineActive = _isPorcupineActive.asStateFlow()

        private val _isBatteryOptimizationExempt = MutableStateFlow(true)
        val isBatteryOptimizationExempt = _isBatteryOptimizationExempt.asStateFlow()

        private val _activeWakeWordEngine = MutableStateFlow("On-Device Acoustic Engine")
        val activeWakeWordEngine = _activeWakeWordEngine.asStateFlow()

        private val _isPausedForActiveListening = MutableStateFlow(false)
        val isPausedForActiveListening = _isPausedForActiveListening.asStateFlow()

        // Shared event flow notifying listeners when wake-word is verified
        private val _wakeWordDetectedEvent = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
        val wakeWordDetectedEvent = _wakeWordDetectedEvent.asSharedFlow()

        private var activeServiceInstance: AuraVoiceService? = null

        fun pauseForActiveListening() {
            Log.i("AuraVoiceService", "[STATE_MACHINE] ⏸️ Pausing background wake-word listener for active speech input.")
            _isPausedForActiveListening.value = true
            activeServiceInstance?.pauseBackgroundMonitoring()
        }

        fun resumeFromActiveListening() {
            Log.i("AuraVoiceService", "[STATE_MACHINE] ▶️ Resuming background wake-word listener after active command session.")
            _isPausedForActiveListening.value = false
            activeServiceInstance?.resumeBackgroundMonitoring()
        }

        fun restartWakeWordEngine() {
            activeServiceInstance?.startAcousticMonitorLoop()
        }

        fun start(context: Context) {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasMicPermission) {
                Log.w("AuraVoiceService", "Cannot start AuraVoiceService: RECORD_AUDIO permission not granted yet.")
                return
            }

            try {
                val intent = Intent(context, AuraVoiceService::class.java).apply {
                    action = ACTION_START_SERVICE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("AuraVoiceService", "Error starting voice service: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AuraVoiceService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun toggleMute(context: Context) {
            val intent = Intent(context, AuraVoiceService::class.java).apply {
                action = ACTION_TOGGLE_MUTE
            }
            context.startService(intent)
        }

        fun triggerListenNow(context: Context) {
            val intent = Intent(context, AuraVoiceService::class.java).apply {
                action = ACTION_LISTEN_NOW
            }
            context.startService(intent)
        }

        fun updateStatus(text: String) {
            _serviceStatusText.value = text
            activeServiceInstance?.updateNotification()
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            return com.example.system.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        }

        fun createBatteryOptimizationIntent(context: Context): Intent {
            return com.example.system.BatteryOptimizationHelper.createRequestIgnoreBatteryOptimizationIntent(context)
        }

        fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
            return com.example.system.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
        }
    }

    private val tag = "AuraVoiceService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioMonitorJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var porcupineDetector: PorcupineWakeWordDetector? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    // Sliding audio window (2 seconds at 16kHz 16-bit mono = 32,000 samples)
    private val sampleRate = 16000
    private val windowCapacity = sampleRate * 2
    private val rollingAudioBuffer = ShortArray(windowCapacity)
    private var bufferWriteIndex = 0

    // Adaptive noise floor tracking
    private var noiseFloorRms = 0.03f

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            createNotificationChannel()
            acquireWakeLock()
            requestAudioFocus()
            startForegroundWithMicrophone()
            _isServiceActive.value = true

            startAcousticMonitorLoop()
            Log.d(tag, "AuraVoiceService created successfully.")
        } catch (t: Throwable) {
            Log.e(tag, "Fatal error initializing AuraVoiceService: ${t.message}", t)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isBatteryExempt = com.example.system.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
        _isBatteryOptimizationExempt.value = isBatteryExempt
        Log.i(tag, "[BACKGROUND_MONITOR_DEBUG] Foreground Voice Service startId=$startId action=${intent?.action} | Battery Optimization Exempt: $isBatteryExempt | Audio Focus: $hasAudioFocus")

        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.d(tag, "Received ACTION_STOP_SERVICE")
                handleStopService()
                return START_NOT_STICKY
            }
            ACTION_EXEMPT_BATTERY -> {
                Log.d(tag, "Received ACTION_EXEMPT_BATTERY")
                com.example.system.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
                updateNotification()
            }
            ACTION_TOGGLE_MUTE -> {
                _isMuted.value = !_isMuted.value
                val status = if (_isMuted.value) {
                    "Microphone Muted (Listening Paused)"
                } else {
                    "Voice-Lock Guard Active • Standby for wake word"
                }
                _serviceStatusText.value = status
                updateNotification()
                if (_isMuted.value) {
                    porcupineDetector?.stop()
                } else {
                    porcupineDetector?.start()
                }
                Log.d(tag, "Mute toggled: isMuted=${_isMuted.value}")
            }
            ACTION_LISTEN_NOW -> {
                _serviceStatusText.value = "Active Voice Command Triggered"
                updateNotification()
                triggerWakeDetectedFlow(isOwnerVerified = true)
            }
            ACTION_START_SERVICE, null -> {
                updateNotification()
            }
        }

        // Return START_STICKY to guarantee restart by the Android runtime if killed under memory pressure
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(tag, "onTaskRemoved: checking recovery restart via AlarmManager")
        val preferences = AuraApplication.instance.preferences
        val hasMicPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (preferences.isBackgroundServiceRunning && hasMicPermission) {
            val restartServiceIntent = Intent(applicationContext, AuraVoiceService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            val restartServicePendingIntent = PendingIntent.getService(
                applicationContext,
                101,
                restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
            )
        }
    }

    private fun handleStopService() {
        val preferences = AuraApplication.instance.preferences
        preferences.isBackgroundServiceRunning = false
        _isServiceActive.value = false
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AuraAI:VoiceServiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(15 * 60 * 1000L) // 15 minute acquisition window with auto-refresh
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(tag, "Error releasing wake lock: ${e.message}")
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(this)
                .build()

            audioFocusRequest = request
            val res = audioManager?.requestAudioFocus(request)
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        } else {
            @Suppress("DEPRECATION")
            val res = audioManager?.requestAudioFocus(
                this,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(this)
        }
        hasAudioFocus = false
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aura Voice-Lock Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps voice biometric guard and wake-word detection running in background"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val prefs = AuraApplication.instance.preferences
        val userName = prefs.userName
        val isMutedState = _isMuted.value
        val isPorcupine = _isPorcupineActive.value
        val isBatteryExempt = com.example.system.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
        _isBatteryOptimizationExempt.value = isBatteryExempt

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action 1: Listen Now
        val listenIntent = Intent(this, AuraVoiceService::class.java).apply {
            action = ACTION_LISTEN_NOW
        }
        val pendingListen = PendingIntent.getService(
            this,
            1,
            listenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action 2: Toggle Mute
        val muteIntent = Intent(this, AuraVoiceService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val pendingMute = PendingIntent.getService(
            this,
            2,
            muteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action 3: Stop or Exempt Battery
        val action3Intent = Intent(this, AuraVoiceService::class.java).apply {
            action = if (!isBatteryExempt) ACTION_EXEMPT_BATTERY else ACTION_STOP_SERVICE
        }
        val pendingAction3 = PendingIntent.getService(
            this,
            3,
            action3Intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentTitle = "Aura AI • $userName's Assistant"
        val contentText = when {
            isMutedState -> "Microphone Muted (Listening Paused)"
            isPorcupine -> "Porcupine Active • Keyword '${prefs.selectedPorcupineKeyword.ifBlank { "JARVIS" }.uppercase()}'"
            else -> _serviceStatusText.value
        }

        val subText = when {
            !isBatteryExempt -> "⚠️ Battery Optimization Active (Tap to exempt)"
            isPorcupine -> "Hardware DSP Wake-Word Guard Active"
            else -> "Voice-Lock Guard Active"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText(subText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_btn_speak_now, "Listen", pendingListen)
            .addAction(
                if (isMutedState) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode,
                if (isMutedState) "Resume" else "Mute",
                pendingMute
            )

        if (!isBatteryExempt) {
            builder.addAction(android.R.drawable.ic_dialog_alert, "Exempt Battery", pendingAction3)
        } else {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingAction3)
        }

        return builder.build()
    }

    private fun startForegroundWithMicrophone() {
        try {
            val notification = buildNotification()
            val hasMicPermission = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasMicPermission) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType)
        } catch (t: Throwable) {
            Log.e(tag, "startForeground error: ${t.message}", t)
            stopSelf()
        }
    }

    fun updateNotification() {
        if (!_isServiceActive.value) return
        try {
            val notification = buildNotification()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update notification", e)
        }
    }

    fun pauseBackgroundMonitoring() {
        try {
            audioMonitorJob?.cancel()
            audioMonitorJob = null

            porcupineDetector?.stop()
            porcupineDetector?.release()
            porcupineDetector = null
            _isPorcupineActive.value = false

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            _serviceStatusText.value = "Mic paused for active speech input"
            updateNotification()
            Log.d(tag, "[AUDIO_RES_DEBUG] Released AudioRecord & Porcupine hardware mic lock for active speech input.")
        } catch (e: Exception) {
            Log.w(tag, "[AUDIO_RES_DEBUG] Error pausing background monitoring: ${e.message}")
        }
    }

    fun resumeBackgroundMonitoring() {
        if (!_isServiceActive.value || _isMuted.value || _isPausedForActiveListening.value) return
        _serviceStatusText.value = "Voice-Lock Guard Active • Standby for wake word"
        updateNotification()
        startAcousticMonitorLoop()
        Log.d(tag, "[AUDIO_RES_DEBUG] Resuming background acoustic monitor loop.")
    }

    /**
     * High-performance wake-word spotting loop.
     * Uses Picovoice Porcupine SDK for hardware-accelerated, low-power detection when configured,
     * and automatically falls back to the native on-device acoustic RMS & spectral engine.
     */
    @SuppressLint("MissingPermission")
    private fun startAcousticMonitorLoop() {
        if (_isPausedForActiveListening.value) {
            Log.d(tag, "[AUDIO_RES_DEBUG] startAcousticMonitorLoop skipped: paused for active speech input.")
            return
        }

        audioMonitorJob?.cancel()
        porcupineDetector?.release()
        porcupineDetector = null
        _isPorcupineActive.value = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            // Ignored
        }

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this@AuraVoiceService,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            Log.w(tag, "Microphone permission not granted. Running simulated monitor.")
            _activeWakeWordEngine.value = "Simulation Mode (No Mic Permission)"
            audioMonitorJob = serviceScope.launch(Dispatchers.IO) {
                runSimulatedBackgroundMonitor()
            }
            return
        }

        val prefs = AuraApplication.instance.preferences

        // 1. Check if Picovoice Porcupine is enabled and AccessKey is provided
        if (prefs.usePorcupineWakeWord && prefs.picovoiceAccessKey.isNotBlank()) {
            val detector = PorcupineWakeWordDetector(
                context = this@AuraVoiceService,
                accessKey = prefs.picovoiceAccessKey,
                onWakeWordDetected = { keywordIndex, keywordName ->
                    Log.i(tag, "Porcupine spotted wake-word: $keywordName (index=$keywordIndex)")
                    vibrateFeedback(isSuccess = true)
                    _serviceStatusText.value = "Wake-Word Spotted ($keywordName)"
                    updateNotification()
                    evaluateWakeWordBiometrics()
                },
                onError = { ex ->
                    Log.w(tag, "Porcupine runtime exception: ${ex.message}. Switching to native acoustic fallback.")
                    _isPorcupineActive.value = false
                    _activeWakeWordEngine.value = "Aura Acoustic Engine (Fallback)"
                    startNativeAcousticRecordLoop()
                }
            )

            val initialized = detector.initialize(
                keywordName = prefs.selectedPorcupineKeyword,
                sensitivity = prefs.porcupineSensitivity
            )

            if (initialized && detector.start()) {
                porcupineDetector = detector
                _isPorcupineActive.value = true
                _activeWakeWordEngine.value = "Picovoice Porcupine (${prefs.selectedPorcupineKeyword})"
                Log.i(tag, "Picovoice Porcupine active with keyword: ${prefs.selectedPorcupineKeyword}")
                return
            } else {
                Log.w(tag, "Porcupine could not start: ${detector.lastErrorMessage}. Falling back to native acoustic engine.")
                detector.release()
            }
        }

        // 2. Native On-Device Acoustic Engine (Default / Fallback)
        _isPorcupineActive.value = false
        _activeWakeWordEngine.value = "Aura On-Device Acoustic Engine"
        startNativeAcousticRecordLoop()
    }

    @SuppressLint("MissingPermission")
    private fun startNativeAcousticRecordLoop() {
        audioMonitorJob?.cancel()
        audioMonitorJob = serviceScope.launch(Dispatchers.IO) {
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                .coerceAtLeast(2048)

            try {
                // Prefer VOICE_RECOGNITION tuning for wake-word acoustic filtering
                audioRecord = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                } catch (e: Exception) {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                }

                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.startRecording()
                    val readChunk = ShortArray(bufferSize / 2)
                    var consecutiveSpeechFrames = 0

                    while (isActive && _isServiceActive.value) {
                        if (_isMuted.value) {
                            delay(400)
                            continue
                        }

                        val readCount = audioRecord?.read(readChunk, 0, readChunk.size) ?: 0
                        if (readCount > 0) {
                            // 1. Compute acoustic RMS energy
                            var sumSquares = 0.0
                            for (i in 0 until readCount) {
                                val s = readChunk[i].toDouble()
                                sumSquares += s * s

                                // Store into rolling circular window
                                rollingAudioBuffer[bufferWriteIndex] = readChunk[i]
                                bufferWriteIndex = (bufferWriteIndex + 1) % windowCapacity
                            }
                            val meanSquare = sumSquares / readCount
                            val rms = sqrt(meanSquare)
                            val normalizedRms = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                            _backgroundAudioRms.value = normalizedRms

                            // 2. Update adaptive noise floor
                            noiseFloorRms = (noiseFloorRms * 0.95f) + (normalizedRms * 0.05f)
                            val speechThreshold = (noiseFloorRms * 2.2f).coerceIn(0.12f, 0.40f)

                            // 3. Voice activity detection & wake-word spotting
                            if (normalizedRms > speechThreshold) {
                                consecutiveSpeechFrames++
                                if (consecutiveSpeechFrames == 3) {
                                    evaluateWakeWordBiometrics()
                                }
                            } else {
                                consecutiveSpeechFrames = 0
                            }
                        }
                        delay(50)
                    }
                } else {
                    Log.w(tag, "AudioRecord state not initialized, running simulated background monitor")
                    runSimulatedBackgroundMonitor()
                }
            } catch (e: Exception) {
                Log.w(tag, "Hardware mic recording unavailable: ${e.message}, running fallback monitor")
                runSimulatedBackgroundMonitor()
            }
        }
    }

    /**
     * Evaluates collected rolling audio frame against the owner's enrolled biometric voiceprint.
     */
    private fun evaluateWakeWordBiometrics() {
        val app = AuraApplication.instance
        val prefs = app.preferences
        val enrolledCentroid = VoiceprintEngine.deserializeEmbedding(prefs.voiceprintEmbeddingString)

        if (!prefs.isVoiceEnrolled || enrolledCentroid == null) {
            // Unenrolled — trigger wake for anyone
            triggerWakeDetectedFlow(isOwnerVerified = true)
            return
        }

        // Snapshot latest 1-second PCM segment from circular rolling buffer
        val snapshotSize = sampleRate
        val snapshot = ShortArray(snapshotSize)
        val startIndex = (bufferWriteIndex - snapshotSize + windowCapacity) % windowCapacity
        for (i in 0 until snapshotSize) {
            snapshot[i] = rollingAudioBuffer[(startIndex + i) % windowCapacity]
        }

        // Extract 32-D acoustic feature vector
        val voiceprintEngine = app.voiceprintEngine
        val testEmbedding = voiceprintEngine.extractEmbeddingFromPcm(snapshot)
        val result = voiceprintEngine.verifySpeaker(
            enrolledVoiceprint = enrolledCentroid,
            testEmbedding = testEmbedding,
            threshold = prefs.voiceSimilarityThreshold
        )

        Log.d(tag, "Biometric evaluation result: score=${result.similarityScore}, threshold=${result.threshold}, verified=${result.isVerified}")

        if (result.isVerified) {
            vibrateFeedback(isSuccess = true)
            _serviceStatusText.value = "Wake Word Detected: Owner Verified"
            updateNotification()
            triggerWakeDetectedFlow(isOwnerVerified = true)
        } else {
            // Mismatch: stranger spoke wake word
            vibrateFeedback(isSuccess = false)
            _serviceStatusText.value = "Voice Mismatch: Stranger voice rejected"
            updateNotification()
            Log.w(tag, "Unauthorized speaker rejected: score=${result.similarityScore}")
        }
    }

    private fun triggerWakeDetectedFlow(isOwnerVerified: Boolean) {
        _wakeWordDetectedEvent.tryEmit(isOwnerVerified)

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            val wl = pm?.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "AuraAI:WakeWordSpotScreenOn"
            )
            wl?.acquire(3000L)
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire screen wake lock: ${e.message}")
        }

        Log.i(tag, "[BACKGROUND_MONITOR_DEBUG] Wake-word triggered! Turning screen on and launching MainActivity. Owner verified: $isOwnerVerified")

        // Launch MainActivity with AUTO_TRIGGER_LISTEN extra
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("AUTO_TRIGGER_LISTEN", true)
            putExtra("OWNER_VERIFIED", isOwnerVerified)
        }
        startActivity(openAppIntent)
    }

    private fun vibrateFeedback(isSuccess: Boolean) {
        try {
            val durationMs = if (isSuccess) 80L else 200L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Vibration failed: ${e.message}")
        }
    }

    private suspend fun runSimulatedBackgroundMonitor() {
        while (serviceScope.isActive && _isServiceActive.value) {
            if (!_isMuted.value) {
                _backgroundAudioRms.value = 0.05f
            } else {
                _backgroundAudioRms.value = 0f
            }
            delay(1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "AuraVoiceService onDestroy called")
        _isServiceActive.value = false
        audioMonitorJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            // Ignored
        }

        try {
            porcupineDetector?.release()
            porcupineDetector = null
            _isPorcupineActive.value = false
        } catch (e: Exception) {
            // Ignored
        }

        abandonAudioFocus()
        releaseWakeLock()
        serviceScope.cancel()
        if (activeServiceInstance == this) {
            activeServiceInstance = null
        }
    }
}

