package com.example.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Data representation of the app's essential permission status.
 */
data class AppPermissionState(
    val hasAudioPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasForegroundMicrophonePermission: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false
) {
    val isCoreVoiceReady: Boolean
        get() = hasAudioPermission && (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || hasForegroundMicrophonePermission)

    val isAllGranted: Boolean
        get() = hasAudioPermission && hasNotificationPermission && hasOverlayPermission
}

/**
 * Centralized Permission Handler for managing, checking, and launching intents
 * for RECORD_AUDIO, FOREGROUND_SERVICE_MICROPHONE, POST_NOTIFICATIONS, and SYSTEM_ALERT_WINDOW.
 */
object PermissionHandler {

    /**
     * Checks all permissions and returns an immutable [AppPermissionState].
     */
    fun checkPermissions(context: Context): AppPermissionState {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        val hasFgMicrophone = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                context,
                "android.permission.FOREGROUND_SERVICE_MICROPHONE"
            ) == PackageManager.PERMISSION_GRANTED || hasAudio
        } else {
            true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }

        return AppPermissionState(
            hasAudioPermission = hasAudio,
            hasNotificationPermission = hasNotifications,
            hasOverlayPermission = hasOverlay,
            hasForegroundMicrophonePermission = hasFgMicrophone,
            isBatteryOptimizationIgnored = isBatteryIgnored
        )
    }

    /**
     * Creates an Intent to navigate the user directly to the Draw Over Other Apps (SYSTEM_ALERT_WINDOW) settings screen.
     */
    fun createOverlayPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            createAppSettingsIntent(context)
        }
    }

    /**
     * Creates an Intent to navigate to the app's main system settings page.
     */
    fun createAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates an Intent to request ignoring battery optimization.
     */
    fun createBatteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            createAppSettingsIntent(context)
        }
    }

    /**
     * Returns list of runtime permissions that currently need to be requested via standard launcher.
     */
    fun getMissingRuntimePermissions(context: Context): List<String> {
        val list = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return list
    }
}
