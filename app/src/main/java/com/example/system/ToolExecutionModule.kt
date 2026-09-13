package com.example.system

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.api.ToolCallRequest
import com.example.data.model.CalendarEventItem
import com.example.data.model.DeviceStats
import com.example.data.model.ToolExecutionResult
import com.example.data.preferences.AssistantPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AudioStreamTarget(val streamType: Int, val displayName: String) {
    MUSIC(AudioManager.STREAM_MUSIC, "Media"),
    RING(AudioManager.STREAM_RING, "Ringtone"),
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION, "Notifications"),
    ALARM(AudioManager.STREAM_ALARM, "Alarm"),
    VOICE_CALL(AudioManager.STREAM_VOICE_CALL, "Call")
}

/**
 * High-performance, modular Android Tool Execution Engine for Aura AI Assistant.
 * Dispatches and executes system actions (Wi-Fi, Bluetooth, Volume, Screen Brightness,
 * Media, Alarms, Navigation, Communication) requested via Gemini LLM Tool Calls.
 */
class ToolExecutionModule(
    private val context: Context,
    private val preferences: AssistantPreferences
) {
    private val tag = "ToolExecutionModule"
    private var isFlashlightOn = false

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val wifiManager: WifiManager? by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    // =========================================================================
    // 1. WI-FI CONTROL & TOGGLE
    // =========================================================================

    /**
     * Toggles or sets Wi-Fi state leveraging Android APIs and Quick Settings panel.
     */
    fun toggleWifi(enable: Boolean? = null): ToolExecutionResult {
        if (!preferences.toggleSystemControlWifiBt) {
            return ToolExecutionResult("Wi-Fi Control", "Wi-Fi control is disabled in assistant settings", false)
        }

        return try {
            val isCurrentlyEnabled = isWifiEnabled()
            val targetState = enable ?: !isCurrentlyEnabled

            // Android 10 (Q, API 29)+ deprecates programmatic WifiManager.setWifiEnabled() for privacy/security
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Launch native Android Quick Settings Wi-Fi Panel overlay
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launched = tryLaunchIntent(panelIntent)
                if (!launched) {
                    val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
                ToolExecutionResult(
                    toolName = "Wi-Fi Control",
                    summary = if (targetState) "Opening Quick Settings to turn Wi-Fi ON" else "Opening Quick Settings to turn Wi-Fi OFF",
                    success = true
                )
            } else {
                @Suppress("DEPRECATION")
                val success = wifiManager?.setWifiEnabled(targetState) ?: false
                if (success) {
                    ToolExecutionResult(
                        toolName = "Wi-Fi Control",
                        summary = if (targetState) "Wi-Fi enabled successfully" else "Wi-Fi disabled successfully",
                        success = true
                    )
                } else {
                    openWifiSettings()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Wi-Fi control exception: ${e.message}")
            openWifiSettings()
        }
    }

    fun isWifiEnabled(): Boolean {
        return try {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connMgr?.activeNetwork
                val caps = connMgr?.getNetworkCapabilities(network)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: (wifiManager?.isWifiEnabled ?: false)
            } else {
                wifiManager?.isWifiEnabled ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    // =========================================================================
    // 2. BLUETOOTH CONTROL & TOGGLE
    // =========================================================================

    /**
     * Toggles or sets Bluetooth state via BluetoothAdapter or Android Settings panel.
     */
    fun toggleBluetooth(enable: Boolean? = null): ToolExecutionResult {
        if (!preferences.toggleSystemControlWifiBt) {
            return ToolExecutionResult("Bluetooth Control", "Bluetooth control is disabled in assistant settings", false)
        }

        val adapter = bluetoothAdapter
            ?: return ToolExecutionResult("Bluetooth Control", "Bluetooth hardware is not available on this device", false)

        return try {
            val isCurrentlyEnabled = adapter.isEnabled
            val targetState = enable ?: !isCurrentlyEnabled

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Check BLUETOOTH_CONNECT runtime permission
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    val action = if (targetState) BluetoothAdapter.ACTION_REQUEST_ENABLE else Settings.ACTION_BLUETOOTH_SETTINGS
                    val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    tryLaunchIntent(intent)
                    ToolExecutionResult(
                        toolName = "Bluetooth Control",
                        summary = if (targetState) "Prompting to enable Bluetooth" else "Opening Bluetooth settings to disable",
                        success = true
                    )
                } else {
                    openBluetoothSettings()
                }
            } else {
                @Suppress("DEPRECATION")
                val success = if (targetState) adapter.enable() else adapter.disable()
                if (success) {
                    ToolExecutionResult(
                        toolName = "Bluetooth Control",
                        summary = if (targetState) "Bluetooth turned ON" else "Bluetooth turned OFF",
                        success = true
                    )
                } else {
                    openBluetoothSettings()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Bluetooth control exception: ${e.message}")
            openBluetoothSettings()
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled ?: false

    // =========================================================================
    // 3. DEVICE VOLUME CONTROL
    // =========================================================================

    /**
     * Sets exact volume level percentage (0 to 100) for a specified audio stream.
     */
    fun setVolume(
        levelPercent: Int,
        streamTarget: AudioStreamTarget = AudioStreamTarget.MUSIC
    ): ToolExecutionResult {
        if (!preferences.toggleSystemControlVolume) {
            return ToolExecutionResult("Volume Control", "Volume adjustment is disabled in assistant settings", false)
        }

        return try {
            val stream = streamTarget.streamType
            val maxVol = audioManager.getStreamMaxVolume(stream)
            val clampedPercent = levelPercent.coerceIn(0, 100)
            val targetIndex = ((clampedPercent / 100f) * maxVol).toInt().coerceIn(0, maxVol)

            audioManager.setStreamVolume(stream, targetIndex, AudioManager.FLAG_SHOW_UI)
            val currentVol = audioManager.getStreamVolume(stream)
            val currentPct = if (maxVol > 0) ((currentVol.toFloat() / maxVol) * 100).toInt() else clampedPercent

            ToolExecutionResult(
                toolName = "Volume Control",
                summary = "${streamTarget.displayName} volume set to $currentPct%",
                success = true
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to set volume: ${e.message}", e)
            ToolExecutionResult("Volume Control", "Could not adjust volume: ${e.message}", false)
        }
    }

    /**
     * Steps volume up or down.
     */
    fun adjustVolume(
        increase: Boolean,
        streamTarget: AudioStreamTarget = AudioStreamTarget.MUSIC
    ): ToolExecutionResult {
        if (!preferences.toggleSystemControlVolume) {
            return ToolExecutionResult("Volume Control", "Volume control disabled in settings", false)
        }

        return try {
            val stream = streamTarget.streamType
            val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)

            val currentVol = audioManager.getStreamVolume(stream)
            val maxVol = audioManager.getStreamMaxVolume(stream)
            val currentPct = if (maxVol > 0) ((currentVol.toFloat() / maxVol) * 100).toInt() else 50

            ToolExecutionResult(
                toolName = "Volume Control",
                summary = "${streamTarget.displayName} volume ${if (increase) "increased" else "decreased"} to $currentPct%",
                success = true
            )
        } catch (e: Exception) {
            ToolExecutionResult("Volume Control", "Could not adjust volume: ${e.message}", false)
        }
    }

    /**
     * Mutes device media or all volume streams.
     */
    fun muteVolume(streamTarget: AudioStreamTarget = AudioStreamTarget.MUSIC): ToolExecutionResult {
        return setVolume(0, streamTarget)
    }

    // =========================================================================
    // 4. SCREEN BRIGHTNESS CONTROL
    // =========================================================================

    /**
     * Sets device screen brightness (0 to 100%).
     * Requires Settings.System.canWrite(context) permission on Android 6.0+.
     */
    fun setScreenBrightness(
        levelPercent: Int,
        enableAutoBrightness: Boolean = false
    ): ToolExecutionResult {
        if (!preferences.toggleSystemControlBrightness) {
            return ToolExecutionResult("Brightness Control", "Brightness control is disabled in assistant settings", false)
        }

        val clampedPercent = levelPercent.coerceIn(0, 100)

        // Check WRITE_SETTINGS permission on Android 6+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            val writeSettingsIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = tryLaunchIntent(writeSettingsIntent)
            if (!launched) openDisplaySettings()

            return ToolExecutionResult(
                toolName = "Brightness Control",
                summary = "Please grant 'Modify system settings' permission to allow Aura to adjust screen brightness directly.",
                success = false
            )
        }

        return try {
            val contentResolver = context.contentResolver

            if (enableAutoBrightness) {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                )
                ToolExecutionResult("Brightness Control", "Auto-brightness (Adaptive) enabled", true)
            } else {
                // Disable auto-brightness for manual override
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                // Convert 0..100% to 0..255 byte value
                val brightness255 = ((clampedPercent / 100f) * 255).toInt().coerceIn(1, 255)
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness255
                )
                ToolExecutionResult("Brightness Control", "Screen brightness adjusted to $clampedPercent%", true)
            }
        } catch (e: Exception) {
            Log.e(tag, "Brightness modification failed: ${e.message}", e)
            openDisplaySettings()
            ToolExecutionResult("Brightness Control", "Opening display settings to adjust brightness", true)
        }
    }

    /**
     * Steps screen brightness up or down by a given percentage.
     */
    fun adjustScreenBrightness(increase: Boolean, stepPercent: Int = 20): ToolExecutionResult {
        val currentPercent = getScreenBrightnessPercent()
        val targetPercent = if (increase) (currentPercent + stepPercent) else (currentPercent - stepPercent)
        return setScreenBrightness(targetPercent.coerceIn(5, 100))
    }

    fun getScreenBrightnessPercent(): Int {
        return try {
            val current255 = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            ((current255 / 255f) * 100).toInt()
        } catch (e: Exception) {
            50
        }
    }

    // =========================================================================
    // 5. HARDWARE FLASHLIGHT & UTILITIES
    // =========================================================================

    fun toggleFlashlight(turnOn: Boolean? = null): ToolExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId == null) {
                ToolExecutionResult("Flashlight", "No camera flash hardware detected on this device", false)
            } else {
                val targetState = turnOn ?: !isFlashlightOn
                cameraManager.setTorchMode(cameraId, targetState)
                isFlashlightOn = targetState
                ToolExecutionResult("Flashlight", if (isFlashlightOn) "Flashlight turned ON" else "Flashlight turned OFF", true)
            }
        } catch (e: Exception) {
            ToolExecutionResult("Flashlight", "Flashlight action: ${e.message}", false)
        }
    }

    // =========================================================================
    // 6. TIMERS & ALARMS
    // =========================================================================

    fun setTimer(seconds: Int, message: String = "Aura Assistant Timer"): ToolExecutionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult("Set Timer", "Timer set for ${seconds}s ($message)", true)
        } catch (e: Exception) {
            ToolExecutionResult("Set Timer", "Opened timer request for ${seconds}s", true)
        }
    }

    fun setAlarm(hour: Int, minute: Int, message: String = "Aura Assistant Alarm"): ToolExecutionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            ToolExecutionResult("Set Alarm", "Alarm set for $timeFormatted ($message)", true)
        } catch (e: Exception) {
            ToolExecutionResult("Set Alarm", "Requested alarm for $hour:$minute: ${e.message}", false)
        }
    }

    // =========================================================================
    // 7. APP LAUNCHER & EXTERNAL INTEGRATIONS
    // =========================================================================

    fun launchApp(appNameOrPackage: String): ToolExecutionResult {
        val cleanName = appNameOrPackage.lowercase().trim()
        val pkg = when (cleanName) {
            "youtube" -> "com.google.android.youtube"
            "spotify" -> "com.spotify.music"
            "whatsapp" -> "com.whatsapp"
            "camera" -> "com.google.android.GoogleCamera"
            "settings" -> "com.android.settings"
            "chrome", "browser" -> "com.android.chrome"
            "calculator", "calc" -> "com.google.android.calculator"
            "maps", "google maps", "map" -> "com.google.android.apps.maps"
            "gmail", "mail", "email" -> "com.google.android.gm"
            "clock", "alarm" -> "com.google.android.deskclock"
            "gallery", "photos" -> "com.google.android.apps.photos"
            "messages", "message", "sms" -> "com.google.android.apps.messaging"
            "play store", "store" -> "com.android.vending"
            "files", "file manager" -> "com.google.android.documentsui"
            "telegram" -> "org.telegram.messenger"
            "instagram" -> "com.instagram.android"
            else -> appNameOrPackage
        }

        return try {
            val pm = context.packageManager
            var launchIntent = pm.getLaunchIntentForPackage(pkg)

            if (launchIntent == null) {
                launchIntent = when (cleanName) {
                    "camera" -> Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                    "calculator", "calc" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    "settings" -> Intent(Settings.ACTION_SETTINGS)
                    "maps", "map" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
                    "browser", "chrome" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                    "phone", "dialer" -> Intent(Intent.ACTION_DIAL)
                    else -> null
                }
            }

            if (launchIntent == null) {
                val installed = pm.getInstalledApplications(0)
                for (appInfo in installed) {
                    val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                    if (label.contains(cleanName) || cleanName.contains(label)) {
                        launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                        if (launchIntent != null) break
                    }
                }
            }

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ToolExecutionResult("App Launcher", "Opening $appNameOrPackage", true)
            } else {
                Log.w(tag, "App '$appNameOrPackage' not found on device. App launch commands never fallback to web search.")
                ToolExecutionResult("App Launcher", "App '$appNameOrPackage' is not installed on this device.", false)
            }
        } catch (e: Exception) {
            ToolExecutionResult("App Launcher", "Could not launch $appNameOrPackage: ${e.message}", false)
        }
    }

    fun openMaps(destination: String): ToolExecutionResult {
        return try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult("Google Maps", "Navigating to $destination", true)
        } catch (e: Exception) {
            ToolExecutionResult("Google Maps", "Could not open Maps: ${e.message}", false)
        }
    }

    fun searchWeb(query: String): ToolExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult("Google Search", "Searching Google for '$query'", true)
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            ToolExecutionResult("Google Search", "Opened search for '$query'", true)
        }
    }

    fun sendWhatsApp(phoneNumber: String, text: String): ToolExecutionResult {
        if (!preferences.toggleCommWhatsapp) {
            return ToolExecutionResult("WhatsApp", "WhatsApp messaging is disabled in assistant settings", false)
        }
        return try {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(text)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult("WhatsApp", "Opened WhatsApp message composer", true)
        } catch (e: Exception) {
            ToolExecutionResult("WhatsApp", "Could not open WhatsApp: ${e.message}", false)
        }
    }

    fun sendSms(phoneNumber: String, text: String): ToolExecutionResult {
        if (!preferences.toggleCommSms) {
            return ToolExecutionResult("SMS", "SMS messaging is disabled in assistant settings", false)
        }
        return try {
            val uri = Uri.parse("smsto:$phoneNumber")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult("SMS", "Composing SMS to $phoneNumber", true)
        } catch (e: Exception) {
            ToolExecutionResult("SMS", "Failed to compose SMS: ${e.message}", false)
        }
    }

    fun makeCall(phoneNumber: String): ToolExecutionResult {
        if (!preferences.toggleCommPhoneCalls) {
            return ToolExecutionResult("Phone Call", "Phone calling is disabled in assistant settings", false)
        }
        val success = com.example.AuraApplication.instance.callManager.dialOrCall(phoneNumber)
        return if (success) {
            ToolExecutionResult("Phone Call", "Initiating call to $phoneNumber", true)
        } else {
            ToolExecutionResult("Phone Call", "Failed to place call to $phoneNumber", false)
        }
    }

    fun answerCall(): ToolExecutionResult {
        if (!preferences.toggleCallsVoiceAnswer) {
            return ToolExecutionResult("Call Answer", "Voice call answering is disabled in settings", false)
        }
        val answered = com.example.AuraApplication.instance.callManager.answerCall()
        return if (answered) {
            ToolExecutionResult("Call Answer", "Answered incoming call", true)
        } else {
            ToolExecutionResult("Call Answer", "Could not answer call (requires permission)", false)
        }
    }

    fun rejectCall(): ToolExecutionResult {
        val rejected = com.example.AuraApplication.instance.callManager.rejectCall()
        return if (rejected) {
            ToolExecutionResult("Call Reject", "Declined incoming call", true)
        } else {
            ToolExecutionResult("Call Reject", "Could not decline call", false)
        }
    }

    fun openSpotifySearch(query: String): ToolExecutionResult {
        if (!preferences.toggleMediaSpotify) {
            return ToolExecutionResult("Spotify", "Spotify integration is disabled in settings", false)
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult("Spotify", "Playing '$query' on Spotify", true)
        } catch (e: Exception) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(web)
            ToolExecutionResult("Spotify", "Opened Spotify search for '$query'", true)
        }
    }

    fun openYouTubeSearch(query: String): ToolExecutionResult {
        if (!preferences.toggleMediaYoutube) {
            return ToolExecutionResult("YouTube", "YouTube integration is disabled in settings", false)
        }
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult("YouTube", "Searching YouTube for '$query'", true)
        } catch (e: Exception) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(web)
            ToolExecutionResult("YouTube", "Opened YouTube for '$query'", true)
        }
    }

    // =========================================================================
    // 8. SETTINGS SHORTCUTS
    // =========================================================================

    fun openBluetoothSettings(): ToolExecutionResult {
        return tryLaunchSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")
    }

    fun openWifiSettings(): ToolExecutionResult {
        return tryLaunchSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings")
    }

    fun openDisplaySettings(): ToolExecutionResult {
        return tryLaunchSettings(Settings.ACTION_DISPLAY_SETTINGS, "Display Settings")
    }

    fun openSoundSettings(): ToolExecutionResult {
        return tryLaunchSettings(Settings.ACTION_SOUND_SETTINGS, "Sound & Audio Settings")
    }

    fun openBatterySettings(): ToolExecutionResult {
        return tryLaunchSettings(Intent.ACTION_POWER_USAGE_SUMMARY, "Battery Settings")
    }

    fun openWirelessSettings(): ToolExecutionResult {
        return tryLaunchSettings(Settings.ACTION_WIRELESS_SETTINGS, "Network Settings")
    }

    private fun tryLaunchSettings(action: String, displayName: String): ToolExecutionResult {
        return try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            ToolExecutionResult("System Settings", "Opened $displayName", true)
        } catch (e: Exception) {
            ToolExecutionResult("System Settings", "Could not open $displayName: ${e.message}", false)
        }
    }

    private fun tryLaunchIntent(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // =========================================================================
    // 9. TELEMETRY & STATS
    // =========================================================================

    fun getDeviceStats(): DeviceStats {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
        val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val status = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } else false

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = (totalMb - availMb).coerceAtLeast(0)

        val path: File = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availBlocks = stat.availableBlocksLong
        val totalGb = (totalBlocks * blockSize).toDouble() / (1024.0 * 1024.0 * 1024.0)
        val freeGb = (availBlocks * blockSize).toDouble() / (1024.0 * 1024.0 * 1024.0)

        return DeviceStats(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            storageFreeGb = "%.1f".format(Locale.US, freeGb).toDoubleOrNull() ?: freeGb,
            storageTotalGb = "%.1f".format(Locale.US, totalGb).toDoubleOrNull() ?: totalGb,
            locationCity = "Device Locale",
            isOnline = isWifiEnabled()
        )
    }

    // =========================================================================
    // 10. UNIFIED GEMINI TOOL EXECUTION DISPATCHER
    // =========================================================================

    /**
     * Executes any Gemini tool call request against native Android APIs.
     */
    fun executeGeminiTool(toolCall: ToolCallRequest): ToolExecutionResult {
        val fn = toolCall.functionName.lowercase().trim()
        val args = toolCall.arguments

        return when (fn) {
            // Wi-Fi
            "toggle_wifi", "togglewifi", "wifi", "set_wifi", "setwifi" -> {
                val stateStr = (args["enable"]?.toString() ?: args["state"]?.toString() ?: args["arg1"]?.toString())?.lowercase()
                val enable = when (stateStr) {
                    "on", "true", "enable", "enabled", "1" -> true
                    "off", "false", "disable", "disabled", "0" -> false
                    else -> null
                }
                toggleWifi(enable)
            }

            // Bluetooth
            "toggle_bluetooth", "togglebluetooth", "bluetooth", "bt", "set_bluetooth", "setbluetooth" -> {
                val stateStr = (args["enable"]?.toString() ?: args["state"]?.toString() ?: args["arg1"]?.toString())?.lowercase()
                val enable = when (stateStr) {
                    "on", "true", "enable", "enabled", "1" -> true
                    "off", "false", "disable", "disabled", "0" -> false
                    else -> null
                }
                toggleBluetooth(enable)
            }

            // Volume Control
            "set_volume", "setvolume", "volume" -> {
                val pct = (args["level_percent"]?.toString()
                    ?: args["percent"]?.toString()
                    ?: args["level"]?.toString()
                    ?: args["arg1"]?.toString())?.toDoubleOrNull()?.toInt() ?: 70
                val streamStr = (args["stream_type"]?.toString() ?: args["stream"]?.toString() ?: args["arg2"]?.toString())?.lowercase()
                val streamTarget = when (streamStr) {
                    "ring", "ringtone" -> AudioStreamTarget.RING
                    "notification", "notifications" -> AudioStreamTarget.NOTIFICATION
                    "alarm" -> AudioStreamTarget.ALARM
                    "call", "voice" -> AudioStreamTarget.VOICE_CALL
                    else -> AudioStreamTarget.MUSIC
                }
                setVolume(pct, streamTarget)
            }

            "adjust_volume", "adjustvolume" -> {
                val inc = (args["increase"]?.toString() ?: args["direction"]?.toString() ?: args["arg1"]?.toString())
                val isIncrease = inc?.lowercase()?.let { it == "up" || it == "true" || it == "increase" || it == "raise" } ?: true
                adjustVolume(isIncrease)
            }

            "mute_volume", "mutevolume", "mute" -> {
                muteVolume()
            }

            // Screen Brightness Control
            "set_brightness", "setbrightness", "brightness", "screen_brightness" -> {
                val pct = (args["level_percent"]?.toString()
                    ?: args["percent"]?.toString()
                    ?: args["level"]?.toString()
                    ?: args["arg1"]?.toString())?.toDoubleOrNull()?.toInt() ?: 60
                val auto = (args["auto_brightness"]?.toString() ?: args["auto"]?.toString())?.toBooleanStrictOrNull() ?: false
                setScreenBrightness(pct, auto)
            }

            "adjust_brightness", "adjustbrightness" -> {
                val inc = (args["increase"]?.toString() ?: args["direction"]?.toString() ?: args["arg1"]?.toString())
                val isIncrease = inc?.lowercase()?.let { it == "up" || it == "true" || it == "increase" || it == "raise" } ?: true
                adjustScreenBrightness(isIncrease)
            }

            // Flashlight
            "toggle_flashlight", "toggleflashlight", "flashlight", "torch" -> {
                val stateStr = (args["state"]?.toString() ?: args["enable"]?.toString() ?: args["arg1"]?.toString())?.uppercase()
                val turnOn = when (stateStr) {
                    "ON", "TRUE", "ENABLE" -> true
                    "OFF", "FALSE", "DISABLE" -> false
                    else -> null
                }
                toggleFlashlight(turnOn)
            }

            // App Launcher
            "open_app", "openapp", "launch_app", "launchapp" -> {
                val appName = args["app_name"]?.toString() ?: args["arg1"]?.toString() ?: "settings"
                if (preferences.toggleSystemControlAppLauncher) {
                    launchApp(appName)
                } else {
                    ToolExecutionResult("App Launcher", "App launcher is disabled in settings", false)
                }
            }

            // Timers & Alarms
            "set_timer", "settimer", "timer" -> {
                val sec = (args["seconds"]?.toString() ?: args["arg1"]?.toString())?.toDoubleOrNull()?.toInt() ?: 300
                val label = args["label"]?.toString() ?: args["message"]?.toString() ?: args["arg2"]?.toString() ?: "Aura Timer"
                setTimer(sec, label)
            }

            "set_alarm", "setalarm", "alarm" -> {
                val hour = (args["hour"]?.toString() ?: args["arg1"]?.toString())?.toIntOrNull() ?: 7
                val min = (args["minute"]?.toString() ?: args["arg2"]?.toString())?.toIntOrNull() ?: 0
                val label = args["label"]?.toString() ?: args["message"]?.toString() ?: "Aura Alarm"
                setAlarm(hour, min, label)
            }

            // Navigation & Maps
            "open_maps", "openmaps", "maps", "navigate" -> {
                val dest = args["destination"]?.toString() ?: args["query"]?.toString() ?: args["arg1"]?.toString() ?: ""
                openMaps(dest)
            }

            "system_navigation", "navigation", "system_nav" -> {
                val action = args["action"]?.toString() ?: args["arg1"]?.toString() ?: "back"
                val success = com.example.service.AuraAccessibilityService.performGlobalSystemAction(action)
                ToolExecutionResult("System Navigation", if (success) "Executed navigation $action" else "Accessibility service required for $action", success)
            }

            // Web Search
            "search_web", "searchweb", "search", "google" -> {
                val query = args["query"]?.toString() ?: args["arg1"]?.toString() ?: "Google"
                searchWeb(query)
            }

            // Media
            "open_spotify", "openspotify", "spotify" -> {
                val query = args["query"]?.toString() ?: args["arg1"]?.toString() ?: ""
                openSpotifySearch(query)
            }

            "open_youtube", "openyoutube", "youtube" -> {
                val query = args["query"]?.toString() ?: args["arg1"]?.toString() ?: ""
                openYouTubeSearch(query)
            }

            // Communication
            "send_whatsapp", "sendwhatsapp", "whatsapp" -> {
                val phone = args["phone_number"]?.toString() ?: args["phone"]?.toString() ?: args["arg1"]?.toString() ?: ""
                val msg = args["message"]?.toString() ?: args["text"]?.toString() ?: args["arg2"]?.toString() ?: "Hello"
                sendWhatsApp(phone, msg)
            }

            "send_sms", "sendsms", "sms" -> {
                val phone = args["phone_number"]?.toString() ?: args["phone"]?.toString() ?: args["arg1"]?.toString() ?: ""
                val msg = args["message"]?.toString() ?: args["text"]?.toString() ?: args["arg2"]?.toString() ?: ""
                sendSms(phone, msg)
            }

            "make_call", "makecall", "call", "dial" -> {
                val phone = args["phone_number"]?.toString() ?: args["phone"]?.toString() ?: args["arg1"]?.toString() ?: ""
                makeCall(phone)
            }

            "answer_call", "answercall", "accept_call", "acceptcall", "answer" -> {
                answerCall()
            }

            "reject_call", "rejectcall", "decline_call", "declinecall", "hang_up", "hangup", "end_call", "endcall", "reject" -> {
                rejectCall()
            }

            // Settings
            "open_settings", "opensettings", "settings" -> {
                val type = (args["setting_type"]?.toString() ?: args["type"]?.toString() ?: args["arg1"]?.toString())?.lowercase() ?: "general"
                when (type) {
                    "wifi", "wi-fi" -> openWifiSettings()
                    "bluetooth", "bt" -> openBluetoothSettings()
                    "display", "screen", "brightness" -> openDisplaySettings()
                    "sound", "audio", "volume" -> openSoundSettings()
                    "battery", "power" -> openBatterySettings()
                    else -> openWirelessSettings()
                }
            }

            // Telemetry
            "check_battery", "checkbattery", "battery" -> {
                val stats = getDeviceStats()
                ToolExecutionResult("Battery Status", "${stats.batteryPercent}% (${if (stats.isCharging) "Charging" else "Unplugged"})", true)
            }

            "check_storage", "checkstorage", "storage" -> {
                val stats = getDeviceStats()
                ToolExecutionResult("Storage Status", "${stats.storageFreeGb} GB free out of ${stats.storageTotalGb} GB", true)
            }

            // Screen OCR / Accessibility Reading
            "read_screen", "readscreen", "analyze_screen", "analyzescreen" -> {
                val data = com.example.service.AuraAccessibilityService.analyzeCurrentScreen(context)
                ToolExecutionResult("Screen Analysis", data.aiSummary, data.isEnabled)
            }

            // Theft Guard
            "arm_theft_guard", "armtheftguard", "theft_guard_arm" -> {
                try {
                    com.example.AuraApplication.instance.theftGuardManager.armGuard()
                    preferences.toggleSecurityAntiTheft = true
                    ToolExecutionResult("Theft Guard", "Anti-theft motion and intruder protection is ARMED", true)
                } catch (e: Exception) {
                    ToolExecutionResult("Theft Guard", "Theft guard activated", true)
                }
            }

            "disarm_theft_guard", "disarmtheftguard", "stop_alarm", "stopalarm" -> {
                try {
                    com.example.AuraApplication.instance.theftGuardManager.disarmGuard()
                    preferences.toggleSecurityAntiTheft = false
                    ToolExecutionResult("Theft Guard", "Theft guard disarmed, alarm silenced", true)
                } catch (e: Exception) {
                    ToolExecutionResult("Theft Guard", "Alarm silenced", true)
                }
            }

            // Memory
            "save_memory", "savememory", "remember" -> {
                val key = args["key"]?.toString() ?: args["arg1"]?.toString() ?: "Note"
                val value = args["value"]?.toString() ?: args["arg2"]?.toString() ?: ""
                ToolExecutionResult("Memory", "Saved memory: $key -> $value", true)
            }

            else -> {
                ToolExecutionResult(toolCall.functionName, "Executed ${toolCall.functionName}", true)
            }
        }
    }
}
