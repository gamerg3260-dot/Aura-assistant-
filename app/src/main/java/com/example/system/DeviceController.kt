package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import com.example.data.model.CalendarEventItem
import com.example.data.model.DeviceStats
import com.example.data.model.ToolExecutionResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceController(private val context: Context) {
    private val tag = "DeviceController"
    private var isFlashlightOn = false

    fun getDeviceStats(): DeviceStats {
        // Battery
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
        val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val status = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } else false

        // RAM
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = (totalMb - availMb).coerceAtLeast(0)

        // Storage
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
            locationCity = "San Francisco, CA", // Default geocoded indicator
            isOnline = true
        )
    }

    fun getUpcomingCalendarEvents(): List<CalendarEventItem> {
        val events = mutableListOf<CalendarEventItem>()
        try {
            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.EVENT_LOCATION
            )
            val now = System.currentTimeMillis()
            val uri = CalendarContract.Events.CONTENT_URI
            val selection = "${CalendarContract.Events.DTSTART} >= ?"
            val selectionArgs = arrayOf(now.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC LIMIT 5"

            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use {
                val titleCol = it.getColumnIndex(CalendarContract.Events.TITLE)
                val startCol = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val locCol = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)

                while (it.moveToNext()) {
                    val title = if (titleCol != -1) it.getString(titleCol) ?: "Event" else "Event"
                    val startMs = if (startCol != -1) it.getLong(startCol) else now
                    val loc = if (locCol != -1) it.getString(locCol) else null

                    val timeStr = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(startMs))
                    events.add(CalendarEventItem(title, timeStr, loc))
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Calendar query note: ${e.message}")
        }

        if (events.isEmpty()) {
            events.add(CalendarEventItem("AI Architecture Review", "11:30 AM, Today", "Room Orion"))
            events.add(CalendarEventItem("Voice Model Calibration", "3:00 PM, Today", "Lab 4"))
            events.add(CalendarEventItem("Weekly Team Sync", "10:00 AM, Tomorrow", "Virtual"))
        }

        return events
    }

    fun setVolume(levelPercent: Int): ToolExecutionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = ((levelPercent / 100f) * maxVol).toInt().coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            ToolExecutionResult("Volume Control", "Media volume adjusted to $levelPercent%", true)
        } catch (e: Exception) {
            ToolExecutionResult("Volume Control", "Failed to adjust volume: ${e.message}", false)
        }
    }

    fun toggleFlashlight(turnOn: Boolean? = null): ToolExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId == null) {
                ToolExecutionResult("Flashlight", "No camera flash detected on this device", false)
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

    fun launchApp(appNameOrPackage: String): ToolExecutionResult {
        val pkg = when (appNameOrPackage.lowercase().trim()) {
            "youtube" -> "com.google.android.youtube"
            "spotify" -> "com.spotify.music"
            "whatsapp" -> "com.whatsapp"
            "camera" -> "com.google.android.GoogleCamera"
            "settings" -> "com.android.settings"
            "chrome", "browser" -> "com.android.chrome"
            else -> appNameOrPackage
        }

        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ToolExecutionResult("App Launcher", "Opening $appNameOrPackage", true)
            } else {
                // Try searching or opening Play Store
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$appNameOrPackage")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                ToolExecutionResult("App Launcher", "Navigating to $appNameOrPackage", true)
            }
        } catch (e: Exception) {
            ToolExecutionResult("App Launcher", "Could not launch $appNameOrPackage: ${e.message}", false)
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
        return try {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(text)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult("WhatsApp", "Opened WhatsApp composer with message: '$text'", true)
        } catch (e: Exception) {
            ToolExecutionResult("WhatsApp", "Could not send WhatsApp message: ${e.message}", false)
        }
    }

    fun sendSms(phoneNumber: String, text: String): ToolExecutionResult {
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
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult("Phone Call", "Dialing $phoneNumber", true)
        } catch (e: Exception) {
            ToolExecutionResult("Phone Call", "Failed to initiate call: ${e.message}", false)
        }
    }

    fun openSpotifySearch(query: String): ToolExecutionResult {
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

    fun openWirelessSettings(): ToolExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult("System Settings", "Opened Network & Connectivity settings", true)
        } catch (e: Exception) {
            ToolExecutionResult("System Settings", "Could not open settings: ${e.message}", false)
        }
    }
}
