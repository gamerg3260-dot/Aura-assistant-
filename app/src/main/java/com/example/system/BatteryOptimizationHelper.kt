package com.example.system

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Utility object to inspect and manage Android Doze mode and battery optimization exemptions.
 *
 * Ensures the assistant's foreground service and background wake-word listening
 * operate continuously without being throttled or terminated by the OS when the screen is locked.
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptimization"

    /**
     * Checks if the application is currently exempt from battery optimizations.
     *
     * @param context The application or component context.
     * @return `true` if the app is already whitelisted / exempt from battery optimization,
     *         `false` otherwise.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val packageName = context.packageName

        return try {
            powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery optimization status", e)
            false
        }
    }

    /**
     * Creates an Intent to prompt the user to exempt the application from battery optimizations
     * using [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS].
     *
     * If the system does not support or denies direct package-targeted request, falls back to
     * [Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS].
     *
     * @param context The application or component context.
     * @return An [Intent] configured to request exemption.
     */
    @SuppressLint("BatteryLife")
    fun createRequestIgnoreBatteryOptimizationIntent(context: Context): Intent {
        val packageName = context.packageName

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create direct request intent, falling back to settings list", e)
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Requests battery optimization exemption by launching the appropriate system dialog or settings screen.
     *
     * @param context The context used to start the activity.
     * @return `true` if the intent was successfully dispatched, `false` otherwise.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (isIgnoringBatteryOptimizations(context)) {
            Log.d(TAG, "App is already exempt from battery optimizations.")
            return true
        }

        val intent = createRequestIgnoreBatteryOptimizationIntent(context)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Direct exemption activity not found, opening general battery settings", e)
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed to open battery optimization settings", fallbackEx)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error launching battery optimization intent", e)
            false
        }
    }

    /**
     * Directly opens the system's battery optimization whitelist settings page.
     *
     * @param context The context used to start the activity.
     * @return `true` if the settings screen opened successfully, `false` otherwise.
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open general battery optimization settings", e)
            false
        }
    }
}
