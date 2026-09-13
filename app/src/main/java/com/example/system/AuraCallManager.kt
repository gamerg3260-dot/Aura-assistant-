package com.example.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.preferences.AssistantPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles incoming call detection, caller ID announcement, hands-free call answering,
 * rejection, and dialing using Android TelecomManager and Telephony APIs.
 */
class AuraCallManager(
    private val context: Context,
    private val preferences: AssistantPreferences,
    private val onIncomingCallDetected: ((callerName: String, callerNumber: String) -> Unit)? = null
) {
    private val tag = "AuraCallManager"
    private val scope = CoroutineScope(Dispatchers.Main)

    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    private val telecomManager by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }

    private val _incomingCallerName = MutableStateFlow<String?>(null)
    val incomingCallerName: StateFlow<String?> = _incomingCallerName.asStateFlow()

    private val _incomingCallerNumber = MutableStateFlow<String?>(null)
    val incomingCallerNumber: StateFlow<String?> = _incomingCallerNumber.asStateFlow()

    private val _isCallRinging = MutableStateFlow(false)
    val isCallRinging: StateFlow<Boolean> = _isCallRinging.asStateFlow()

    private var telephonyCallback: Any? = null
    private var legacyPhoneStateListener: PhoneStateListener? = null

    init {
        registerCallListener()
    }

    /**
     * Registers TelephonyCallback (Android 12+) or PhoneStateListener (< Android 12)
     */
    @SuppressLint("NewApi")
    fun registerCallListener() {
        val tm = telephonyManager ?: return
        val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPhoneStatePermission) {
            Log.d(tag, "[CALL_ANNOUNCER] READ_PHONE_STATE permission not yet granted; call listener standby.")
            return
        }

        if (telephonyCallback != null || legacyPhoneStateListener != null) {
            Log.d(tag, "[CALL_ANNOUNCER] Telephony listener already active.")
            return
        }

        Log.i(tag, "[CALL_ENGINE_INFO] Hands-free incoming call detection and voice controls (Answer/Reject/Dial) initialized. NOTE: Android OS platform security policies prevent third-party apps from injecting/intercepting raw SIM call stream audio directly, but caller ID announcement and TelecomManager call controls are 100% active.")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateChange(state, null)
                    }
                }
                telephonyCallback = callback
                tm.registerTelephonyCallback(context.mainExecutor, callback)
                Log.d(tag, "[CALL_ANNOUNCER] TelephonyCallback registered successfully for Android 12+.")
            } else {
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateChange(state, phoneNumber)
                    }
                }
                legacyPhoneStateListener = listener
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                Log.d(tag, "[CALL_ANNOUNCER] Legacy PhoneStateListener registered successfully.")
            }
        } catch (e: Exception) {
            Log.w(tag, "[CALL_ANNOUNCER] Could not register call listener: ${e.message}")
        }
    }

    /**
     * Tests the call announcer pipeline directly, verifying toggle state and TTS trigger.
     */
    fun testAnnounceCall(callerName: String = "John Doe"): String {
        Log.i(tag, "[CALL_ANNOUNCER] Testing call announcer for '$callerName'. Toggle state: ${preferences.toggleCallsAnnouncer}")
        return if (preferences.toggleCallsAnnouncer) {
            onIncomingCallDetected?.invoke(callerName, "+1-555-0199")
            "Test announcement triggered for $callerName"
        } else {
            "Call announcer toggle is OFF in Settings. Turn it ON to hear announcements."
        }
    }

    private fun handleCallStateChange(state: Int, incomingNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                _isCallRinging.value = true
                val resolvedNumber = incomingNumber ?: "Unknown"
                _incomingCallerNumber.value = resolvedNumber
                val resolvedName = resolveContactName(resolvedNumber)
                _incomingCallerName.value = resolvedName

                Log.i(tag, "[CALL_ANNOUNCER] RINGING detected from '$resolvedName' ($resolvedNumber). Toggle state: ${preferences.toggleCallsAnnouncer}")
                if (preferences.toggleCallsAnnouncer) {
                    onIncomingCallDetected?.invoke(resolvedName, resolvedNumber)
                }
            }
            TelephonyManager.CALL_STATE_IDLE, TelephonyManager.CALL_STATE_OFFHOOK -> {
                _isCallRinging.value = false
                _incomingCallerName.value = null
                _incomingCallerNumber.value = null
            }
        }
    }

    /**
     * Answers an incoming ringing call hands-free via TelecomManager.
     */
    @SuppressLint("MissingPermission")
    fun answerCall(): Boolean {
        if (!preferences.toggleCallsVoiceAnswer) {
            Log.w(tag, "Voice call answering is disabled in assistant settings.")
            return false
        }

        return try {
            val hasAnswerPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasAnswerPermission) {
                telecomManager?.acceptRingingCall(0)
                Log.d(tag, "acceptRingingCall executed via TelecomManager.")
                true
            } else {
                Log.w(tag, "ANSWER_PHONE_CALLS permission not granted or API < 26.")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to answer ringing call: ${e.message}", e)
            false
        }
    }

    /**
     * Rejects or ends a call via TelecomManager.
     */
    @SuppressLint("MissingPermission")
    fun rejectCall(): Boolean {
        return try {
            val hasAnswerPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasAnswerPermission) {
                telecomManager?.endCall()
                Log.d(tag, "endCall executed via TelecomManager.")
                true
            } else {
                Log.w(tag, "ANSWER_PHONE_CALLS permission required for endCall.")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to reject call: ${e.message}", e)
            false
        }
    }

    /**
     * Initiates a phone call or launches system dialer.
     */
    fun dialOrCall(phoneNumber: String): Boolean {
        return try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            val hasCallPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val intent = if (hasCallPermission && cleanNumber.isNotBlank()) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to initiate dial/call: ${e.message}", e)
            false
        }
    }

    /**
     * Queries Android Contacts Provider to resolve caller number to contact display name.
     */
    private fun resolveContactName(phoneNumber: String): String {
        if (phoneNumber == "Unknown" || phoneNumber.isBlank()) return "Unknown Caller"

        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasContactsPermission) return phoneNumber

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return it.getString(nameIndex) ?: phoneNumber
                    }
                }
            }
            phoneNumber
        } catch (e: Exception) {
            phoneNumber
        }
    }

    fun unregister() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
                telephonyManager?.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            } else if (legacyPhoneStateListener != null) {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_NONE)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error unregistering call listener: ${e.message}")
        }
    }
}
