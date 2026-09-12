package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AuraApplication
import com.example.data.model.AssistantListeningState
import com.example.ui.AuraViewModel
import com.example.ui.components.AudioWaveform
import com.example.ui.components.AuraGlassCard
import com.example.ui.components.StatBadge
import com.example.ui.components.VoiceOrb
import com.example.ui.theme.AuraCardBorder
import com.example.ui.theme.AuraCyanBright
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraDarkSurface
import com.example.ui.theme.AuraError
import com.example.ui.theme.AuraPinkTertiary
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraVioletSecondary
import com.example.ui.theme.AuraWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: AuraViewModel,
    modifier: Modifier = Modifier
) {
    val preferences = AuraApplication.instance.preferences
    val stats by viewModel.deviceStats.collectAsState()
    val calendarEvents by viewModel.calendarEvents.collectAsState()
    val listeningState by viewModel.listeningState.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val lastSpeech by viewModel.lastRecognizedSpeech.collectAsState()
    val lastReply by viewModel.lastAssistantReply.collectAsState()
    val toolResult by viewModel.lastToolResult.collectAsState()
    val executionSource by viewModel.lastToolExecutionSource.collectAsState()
    val audioRms by viewModel.audioRms.collectAsState()
    val isEnrolled by viewModel.isVoiceEnrolled.collectAsState()

    var textInput by remember { mutableStateOf("") }

    val currentDateStr = remember {
        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
    }

    val greetingText = remember(preferences.userName) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..21 -> "Good Evening"
            else -> "Good Night"
        }
        "$timeGreeting, ${preferences.userName}"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Greeting & Online Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = greetingText,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = AuraCyanPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${stats.locationCity} • $currentDateStr",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                // Online & Biometric Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isEnrolled) AuraSuccess.copy(alpha = 0.15f) else AuraWarning.copy(alpha = 0.15f)
                        )
                        .border(
                            1.dp,
                            if (isEnrolled) AuraSuccess.copy(alpha = 0.4f) else AuraWarning.copy(alpha = 0.4f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isEnrolled) AuraSuccess else AuraWarning)
                        )
                        Text(
                            text = if (isEnrolled) "Voice-Lock: ON" else "Voice Unenrolled",
                            color = if (isEnrolled) AuraSuccess else AuraWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Live Voice Visualizer Orb
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VoiceOrb(
                        state = listeningState,
                        audioRms = audioRms,
                        onClick = {
                            if (listeningState == AssistantListeningState.SPEAKING) {
                                viewModel.stopAssistantSpeaking()
                            } else {
                                viewModel.triggerWakeWordAndListen(isOwner = true)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = statusMsg,
                        color = when (listeningState) {
                            AssistantListeningState.VOICE_MISMATCH_ALERT -> AuraError
                            AssistantListeningState.SPEAKER_VERIFYING -> AuraVioletSecondary
                            AssistantListeningState.SPEAKING -> AuraSuccess
                            else -> AuraCyanBright
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )

                    Text(
                        text = "Wake word: \"${preferences.customWakeWord}\" • Tap orb to speak",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Default Digital Assistant System Banner
        item {
            val context = LocalContext.current
            val isDefaultAssistant = remember {
                com.example.service.voiceinteraction.AuraVoiceInteractionService.isSelectedAsDefaultAssistant(context)
            }

            AuraGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
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
                    }
            ) {
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDefaultAssistant) AuraSuccess.copy(alpha = 0.15f) else AuraCyanPrimary.copy(alpha = 0.15f))
                                .border(1.dp, if (isDefaultAssistant) AuraSuccess.copy(alpha = 0.5f) else AuraCyanPrimary.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDefaultAssistant) Icons.Default.CheckCircle else Icons.Default.Assistant,
                                contentDescription = null,
                                tint = if (isDefaultAssistant) AuraSuccess else AuraCyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isDefaultAssistant) "Default Digital Assistant Active" else "Default Assistant App",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isDefaultAssistant) AuraSuccess.copy(alpha = 0.2f) else AuraCyanPrimary.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
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
                                text = if (isDefaultAssistant)
                                    "Long-press home / gesture / wake words wake up Aura directly."
                                else
                                    "Tap to select Aura in Android's 'Default digital assistant app' menu.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = "Open Settings",
                        tint = AuraCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Foreground Service Status Card
        item {
            val isBgServiceActive by viewModel.isBackgroundServiceEnabled.collectAsState()
            val isMuted by viewModel.isServiceMuted.collectAsState()
            val activeEngine by viewModel.activeWakeWordEngine.collectAsState()
            val isPorcupineActive by viewModel.isPorcupineActive.collectAsState()

            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isBgServiceActive) AuraCyanPrimary.copy(alpha = 0.15f) else AuraDarkSurface)
                                .border(1.dp, if (isBgServiceActive) AuraCyanPrimary.copy(alpha = 0.5f) else AuraCardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isBgServiceActive) Icons.Default.NotificationsActive else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = if (isBgServiceActive) AuraCyanPrimary else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isBgServiceActive) "Background Guard Active" else "Background Service Paused",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isBgServiceActive && !isMuted) AuraSuccess else if (isMuted) AuraPinkTertiary else TextMuted)
                                )
                                if (isBgServiceActive && isPorcupineActive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AuraCyanPrimary.copy(alpha = 0.15f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "PORCUPINE DSP",
                                            color = AuraCyanPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isBgServiceActive) {
                                    if (isMuted) "Microphone Muted (Listening Paused)" else "$activeEngine • START_STICKY"
                                } else {
                                    "Tap to start persistent listening service"
                                },
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isBgServiceActive) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.toggleServiceMute() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Mute/Unmute",
                                    tint = if (isMuted) AuraPinkTertiary else AuraCyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AuraCyanPrimary.copy(alpha = 0.15f))
                                .border(1.dp, AuraCyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable { viewModel.toggleBackgroundService(true) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Start Service", color = AuraCyanPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Live Speech / Response Transcript Card
        if (lastSpeech.isNotBlank() || lastReply.isNotBlank()) {
            item {
                AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (lastSpeech.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "You: ",
                                    color = AuraCyanPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = lastSpeech,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        if (lastReply.isNotBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "Aura: ",
                                    color = AuraVioletSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = lastReply,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        if (toolResult != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AuraVioletSecondary.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Tool: ${toolResult?.toolName} • ${toolResult?.summary}",
                                        color = AuraCyanBright,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (!executionSource.isNullOrBlank() && executionSource != "Ready") {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (executionSource?.startsWith("gemini") == true) AuraCyanPrimary.copy(alpha = 0.15f)
                                                else AuraPinkTertiary.copy(alpha = 0.15f)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (executionSource?.startsWith("gemini") == true) "GEMINI 3.5 FLASH" else "ON-DEVICE",
                                            color = if (executionSource?.startsWith("gemini") == true) AuraCyanPrimary else AuraPinkTertiary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // System Stats Bar (Battery, RAM, Storage)
        item {
            Text(
                text = "SYSTEM TELEMETRY",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatBadge(
                    icon = if (stats.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    value = "${stats.batteryPercent}%",
                    label = if (stats.isCharging) "Charging" else "Battery",
                    tintColor = if (stats.batteryPercent < 20) AuraError else AuraSuccess,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    icon = Icons.Default.Memory,
                    value = "${stats.ramUsedMb}M",
                    label = "RAM Used",
                    tintColor = AuraCyanPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    icon = Icons.Default.SdCard,
                    value = "${stats.storageFreeGb}GB",
                    label = "Free Disk",
                    tintColor = AuraVioletSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Upcoming Calendar Events Card
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UPCOMING SCHEDULE",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = AuraCyanPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    calendarEvents.take(3).forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                if (event.location != null) {
                                    Text(
                                        text = event.location,
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AuraDarkSurface)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = event.timeFormatted,
                                    color = AuraCyanPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Command Shortcuts
        item {
            Text(
                text = "QUICK COMMAND SHORTCUTS",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    CommandChip(
                        title = "Listen",
                        icon = Icons.Default.Mic,
                        onClick = { viewModel.triggerWakeWordAndListen(isOwner = true) }
                    )
                }
                item {
                    CommandChip(
                        title = "15m Timer",
                        icon = Icons.Default.Timer,
                        onClick = { viewModel.submitTextCommand("Set a timer for 15 minutes", isOwner = true) }
                    )
                }
                item {
                    CommandChip(
                        title = "Flashlight",
                        icon = Icons.Default.FlashlightOn,
                        onClick = { viewModel.submitTextCommand("Toggle flashlight", isOwner = true) }
                    )
                }
                item {
                    CommandChip(
                        title = "Device Health",
                        icon = Icons.Default.BatteryFull,
                        onClick = { viewModel.submitTextCommand("Check my battery and storage status", isOwner = true) }
                    )
                }
                item {
                    CommandChip(
                        title = "Vol 80%",
                        icon = Icons.Default.VolumeUp,
                        onClick = { viewModel.submitTextCommand("Set volume to 80", isOwner = true) }
                    )
                }
                item {
                    CommandChip(
                        title = "Read Screen",
                        icon = Icons.Default.Security,
                        onClick = { viewModel.submitTextCommand("Read active screen content", isOwner = true) }
                    )
                }
            }
        }

        // Direct Text Command Input Bar
        item {
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Ask Aura anything...", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuraCyanPrimary,
                            unfocusedBorderColor = AuraCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = AuraDarkSurface,
                            unfocusedContainerColor = AuraDarkSurface
                        )
                    )

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                val query = textInput
                                textInput = ""
                                viewModel.submitTextCommand(query, isOwner = true)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AuraCyanPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color(0xFF070B13),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CommandChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AuraDarkSurface)
            .border(1.dp, AuraCardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AuraCyanPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}
