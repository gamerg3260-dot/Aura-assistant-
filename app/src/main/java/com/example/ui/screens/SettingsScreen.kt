package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AuraApplication
import com.example.service.AuraVoiceService
import com.example.ui.AuraViewModel
import com.example.ui.components.AuraGlassCard
import com.example.ui.theme.AuraCardBorder
import com.example.ui.theme.AuraCyanBright
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraDarkSurface
import com.example.ui.theme.AuraError
import com.example.ui.theme.AuraPinkTertiary
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraVioletSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: AuraViewModel,
    onNavigateToEnrollment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = AuraApplication.instance
    val prefs = app.preferences
    val isEnrolled by viewModel.isVoiceEnrolled.collectAsState()
    val sensitivity by viewModel.voiceSensitivity.collectAsState()
    val isBgServiceEnabled by viewModel.isBackgroundServiceEnabled.collectAsState()
    val usePorcupine by viewModel.usePorcupineWakeWord.collectAsState()
    val picovoiceKey by viewModel.picovoiceAccessKey.collectAsState()
    val porcupineKeyword by viewModel.selectedPorcupineKeyword.collectAsState()
    val porcupineSensitivity by viewModel.porcupineSensitivity.collectAsState()
    val isPorcupineActive by viewModel.isPorcupineActive.collectAsState()
    val activeWakeWordEngine by viewModel.activeWakeWordEngine.collectAsState()
    val activeVoiceName by viewModel.activeVoiceName.collectAsState()
    val speechPitch by viewModel.speechPitch.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()

    // Custom Voice Cloning states
    val isCustomVoiceEnabled by viewModel.isCustomVoiceEnabled.collectAsState()
    val customVoiceProfiles by viewModel.customVoiceProfiles.collectAsState()
    val activeCustomVoiceProfile by viewModel.activeCustomVoiceProfile.collectAsState()
    val isRecordingVoiceSample by viewModel.isRecordingVoiceSample.collectAsState()
    val isPlayingSample by viewModel.isPlayingSample.collectAsState()

    // Continuous Conversation States
    var isContinuousEnabled by remember { mutableStateOf(prefs.isContinuousConversationEnabled) }
    var continuousSilenceTimeout by remember { mutableStateOf(prefs.continuousSilenceTimeoutSeconds.toFloat()) }

    // Floating Bubble Overlay State
    var isFloatingBubbleEnabled by remember { mutableStateOf(prefs.isFloatingBubbleEnabled) }

    // Device Admin Intruder Guard & Face Enrollment States
    var isDeviceAdminEnabled by remember { mutableStateOf(prefs.isDeviceAdminIntruderGuardEnabled) }
    val isOwnerFaceEnrolled by viewModel.isOwnerFaceEnrolled.collectAsState()

    val context = LocalContext.current
    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val enrolled = viewModel.enrollOwnerFace(bitmap)
            if (enrolled) {
                android.widget.Toast.makeText(context, "Owner face enrolled successfully!", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Face enrollment failed. Ensure face is clear.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showImportNameDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var importVoiceNameInput by remember { mutableStateOf("") }

    var showRecordDialog by remember { mutableStateOf(false) }
    var recordVoiceNameInput by remember { mutableStateOf("") }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri
            importVoiceNameInput = "Voice Sample ${customVoiceProfiles.size + 1}"
            showImportNameDialog = true
        }
    }

    // Expansion states for category cards
    val expandedCategories = remember {
        mutableStateMapOf(
            1 to true,
            2 to false,
            3 to false,
            4 to false,
            5 to false,
            6 to false,
            7 to false,
            8 to false,
            9 to false,
            10 to false
        )
    }

    // Local toggle mirror states for real-time reactivity
    var sysVol by remember { mutableStateOf(prefs.toggleSystemControlVolume) }
    var sysBright by remember { mutableStateOf(prefs.toggleSystemControlBrightness) }
    var sysWifi by remember { mutableStateOf(prefs.toggleSystemControlWifiBt) }
    var sysApps by remember { mutableStateOf(prefs.toggleSystemControlAppLauncher) }

    var commPhone by remember { mutableStateOf(prefs.toggleCommPhoneCalls) }
    var commSms by remember { mutableStateOf(prefs.toggleCommSms) }
    var commWa by remember { mutableStateOf(prefs.toggleCommWhatsapp) }

    var infoWeather by remember { mutableStateOf(prefs.toggleInfoWeather) }
    var infoDevice by remember { mutableStateOf(prefs.toggleInfoDeviceStatus) }
    var infoLoc by remember { mutableStateOf(prefs.toggleInfoLocation) }

    var mediaSpotify by remember { mutableStateOf(prefs.toggleMediaSpotify) }
    var mediaYt by remember { mutableStateOf(prefs.toggleMediaYoutube) }

    var prodRemind by remember { mutableStateOf(prefs.toggleProdReminders) }
    var prodTimers by remember { mutableStateOf(prefs.toggleProdTimers) }
    var prodMemory by remember { mutableStateOf(prefs.toggleProdMemory) }

    var visCam by remember { mutableStateOf(prefs.toggleVisionCamera) }
    var visScreen by remember { mutableStateOf(prefs.toggleVisionScreenReading) }
    var visObj by remember { mutableStateOf(prefs.toggleVisionObjectRecognition) }

    var webSearch by remember { mutableStateOf(prefs.toggleWebGoogleSearch) }

    var secTheft by remember { mutableStateOf(prefs.toggleSecurityAntiTheft) }
    var secIntruder by remember { mutableStateOf(prefs.toggleSecurityIntruderAlert) }

    var callAnnounce by remember { mutableStateOf(prefs.toggleCallsAnnouncer) }
    var callVoiceAnswer by remember { mutableStateOf(prefs.toggleCallsVoiceAnswer) }

    var contactsPrio by remember { mutableStateOf(prefs.toggleContactsPriority) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = AuraCyanPrimary, modifier = Modifier.size(24.dp))
                Text(
                    text = "Assistant Preferences",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Configure modular tool execution, background service persistence, and on-device voice biometric lock.",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Section: LLM & Gemini API Configuration
        item {
            val hasConfiguredKey by viewModel.hasConfiguredApiKey.collectAsState()
            val isValidatingKey by viewModel.isValidatingApiKey.collectAsState()
            val apiKeyError by viewModel.apiKeyError.collectAsState()
            val apiKeySuccess by viewModel.apiKeySuccessMessage.collectAsState()
            val currentKeyInput by viewModel.tempApiKey.collectAsState()

            var apiKeyFieldText by remember(currentKeyInput) { mutableStateOf(currentKeyInput) }
            var isApiKeyMasked by remember { mutableStateOf(true) }

            Text(
                text = "LLM & GEMINI API CONFIGURATION",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (hasConfiguredKey) AuraSuccess.copy(alpha = 0.15f) else AuraCyanPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = if (hasConfiguredKey) AuraSuccess else AuraCyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text("Gemini API Engine", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = if (hasConfiguredKey) "Hardware Keystore Active (AES-256)" else "No key configured (Offline rule engine fallback)",
                                    color = if (hasConfiguredKey) AuraSuccess else TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (hasConfiguredKey) AuraSuccess.copy(alpha = 0.15f) else AuraDarkSurface)
                                .border(1.dp, if (hasConfiguredKey) AuraSuccess.copy(alpha = 0.3f) else AuraCardBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hasConfiguredKey) "VALIDATED" else "OFFLINE",
                                color = if (hasConfiguredKey) AuraSuccess else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = apiKeyFieldText,
                        onValueChange = {
                            apiKeyFieldText = it
                            viewModel.setTempApiKey(it)
                        },
                        placeholder = { Text("AIzaSy... (Gemini / Claude API Key)", color = TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        isError = apiKeyError != null,
                        visualTransformation = if (isApiKeyMasked) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyMasked = !isApiKeyMasked }) {
                                Icon(
                                    imageVector = if (isApiKeyMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isApiKeyMasked) "Show Key" else "Hide Key",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (apiKeyError != null) AuraError else AuraCyanPrimary,
                            unfocusedBorderColor = if (apiKeyError != null) AuraError else AuraCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = AuraDarkSurface,
                            unfocusedContainerColor = AuraDarkSurface
                        )
                    )

                    if (apiKeyError != null) {
                        Text(
                            text = apiKeyError ?: "",
                            color = AuraError,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }

                    if (apiKeySuccess != null) {
                        Text(
                            text = apiKeySuccess ?: "",
                            color = AuraSuccess,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.validateAndSaveApiKey(apiKeyFieldText)
                            },
                            enabled = !isValidatingKey && apiKeyFieldText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuraCyanPrimary,
                                contentColor = Color(0xFF070B13),
                                disabledContainerColor = AuraCyanPrimary.copy(alpha = 0.4f),
                                disabledContentColor = Color(0xFF070B13).copy(alpha = 0.4f)
                            )
                        ) {
                            if (isValidatingKey) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF070B13),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verifying...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Validate & Save Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (hasConfiguredKey || apiKeyFieldText.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    apiKeyFieldText = ""
                                    viewModel.clearApiKey()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = AuraError, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear", color = AuraError, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Section: Voice Biometrics
        item {
            Text(
                text = "VOICE BIOMETRICS & LOCK",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Voice-Lock Status", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = if (isEnrolled) "Calibrated to ${prefs.userName}'s voice" else "No voiceprint enrolled",
                                color = if (isEnrolled) AuraSuccess else AuraError,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isEnrolled) AuraSuccess.copy(alpha = 0.15f) else AuraError.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isEnrolled) "ENROLLED" else "UNENROLLED",
                                color = if (isEnrolled) AuraSuccess else AuraError,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Sensitivity slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Similarity Threshold", color = TextSecondary, fontSize = 12.sp)
                            Text("${(sensitivity * 100).toInt()}%", color = AuraCyanPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Slider(
                            value = sensitivity,
                            onValueChange = { viewModel.setVoiceSensitivity(it) },
                            valueRange = 0.60f..0.92f,
                            colors = SliderDefaults.colors(
                                thumbColor = AuraCyanPrimary,
                                activeTrackColor = AuraCyanPrimary,
                                inactiveTrackColor = AuraCardBorder
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateToEnrollment,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuraCyanPrimary,
                                contentColor = Color(0xFF070B13)
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-Enroll", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetEnrollment() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = AuraError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Voice", color = AuraError, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section: Continuous Conversation
        item {
            Text(
                text = "CONTINUOUS CONVERSATION",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Siri-Style Active Session", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "Keep listening back-and-forth after wake word triggers without repeating 'Hey Aura'",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isContinuousEnabled,
                            onCheckedChange = {
                                isContinuousEnabled = it
                                prefs.isContinuousConversationEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AuraCyanPrimary,
                                checkedTrackColor = AuraCyanPrimary.copy(alpha = 0.4f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = AuraCardBorder
                            )
                        )
                    }

                    AnimatedVisibility(visible = isContinuousEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Speech Silence Timeout", color = TextSecondary, fontSize = 12.sp)
                                Text("${continuousSilenceTimeout.toInt()}s", color = AuraCyanPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Slider(
                                value = continuousSilenceTimeout,
                                onValueChange = {
                                    continuousSilenceTimeout = it
                                    prefs.continuousSilenceTimeoutSeconds = it.toInt()
                                },
                                valueRange = 4f..12f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AuraCyanPrimary,
                                    activeTrackColor = AuraCyanPrimary,
                                    inactiveTrackColor = AuraCardBorder
                                )
                            )
                            Text(
                                text = "Tips: Say 'stop', 'bye', or 'cancel' at any time during an active session to exit instantly.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Section: Floating Assistant Bubble
        item {
            Text(
                text = "FLOATING ASSISTANT BUBBLE",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Floating Overlay Bubble", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "Display a draggable bubble over other apps showing real-time listening and speaking animations.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isFloatingBubbleEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                    android.widget.Toast.makeText(context, "Please grant overlay permission for Aura first.", android.widget.Toast.LENGTH_LONG).show()
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                } else {
                                    isFloatingBubbleEnabled = true
                                    prefs.isFloatingBubbleEnabled = true
                                    AuraVoiceService.updateFloatingBubbleVisibility(context)
                                }
                            } else {
                                isFloatingBubbleEnabled = false
                                prefs.isFloatingBubbleEnabled = false
                                AuraVoiceService.updateFloatingBubbleVisibility(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AuraCyanPrimary,
                            checkedTrackColor = AuraCyanPrimary.copy(alpha = 0.4f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = AuraCardBorder
                        )
                    )
                }
            }
        }

        // Section: Voice Persona & Custom Voice Cloning
        item {
            Text(
                text = "VOICE PERSONA & CUSTOM VOICE CLONING",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Title row with switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AuraPinkTertiary.copy(alpha = 0.15f))
                                    .border(1.dp, AuraPinkTertiary.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = AuraPinkTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Voice Synthesis & Cloning",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isCustomVoiceEnabled) "Custom Voice Clone Active" else "Standard Neural Female Voice Active",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = isCustomVoiceEnabled,
                            onCheckedChange = { viewModel.toggleCustomVoice(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AuraPinkTertiary,
                                checkedTrackColor = AuraPinkTertiary.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = AuraDarkSurface
                            )
                        )
                    }

                    Text(
                        text = "Upload any person's audio sample (.mp3, .wav, .m4a) or record a live voice sample to clone their voice tone for your assistant.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    // Mode Selection Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AuraDarkSurface)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isCustomVoiceEnabled) AuraPinkTertiary.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (!isCustomVoiceEnabled) AuraPinkTertiary.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleCustomVoice(false) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Female Neural Voice",
                                fontSize = 11.sp,
                                fontWeight = if (!isCustomVoiceEnabled) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isCustomVoiceEnabled) AuraPinkTertiary else TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCustomVoiceEnabled) AuraCyanPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (isCustomVoiceEnabled) AuraCyanPrimary.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleCustomVoice(true) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Custom Voice Clone",
                                fontSize = 11.sp,
                                fontWeight = if (isCustomVoiceEnabled) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCustomVoiceEnabled) AuraCyanPrimary else TextSecondary
                            )
                        }
                    }

                    // Active Voice Engine Chip
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AuraDarkSurface)
                            .border(1.dp, AuraCardBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isCustomVoiceEnabled) AuraCyanPrimary else AuraPinkTertiary)
                            )
                            Text(
                                text = if (isCustomVoiceEnabled && activeCustomVoiceProfile != null) {
                                    "Active Cloned Voice: ${activeCustomVoiceProfile?.name}"
                                } else {
                                    "Active Voice: $activeVoiceName"
                                },
                                color = if (isCustomVoiceEnabled) AuraCyanPrimary else AuraPinkTertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Upload & Record Actions Section (When Custom Voice is Selected)
                    AnimatedVisibility(visible = isCustomVoiceEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { audioPickerLauncher.launch("audio/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraCyanPrimary)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Upload Audio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        recordVoiceNameInput = "Live Voice ${customVoiceProfiles.size + 1}"
                                        showRecordDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyanPrimary, contentColor = Color(0xFF070B13))
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Record Live Voice", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Profiles List
                            if (customVoiceProfiles.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AuraDarkSurface.copy(alpha = 0.5f))
                                        .border(1.dp, AuraCardBorder, RoundedCornerShape(8.dp))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No custom voice sample uploaded yet.\nTap 'Upload Audio' or 'Record Live Voice' to add anyone's voice!",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("SAVED CUSTOM VOICE PROFILES", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)

                                    customVoiceProfiles.forEach { profile ->
                                        val isSelected = activeCustomVoiceProfile?.id == profile.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) AuraCyanPrimary.copy(alpha = 0.12f) else AuraDarkSurface)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) AuraCyanPrimary.copy(alpha = 0.5f) else AuraCardBorder,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.selectCustomVoice(profile.id) }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = if (isSelected) AuraCyanPrimary else TextMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )

                                                Column {
                                                    Text(
                                                        text = profile.name,
                                                        color = if (isSelected) TextPrimary else TextSecondary,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    Text(
                                                        text = "${profile.durationMs / 1000}s sample • Pitch: ${String.format("%.2f", profile.pitch)}x",
                                                        color = TextMuted,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = { viewModel.playCustomVoiceSample(profile) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.VolumeUp,
                                                        contentDescription = "Play Sample",
                                                        tint = AuraCyanPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { viewModel.deleteCustomVoice(profile.id) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Profile",
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pitch Calibration Slider
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isCustomVoiceEnabled) "Cloned Voice Tone / Pitch" else "Female Voice Pitch",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                String.format("%.2fx", speechPitch),
                                color = if (isCustomVoiceEnabled) AuraCyanPrimary else AuraPinkTertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Slider(
                            value = speechPitch,
                            onValueChange = { viewModel.setSpeechPitch(it) },
                            valueRange = 0.7f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = if (isCustomVoiceEnabled) AuraCyanPrimary else AuraPinkTertiary,
                                activeTrackColor = if (isCustomVoiceEnabled) AuraCyanPrimary else AuraPinkTertiary,
                                inactiveTrackColor = AuraCardBorder
                            )
                        )
                    }

                    // Speech Rate Slider
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speech Speed / Tempo", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                String.format("%.2fx", speechRate),
                                color = if (isCustomVoiceEnabled) AuraCyanPrimary else AuraPinkTertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Slider(
                            value = speechRate,
                            onValueChange = { viewModel.setSpeechRate(it) },
                            valueRange = 0.7f..1.4f,
                            colors = SliderDefaults.colors(
                                thumbColor = if (isCustomVoiceEnabled) AuraCyanPrimary else AuraPinkTertiary,
                                activeTrackColor = if (isCustomVoiceEnabled) AuraCyanPrimary else AuraPinkTertiary,
                                inactiveTrackColor = AuraCardBorder
                            )
                        )
                    }

                    Button(
                        onClick = { viewModel.testCustomClonedVoice() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCustomVoiceEnabled) AuraCyanPrimary else AuraPinkTertiary,
                            contentColor = Color(0xFF070B13)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCustomVoiceEnabled) "Preview Cloned Voice Response" else "Preview Natural Female Voice",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Section: Picovoice Porcupine Wake Word Engine
        item {
            var keyText by remember(picovoiceKey) { mutableStateOf(picovoiceKey) }
            var isKeyVisible by remember { mutableStateOf(false) }

            Text(
                text = "WAKE WORD ENGINE & HARDWARE ACCELERATION",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Title row with switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isPorcupineActive) AuraSuccess.copy(alpha = 0.15f) else AuraCyanPrimary.copy(alpha = 0.15f))
                                    .border(1.dp, if (isPorcupineActive) AuraSuccess.copy(alpha = 0.5f) else AuraCyanPrimary.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = if (isPorcupineActive) AuraSuccess else AuraCyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Picovoice Porcupine SDK",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (isPorcupineActive) "Hardware DSP Active • Zero CPU Idle" else "Micro-power on-device keyword spotter",
                                    color = if (isPorcupineActive) AuraSuccess else TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = usePorcupine,
                            onCheckedChange = { viewModel.setUsePorcupineWakeWord(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF070B13),
                                checkedTrackColor = AuraCyanPrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = AuraDarkSurface
                            )
                        )
                    }

                    // Engine Status Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isPorcupineActive) AuraSuccess.copy(alpha = 0.12f)
                                else AuraDarkSurface
                            )
                            .border(
                                1.dp,
                                if (isPorcupineActive) AuraSuccess.copy(alpha = 0.35f)
                                else AuraCardBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isPorcupineActive) AuraSuccess else AuraPinkTertiary)
                                )
                                Text(
                                    text = "Engine: $activeWakeWordEngine",
                                    color = if (isPorcupineActive) AuraSuccess else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = if (isPorcupineActive) {
                                    "Actively spotting '$porcupineKeyword' with micro-power audio framing."
                                } else if (picovoiceKey.isBlank()) {
                                    "No AccessKey configured. Operating on Aura's native on-device acoustic RMS engine."
                                } else {
                                    "Porcupine standby or native fallback active."
                                },
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (usePorcupine) {
                        // Keyword Selection Chips
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Built-in Keyword Spotter",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val popularKeywords = listOf("JARVIS", "PORCUPINE", "COMPUTER", "BUMBLEBEE")
                                popularKeywords.forEach { kw ->
                                    val isSelected = porcupineKeyword.equals(kw, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) AuraCyanPrimary.copy(alpha = 0.2f) else AuraDarkSurface
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) AuraCyanPrimary else AuraCardBorder,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                viewModel.setSelectedPorcupineKeyword(kw)
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = kw,
                                            color = if (isSelected) AuraCyanPrimary else TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Sensitivity Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Detection Sensitivity", color = TextSecondary, fontSize = 12.sp)
                                Text("${(porcupineSensitivity * 100).toInt()}%", color = AuraCyanPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Slider(
                                value = porcupineSensitivity,
                                onValueChange = { viewModel.setPorcupineSensitivity(it) },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AuraCyanPrimary,
                                    activeTrackColor = AuraCyanPrimary,
                                    inactiveTrackColor = AuraCardBorder
                                )
                            )
                        }

                        // Picovoice AccessKey Input
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Picovoice AccessKey",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (picovoiceKey.isNotBlank()) "Configured" else "Optional (Free)",
                                    color = if (picovoiceKey.isNotBlank()) AuraSuccess else TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            OutlinedTextField(
                                value = keyText,
                                onValueChange = {
                                    keyText = it
                                    viewModel.setPicovoiceAccessKey(it.trim())
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text("Paste Picovoice AccessKey from console.picovoice.ai", color = TextMuted, fontSize = 11.sp)
                                },
                                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                        Icon(
                                            imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Key Visibility",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AuraCyanPrimary,
                                    unfocusedBorderColor = AuraCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedContainerColor = AuraDarkSurface,
                                    unfocusedContainerColor = AuraDarkSurface
                                )
                            )

                            Text(
                                text = "Obtain a free AccessKey at console.picovoice.ai. When blank or invalid, Aura automatically runs its zero-configuration on-device acoustic engine.",
                                color = TextMuted,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Section: Default Digital Assistant App Configuration (Matches Android System Settings)
        item {
            val context = LocalContext.current
            val isDefaultAssistant = remember {
                com.example.service.voiceinteraction.AuraVoiceInteractionService.isSelectedAsDefaultAssistant(context)
            }

            Text(
                text = "DEFAULT DIGITAL ASSISTANT INTEGRATION",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isDefaultAssistant) AuraSuccess.copy(alpha = 0.15f) else AuraCyanPrimary.copy(alpha = 0.15f))
                                    .border(1.dp, if (isDefaultAssistant) AuraSuccess.copy(alpha = 0.5f) else AuraCyanPrimary.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isDefaultAssistant) Icons.Default.CheckCircle else Icons.Default.Assistant,
                                    contentDescription = null,
                                    tint = if (isDefaultAssistant) AuraSuccess else AuraCyanPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Default Assistant App",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isDefaultAssistant) AuraSuccess.copy(alpha = 0.2f) else AuraCyanPrimary.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isDefaultAssistant) "DEFAULT SET" else "SELECT IN SETTINGS",
                                            color = if (isDefaultAssistant) AuraSuccess else AuraCyanBright,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = "Appear alongside Google, ChatGPT, and Claude in Android's 'Default digital assistant app' menu.",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Setting Aura as your default assistant allows instant wake-up via home button long-press, power button hold, swipe gesture, or wake words ('Hey Aura' / 'Hey Google') just like Google Assistant.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val fallbackIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(fallbackIntent)
                                } catch (e2: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDefaultAssistant) AuraDarkSurface else AuraCyanPrimary,
                            contentColor = if (isDefaultAssistant) AuraCyanBright else Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Launch,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isDefaultAssistant) "Open Default Assistant Settings" else "Set as Default Assistant App",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Section: Permission Security & Management
        item {
            com.example.ui.components.PermissionHandlerCard()
        }

        // Section: Foreground Service & Battery Optimization
        item {
            val context = LocalContext.current
            val isMuted by viewModel.isServiceMuted.collectAsState()
            val isIgnoringBattery = remember(isBgServiceEnabled) {
                AuraVoiceService.isIgnoringBatteryOptimizations(context)
            }
            var showSettingsPermissionSheet by remember { mutableStateOf(false) }

            com.example.ui.components.PermissionHandlerSheet(
                isVisible = showSettingsPermissionSheet,
                onDismiss = { showSettingsPermissionSheet = false },
                onPermissionsCompleted = {
                    showSettingsPermissionSheet = false
                    viewModel.toggleBackgroundService(true)
                }
            )

            Text(
                text = "FOREGROUND SERVICE ARCHITECTURE & PERSISTENCE",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Main Service Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Aura Voice Service", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isBgServiceEnabled) AuraSuccess.copy(alpha = 0.2f) else AuraError.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                    Text(
                                        text = if (isBgServiceEnabled) "RUNNING" else "STOPPED",
                                        color = if (isBgServiceEnabled) AuraSuccess else AuraError,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Foreground Service with FOREGROUND_SERVICE_TYPE_MICROPHONE and START_STICKY lifecycle recovery.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Switch(
                            checked = isBgServiceEnabled,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    val permState = com.example.system.PermissionHandler.checkPermissions(context)
                                    if (permState.hasAudioPermission) {
                                        viewModel.toggleBackgroundService(true)
                                    } else {
                                        showSettingsPermissionSheet = true
                                    }
                                } else {
                                    viewModel.toggleBackgroundService(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AuraCyanPrimary,
                                checkedTrackColor = AuraCyanPrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = AuraDarkSurface
                            )
                        )
                    }

                    // Notification & Controls
                    if (isBgServiceEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AuraDarkSurface.copy(alpha = 0.8f))
                                .border(1.dp, AuraCardBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = AuraCyanPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Active Persistent Notification Tray",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = "The persistent notification runs on low importance (silent) with ongoing lock-screen actions: [Listen] [${if (isMuted) "Resume" else "Mute"}] [Stop].",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.toggleServiceMute() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (isMuted) AuraCyanPrimary else TextSecondary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isMuted) "Unmute Guard" else "Mute Guard", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = { viewModel.triggerListenFromService() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AuraCyanPrimary,
                                            contentColor = Color(0xFF070B13)
                                        )
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Test Trigger", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Battery Optimization & Doze Mode Exemption
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AuraDarkSurface)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.BatteryChargingFull,
                                        contentDescription = null,
                                        tint = if (isIgnoringBattery) AuraSuccess else AuraPinkTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Battery Optimization (Doze Mode)",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }

                                Text(
                                    text = if (isIgnoringBattery) "Unrestricted" else "Optimized",
                                    color = if (isIgnoringBattery) AuraSuccess else AuraPinkTertiary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = if (isIgnoringBattery) {
                                    "✓ Aura is exempt from battery optimization. The background service will run uninterrupted without being halted by Android Doze."
                                } else {
                                    "Android may throttle wake-word listening when the screen is turned off. Set battery to 'Unrestricted' for continuous operation."
                                },
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            if (!isIgnoringBattery) {
                                Button(
                                    onClick = {
                                        com.example.system.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AuraVioletSecondary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Unrestricted Battery Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Auto-boot & task persistence info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Power, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Auto-starts on system boot (RECEIVE_BOOT_COMPLETED) and schedules instant recovery if app is swiped from recents.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Section: 10 Feature Categories with Master & Sub-toggles
        item {
            Text(
                text = "MODULAR CAPABILITY TOGGLES (10 CATEGORIES)",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        // 1. System Control
        item {
            CategoryToggleCard(
                categoryIndex = 1,
                title = "1. System Control",
                subtitle = "Volume, brightness, Wi-Fi, app launching, flashlight",
                icon = Icons.Default.PowerSettingsNew,
                isExpanded = expandedCategories[1] == true,
                onToggleExpand = { expandedCategories[1] = !(expandedCategories[1] ?: false) }
            ) {
                SubToggleRow("Media Volume Adjustment", sysVol) {
                    sysVol = it
                    prefs.toggleSystemControlVolume = it
                }
                SubToggleRow("Display Brightness", sysBright) {
                    sysBright = it
                    prefs.toggleSystemControlBrightness = it
                }
                SubToggleRow("Wi-Fi & Bluetooth Settings", sysWifi) {
                    sysWifi = it
                    prefs.toggleSystemControlWifiBt = it
                }
                SubToggleRow("App Launcher (YouTube, Spotify...)", sysApps) {
                    sysApps = it
                    prefs.toggleSystemControlAppLauncher = it
                }
            }
        }

        // 2. Communication
        item {
            CategoryToggleCard(
                categoryIndex = 2,
                title = "2. Communication",
                subtitle = "Direct phone calls, SMS messages, WhatsApp automation",
                icon = Icons.Default.Call,
                isExpanded = expandedCategories[2] == true,
                onToggleExpand = { expandedCategories[2] = !(expandedCategories[2] ?: false) }
            ) {
                SubToggleRow("Direct Phone Calls", commPhone) {
                    commPhone = it
                    prefs.toggleCommPhoneCalls = it
                }
                SubToggleRow("SMS Dispatch", commSms) {
                    commSms = it
                    prefs.toggleCommSms = it
                }
                SubToggleRow("WhatsApp Integration", commWa) {
                    commWa = it
                    prefs.toggleCommWhatsapp = it
                }
            }
        }

        // 3. Info & Telemetry
        item {
            CategoryToggleCard(
                categoryIndex = 3,
                title = "3. Information & Stats",
                subtitle = "Live weather, device diagnostics, location lookups",
                icon = Icons.Default.Info,
                isExpanded = expandedCategories[3] == true,
                onToggleExpand = { expandedCategories[3] = !(expandedCategories[3] ?: false) }
            ) {
                SubToggleRow("Weather Intelligence", infoWeather) {
                    infoWeather = it
                    prefs.toggleInfoWeather = it
                }
                SubToggleRow("Hardware Health & Battery Telemetry", infoDevice) {
                    infoDevice = it
                    prefs.toggleInfoDeviceStatus = it
                }
                SubToggleRow("Reverse-Geocoded Location", infoLoc) {
                    infoLoc = it
                    prefs.toggleInfoLocation = it
                }
            }
        }

        // 4. Media
        item {
            CategoryToggleCard(
                categoryIndex = 4,
                title = "4. Media Playback",
                subtitle = "Spotify playback, YouTube content searches",
                icon = Icons.Default.MusicNote,
                isExpanded = expandedCategories[4] == true,
                onToggleExpand = { expandedCategories[4] = !(expandedCategories[4] ?: false) }
            ) {
                SubToggleRow("Spotify Deep-Link Playback", mediaSpotify) {
                    mediaSpotify = it
                    prefs.toggleMediaSpotify = it
                }
                SubToggleRow("YouTube Search & Launch", mediaYt) {
                    mediaYt = it
                    prefs.toggleMediaYoutube = it
                }
            }
        }

        // 5. Productivity
        item {
            CategoryToggleCard(
                categoryIndex = 5,
                title = "5. Productivity & Memory",
                subtitle = "Reminders, countdown timers, assistant memory facts",
                icon = Icons.Default.Timer,
                isExpanded = expandedCategories[5] == true,
                onToggleExpand = { expandedCategories[5] = !(expandedCategories[5] ?: false) }
            ) {
                SubToggleRow("Calendar & Reminders", prodRemind) {
                    prodRemind = it
                    prefs.toggleProdReminders = it
                }
                SubToggleRow("System Alarms & Timers", prodTimers) {
                    prodTimers = it
                    prefs.toggleProdTimers = it
                }
                SubToggleRow("Persistent Assistant Memory (Room DB)", prodMemory) {
                    prodMemory = it
                    prefs.toggleProdMemory = it
                }
            }
        }

        // 6. Vision & Accessibility
        item {
            CategoryToggleCard(
                categoryIndex = 6,
                title = "6. Vision & Screen Reading",
                subtitle = "Camera capture, AccessibilityService active screen reader",
                icon = Icons.Default.Visibility,
                isExpanded = expandedCategories[6] == true,
                onToggleExpand = { expandedCategories[6] = !(expandedCategories[6] ?: false) }
            ) {
                SubToggleRow("Camera Triggering", visCam) {
                    visCam = it
                    prefs.toggleVisionCamera = it
                }
                SubToggleRow("Accessibility Screen Reading & Siri Analysis", visScreen) {
                    visScreen = it
                    prefs.toggleVisionScreenReading = it
                }
                SubToggleRow("Object & Scene Description", visObj) {
                    visObj = it
                    prefs.toggleVisionObjectRecognition = it
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.analyzeActiveScreen() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analyze Active Screen Now", fontSize = 12.sp, color = AuraCyanPrimary)
                    }
                }
            }
        }

        // 7. Web
        item {
            CategoryToggleCard(
                categoryIndex = 7,
                title = "7. Web Queries",
                subtitle = "Google Search queries and knowledge synthesis",
                icon = Icons.Default.Search,
                isExpanded = expandedCategories[7] == true,
                onToggleExpand = { expandedCategories[7] = !(expandedCategories[7] ?: false) }
            ) {
                SubToggleRow("Google Search Dispatch", webSearch) {
                    webSearch = it
                    prefs.toggleWebGoogleSearch = it
                }
            }
        }

        // 8. Security
        item {
            CategoryToggleCard(
                categoryIndex = 8,
                title = "8. Security & Theft Guard",
                subtitle = "Anti-theft motion sensor, charger unplug siren, voice intruder alarms",
                icon = Icons.Default.Security,
                isExpanded = expandedCategories[8] == true,
                onToggleExpand = { expandedCategories[8] = !(expandedCategories[8] ?: false) }
            ) {
                SubToggleRow("Anti-Theft Motion & Charger Siren", secTheft) {
                    secTheft = it
                    prefs.toggleSecurityAntiTheft = it
                    if (it) viewModel.armTheftGuard() else viewModel.disarmTheftGuard()
                }
                SubToggleRow("Intruder Voice-Mismatch Alert Logging", secIntruder) {
                    secIntruder = it
                    prefs.toggleSecurityIntruderAlert = it
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("SMART LOCK-SCREEN INTRUDER GUARD", color = AuraCyanPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp)
                
                SubToggleRow("Enable Lock-Screen Guard (Device Admin)", isDeviceAdminEnabled) { checked ->
                    isDeviceAdminEnabled = checked
                    prefs.isDeviceAdminIntruderGuardEnabled = checked
                    if (checked) {
                        try {
                            val componentName = android.content.ComponentName(context, com.example.security.AuraDeviceAdminReceiver::class.java)
                            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                                putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Aura uses Device Administrator to detect failed lock screen password attempts and guard against device theft.")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Failed to launch device admin settings.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("OWNER FACE CALIBRATION", color = AuraCyanPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Face Recognition Calibration", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(
                            text = if (isOwnerFaceEnrolled) "Face footprint registered securely" else "No face footprints enrolled yet",
                            color = if (isOwnerFaceEnrolled) AuraSuccess else TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isOwnerFaceEnrolled) AuraSuccess.copy(alpha = 0.15f) else AuraError.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isOwnerFaceEnrolled) "CALIBRATED" else "NOT ENROLLED",
                            color = if (isOwnerFaceEnrolled) AuraSuccess else AuraError,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { takePicturePreviewLauncher.launch(null) },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuraCyanPrimary,
                            contentColor = Color(0xFF070B13)
                        )
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isOwnerFaceEnrolled) "Re-Calibrate" else "Calibrate Face", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (isOwnerFaceEnrolled) {
                        OutlinedButton(
                            onClick = { viewModel.deleteOwnerFace() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = AuraError, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Face", color = AuraError, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (secTheft) {
                                viewModel.disarmTheftGuard()
                                secTheft = false
                            } else {
                                viewModel.armTheftGuard()
                                secTheft = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (secTheft) AuraSuccess else AuraCardBorder,
                            contentColor = if (secTheft) Color(0xFF070B13) else TextPrimary
                        )
                    ) {
                        Text(if (secTheft) "Armed (Tap to Disarm)" else "Arm Theft Guard", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.triggerTheftTestAlarm() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Test Siren", fontSize = 11.sp, color = AuraError)
                    }
                }
            }
        }

        // 9. Calls
        item {
            CategoryToggleCard(
                categoryIndex = 9,
                title = "9. Incoming Calls",
                subtitle = "Caller announcer, voice-controlled accept/reject",
                icon = Icons.Default.Call,
                isExpanded = expandedCategories[9] == true,
                onToggleExpand = { expandedCategories[9] = !(expandedCategories[9] ?: false) }
            ) {
                SubToggleRow("Incoming Caller Announcer", callAnnounce) {
                    callAnnounce = it
                    viewModel.toggleCallAnnouncer(it)
                }
                SubToggleRow("Voice-Controlled Answer / Reject", callVoiceAnswer) {
                    callVoiceAnswer = it
                    prefs.toggleCallsVoiceAnswer = it
                }
                TextButton(
                    onClick = { viewModel.testCallAnnouncement() },
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                ) {
                    Text("🔊 Test Call Announce", color = AuraCyanPrimary)
                }
            }
        }

        // 10. Contacts
        item {
            CategoryToggleCard(
                categoryIndex = 10,
                title = "10. Contacts & Priority",
                subtitle = "Priority contact resolution and disambiguation",
                icon = Icons.Default.ContactPage,
                isExpanded = expandedCategories[10] == true,
                onToggleExpand = { expandedCategories[10] = !(expandedCategories[10] ?: false) }
            ) {
                SubToggleRow("Priority Contact Disambiguation", contactsPrio) {
                    contactsPrio = it
                    prefs.toggleContactsPriority = it
                }
            }
        }

        // Section: Privacy & Hardware Keystore
        item {
            Text(
                text = "PRIVACY & SECURITY GUARANTEES",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = AuraSuccess, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Encrypted Keystore Active", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("AES-256 GCM hardware-backed encryption", color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    Text(
                        text = "• All voiceprints are stored locally as numeric mathematical embeddings (no raw audio recordings stored).\n• Wake-word detection runs on-device.\n• Complete control to purge memories and history at any time.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearAllMemories() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Clear Memory", color = AuraCyanPrimary, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearAllHistory() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Clear History", color = AuraError, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showImportNameDialog && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showImportNameDialog = false
                pendingImportUri = null
            },
            title = { Text("Name Custom Voice", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a name for this uploaded voice sample (e.g. Mom's Voice, Jarvis Clone):", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = importVoiceNameInput,
                        onValueChange = { importVoiceNameInput = it },
                        label = { Text("Voice Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingImportUri
                        if (uri != null) {
                            viewModel.importCustomVoice(uri, importVoiceNameInput)
                        }
                        showImportNameDialog = false
                        pendingImportUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuraCyanPrimary, contentColor = Color(0xFF070B13))
                ) {
                    Text("Save Voice")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportNameDialog = false
                    pendingImportUri = null
                }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = AuraDarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showRecordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (isRecordingVoiceSample) viewModel.cancelRecordingVoiceSample()
                showRecordDialog = false
            },
            title = { Text("Record Live Voice Sample", color = TextPrimary) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Speak clearly into your phone's mic for 3-5 seconds to capture the voice timbre.", color = TextSecondary, fontSize = 12.sp)

                    OutlinedTextField(
                        value = recordVoiceNameInput,
                        onValueChange = { recordVoiceNameInput = it },
                        label = { Text("Voice Profile Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRecordingVoiceSample
                    )

                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingVoiceSample) AuraError.copy(alpha = 0.2f) else AuraCyanPrimary.copy(alpha = 0.15f))
                            .border(2.dp, if (isRecordingVoiceSample) AuraError else AuraCyanPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecordingVoiceSample) Icons.Default.Mic else Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = if (isRecordingVoiceSample) AuraError else AuraCyanPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    if (isRecordingVoiceSample) {
                        Text("Recording audio in progress...", color = AuraError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                if (isRecordingVoiceSample) {
                    Button(
                        onClick = {
                            viewModel.stopRecordingVoiceSample(recordVoiceNameInput)
                            showRecordDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraError, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop & Save")
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.startRecordingVoiceSample()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraCyanPrimary, contentColor = Color(0xFF070B13))
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start Mic Recording")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (isRecordingVoiceSample) viewModel.cancelRecordingVoiceSample()
                    showRecordDialog = false
                }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = AuraDarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun CategoryToggleCard(
    categoryIndex: Int,
    title: String,
    subtitle: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    AuraGlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AuraVioletSecondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = AuraVioletSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = subtitle,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = AuraCyanPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SubToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) AuraCyanPrimary.copy(alpha = 0.08f) else AuraDarkSurface.copy(alpha = 0.6f))
            .border(
                1.dp,
                if (checked) AuraCyanPrimary.copy(alpha = 0.3f) else AuraCardBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (checked) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF070B13),
                checkedTrackColor = AuraCyanPrimary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = AuraDarkSurface
            )
        )
    }
}
