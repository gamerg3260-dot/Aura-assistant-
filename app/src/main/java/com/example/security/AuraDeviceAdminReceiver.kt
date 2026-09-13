package com.example.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import com.example.AuraApplication

/**
 * DeviceAdminReceiver subclass detecting failed lock-screen password/PIN attempts.
 * Triggers background front-camera photo capture, owner face verification, and
 * intruder alarm/warnings when an unauthorized person attempts to unlock the device.
 */
class AuraDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordFailed(context, intent, user)
        Log.w(TAG, "🚨 [DEVICE_ADMIN] Lock screen password failure detected!")

        try {
            val app = context.applicationContext as? AuraApplication
            val prefs = app?.preferences

            if (prefs?.isDeviceAdminIntruderGuardEnabled == true) {
                Log.i(TAG, "[DEVICE_ADMIN] Intruder Guard is active. Delegating to TheftGuardManager.")
                app.theftGuardManager.handlePasswordFailedAttempt()
            } else {
                Log.d(TAG, "[DEVICE_ADMIN] Intruder Guard is disabled in settings. Ignoring password failure.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPasswordFailed: ${e.message}", e)
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordSucceeded(context, intent, user)
        Log.i(TAG, "✅ [DEVICE_ADMIN] Lock screen password succeeded. Resetting security flags.")

        try {
            val app = context.applicationContext as? AuraApplication
            app?.theftGuardManager?.handlePasswordSucceeded()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPasswordSucceeded: ${e.message}", e)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "[DEVICE_ADMIN] Aura Anti-Theft Device Admin enabled successfully.")
        val app = context.applicationContext as? AuraApplication
        app?.preferences?.isDeviceAdminIntruderGuardEnabled = true
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "[DEVICE_ADMIN] Aura Anti-Theft Device Admin disabled.")
        val app = context.applicationContext as? AuraApplication
        app?.preferences?.isDeviceAdminIntruderGuardEnabled = false
    }

    companion object {
        private const val TAG = "AuraDeviceAdminReceiver"
    }
}
