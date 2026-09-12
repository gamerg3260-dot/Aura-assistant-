package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.example.service.AuraVoiceService
import com.example.system.PermissionHandler
import com.example.ui.MainScreen
import com.example.ui.AuraViewModel
import com.example.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AuraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = (application as AuraApplication).preferences
        val permissionState = PermissionHandler.checkPermissions(this)
        if (prefs.isBackgroundServiceRunning && permissionState.hasAudioPermission) {
            try {
                AuraVoiceService.start(this)
            } catch (e: Exception) {
                // Logged or handled safely
            }
        }

        handleIncomingIntent(intent)

        setContent {
            AuraTheme {
                AuraRootApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra("AUTO_TRIGGER_LISTEN", false) == true) {
            val fromAssistant = intent.getBooleanExtra("FROM_DEFAULT_ASSISTANT", false)
            viewModel.triggerWakeWordAndListen(isOwner = true, isDirectInvocation = fromAssistant)
        }
    }
}

@Composable
fun AuraRootApp(viewModel: AuraViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshDeviceData()
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            val prefs = (context.applicationContext as AuraApplication).preferences
            if (prefs.isBackgroundServiceRunning) {
                try {
                    AuraVoiceService.start(context)
                } catch (e: Exception) {
                    // Handled safely
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val missing = PermissionHandler.getMissingRuntimePermissions(context)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    MainScreen(viewModel = viewModel)
}

