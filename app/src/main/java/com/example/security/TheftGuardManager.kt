package com.example.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import kotlin.math.sqrt

data class TheftIncident(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val title: String,
    val details: String
)

class TheftGuardManager(private val context: Context) : SensorEventListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val _isArmed = MutableStateFlow(false)
    val isArmed: StateFlow<Boolean> = _isArmed.asStateFlow()

    private val _isAlarmTriggered = MutableStateFlow(false)
    val isAlarmTriggered: StateFlow<Boolean> = _isAlarmTriggered.asStateFlow()

    private val _alarmReason = MutableStateFlow("")
    val alarmReason: StateFlow<String> = _alarmReason.asStateFlow()

    private val _motionSensitivity = MutableStateFlow(3.8f)
    val motionSensitivity: StateFlow<Float> = _motionSensitivity.asStateFlow()

    private val _incidents = MutableStateFlow<List<TheftIncident>>(emptyList())
    val incidents: StateFlow<List<TheftIncident>> = _incidents.asStateFlow()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var toneGenerator: ToneGenerator? = null
    private var alarmLoopJob: Job? = null
    private var torchBlinkJob: Job? = null

    private var baselineAccel = 9.8f
    private var isCalibrated = false
    private var isChargerReceiverRegistered = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_DISCONNECTED && _isArmed.value) {
                triggerAlarm(
                    type = "CHARGER_DISCONNECT",
                    title = "Charger Disconnected",
                    details = "Device was unplugged while Theft Guard was armed."
                )
            }
        }
    }

    fun setSensitivity(value: Float) {
        _motionSensitivity.value = value.coerceIn(1.5f, 8.0f)
    }

    fun armGuard(motionDetection: Boolean = true, chargerDetection: Boolean = true) {
        _isArmed.value = true
        isCalibrated = false

        if (motionDetection && accelerometer != null) {
            sensorManager?.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
        }

        if (chargerDetection && !isChargerReceiverRegistered) {
            try {
                val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
                context.registerReceiver(powerReceiver, filter)
                isChargerReceiverRegistered = true
            } catch (e: Exception) {
                Log.w("TheftGuard", "Failed to register power receiver", e)
            }
        }

        addIncident(
            type = "GUARD_ARMED",
            title = "Theft Guard Armed",
            details = "Active protection enabled (Motion + Intruder detection active)."
        )
    }

    fun disarmGuard() {
        _isArmed.value = false
        stopAlarm()

        if (sensorManager != null) {
            try {
                sensorManager.unregisterListener(this)
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (isChargerReceiverRegistered) {
            try {
                context.unregisterReceiver(powerReceiver)
                isChargerReceiverRegistered = false
            } catch (e: Exception) {
                // Ignore
            }
        }

        addIncident(
            type = "GUARD_DISARMED",
            title = "Theft Guard Disarmed",
            details = "Protection disarmed by user."
        )
    }

    fun toggleArm(): Boolean {
        if (_isArmed.value) {
            disarmGuard()
            return false
        } else {
            armGuard()
            return true
        }
    }

    fun triggerAlarm(type: String, title: String, details: String) {
        if (_isAlarmTriggered.value) return

        _isAlarmTriggered.value = true
        _alarmReason.value = "$title: $details"

        addIncident(type, title, details)
        startAlarmAudioAndVibration()
        startTorchBlink()
    }

    fun stopAlarm() {
        _isAlarmTriggered.value = false
        _alarmReason.value = ""

        alarmLoopJob?.cancel()
        alarmLoopJob = null

        torchBlinkJob?.cancel()
        torchBlinkJob = null

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            // Ignore
        }

        stopVibration()
        setTorchState(false)
    }

    fun onVoiceMismatchDetected(confidence: Float) {
        if (_isArmed.value) {
            triggerAlarm(
                type = "VOICE_INTRUDER",
                title = "Voice Intruder Mismatch",
                details = "Unauthorized voice attempted commands (Confidence: ${(confidence * 100).toInt()}%)."
            )
        }
    }

    private fun startAlarmAudioAndVibration() {
        alarmLoopJob?.cancel()
        alarmLoopJob = coroutineScope.launch {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            } catch (e: Exception) {
                // Audio fallback
            }

            val vibrator = getVibrator()

            while (isActive && _isAlarmTriggered.value) {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 700)
                } catch (e: Exception) {
                    // Ignore
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(0, 300, 100, 300),
                                -1
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(longArrayOf(0, 300, 100, 300), -1)
                    }
                } catch (e: Exception) {
                    // Ignore
                }

                delay(900)
            }
        }
    }

    private fun startTorchBlink() {
        torchBlinkJob?.cancel()
        torchBlinkJob = coroutineScope.launch {
            var state = true
            while (isActive && _isAlarmTriggered.value) {
                setTorchState(state)
                state = !state
                delay(250)
            }
            setTorchState(false)
        }
    }

    private fun setTorchState(on: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cameraManager.setTorchMode(cameraId, on)
        } catch (e: Exception) {
            // Flashlight might not be available or permission not held
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun stopVibration() {
        try {
            getVibrator()?.cancel()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun addIncident(type: String, title: String, details: String) {
        val newIncident = TheftIncident(
            type = type,
            title = title,
            details = details
        )
        _incidents.value = listOf(newIncident) + _incidents.value.take(20)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!_isArmed.value || _isAlarmTriggered.value || event == null) return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        if (!isCalibrated) {
            baselineAccel = magnitude
            isCalibrated = true
            return
        }

        val delta = kotlin.math.abs(magnitude - baselineAccel)
        if (delta > _motionSensitivity.value) {
            triggerAlarm(
                type = "UNAUTHORIZED_MOTION",
                title = "Device Picked Up / Moved",
                details = "Dynamic movement delta: ${"%.1f".format(delta)} m/s² (Sensitivity: ${_motionSensitivity.value})"
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
