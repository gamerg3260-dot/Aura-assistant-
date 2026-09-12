package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.system.AppPermissionState
import com.example.system.PermissionHandler
import com.example.ui.theme.AuraCardBorder
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraDarkBackground
import com.example.ui.theme.AuraDarkSurface
import com.example.ui.theme.AuraError
import com.example.ui.theme.AuraPinkTertiary
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraVioletSecondary
import com.example.ui.theme.AuraWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * A comprehensive UI component that manages, requests, and displays all essential permissions
 * (RECORD_AUDIO, FOREGROUND_SERVICE_MICROPHONE, SYSTEM_ALERT_WINDOW, POST_NOTIFICATIONS)
 * in a user-friendly manner before the voice service starts.
 */
@Composable
fun PermissionHandlerCard(
    modifier: Modifier = Modifier,
    onAllPermissionsReady: () -> Unit = {}
) {
    val context = LocalContext.current
    var permissionState by remember { mutableStateOf(PermissionHandler.checkPermissions(context)) }

    val refreshState = {
        permissionState = PermissionHandler.checkPermissions(context)
        if (permissionState.hasAudioPermission) {
            onAllPermissionsReady()
        }
    }

    // Standard runtime permissions launcher (Audio + Notifications)
    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshState()
    }

    // Special Overlay Permission intent launcher (SYSTEM_ALERT_WINDOW)
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshState()
    }

    // Battery optimization launcher
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshState()
    }

    AuraGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
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
                            .background(if (permissionState.hasAudioPermission) AuraSuccess.copy(alpha = 0.15f) else AuraCyanPrimary.copy(alpha = 0.15f))
                            .border(1.dp, if (permissionState.hasAudioPermission) AuraSuccess.copy(alpha = 0.4f) else AuraCyanPrimary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (permissionState.hasAudioPermission) Icons.Default.Shield else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (permissionState.hasAudioPermission) AuraSuccess else AuraCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Permission & Service Security",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (permissionState.hasAudioPermission) "All Core Permissions Active" else "Setup Required for Voice Service",
                            color = if (permissionState.hasAudioPermission) AuraSuccess else AuraWarning,
                            fontSize = 11.sp
                        )
                    }
                }

                if (permissionState.hasAudioPermission) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AuraSuccess.copy(alpha = 0.15f))
                            .border(1.dp, AuraSuccess.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Ready", color = AuraSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "To spot 'Hey Aura' in background and execute hands-free commands with Siri-like floating overlay, grant the following permissions:",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            // Permission 1: RECORD_AUDIO & FOREGROUND_SERVICE_MICROPHONE
            PermissionItemRow(
                icon = Icons.Default.Mic,
                title = "Microphone & Real-Time Audio",
                description = "Required for wake-word listening, acoustic biometric voice-lock, and speech transcription.",
                isGranted = permissionState.hasAudioPermission,
                onGrantClick = {
                    val missing = PermissionHandler.getMissingRuntimePermissions(context)
                    if (missing.isNotEmpty()) {
                        runtimePermissionsLauncher.launch(missing.toTypedArray())
                    } else {
                        runtimePermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                }
            )

            // Permission 2: SYSTEM_ALERT_WINDOW (Draw over other apps)
            PermissionItemRow(
                icon = Icons.Default.Layers,
                title = "System Alert Window (Overlay)",
                description = "Enables floating assistant orb and overlay responses over any active application.",
                isGranted = permissionState.hasOverlayPermission,
                onGrantClick = {
                    val intent = PermissionHandler.createOverlayPermissionIntent(context)
                    overlayPermissionLauncher.launch(intent)
                }
            )

            // Permission 3: POST_NOTIFICATIONS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItemRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Background Notifications",
                    description = "Displays the persistent voice-lock status and quick mute/listen action controls.",
                    isGranted = permissionState.hasNotificationPermission,
                    onGrantClick = {
                        runtimePermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                )
            }

            // Quick Grant All Button if missing any
            if (!permissionState.hasAudioPermission || !permissionState.hasOverlayPermission) {
                Button(
                    onClick = {
                        val missing = PermissionHandler.getMissingRuntimePermissions(context)
                        if (missing.isNotEmpty()) {
                            runtimePermissionsLauncher.launch(missing.toTypedArray())
                        } else if (!permissionState.hasOverlayPermission) {
                            val intent = PermissionHandler.createOverlayPermissionIntent(context)
                            overlayPermissionLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_all_permissions_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraCyanPrimary,
                        contentColor = Color(0xFF070B13)
                    )
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Grant Required Permissions", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Modal Bottom Sheet version of Permission Handler that smoothly appears before service activation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionHandlerSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onPermissionsCompleted: () -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    var permissionState by remember { mutableStateOf(PermissionHandler.checkPermissions(context)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    val refresh = {
        permissionState = PermissionHandler.checkPermissions(context)
        if (permissionState.hasAudioPermission) {
            onPermissionsCompleted()
        }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refresh()
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refresh()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AuraDarkSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AuraCyanPrimary.copy(alpha = 0.15f))
                            .border(1.dp, AuraCyanPrimary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = AuraCyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Aura Permissions Setup",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Required for Voice Service & Wake Word",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Text(
                text = "To enable hands-free voice commands, wake-word spotting, and floating overlay assistance, please grant access to the following components:",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Permission Rows
            PermissionItemRow(
                icon = Icons.Default.Mic,
                title = "1. Microphone (RECORD_AUDIO)",
                description = "Enables real-time acoustic spotting for 'Hey Aura' and spoken commands.",
                isGranted = permissionState.hasAudioPermission,
                onGrantClick = {
                    runtimeLauncher.launch(
                        listOfNotNull(
                            Manifest.permission.RECORD_AUDIO,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
                        ).toTypedArray()
                    )
                }
            )

            PermissionItemRow(
                icon = Icons.Default.Layers,
                title = "2. Display Over Apps (SYSTEM_ALERT_WINDOW)",
                description = "Allows Aura to display floating voice bubbles over other apps when invoked.",
                isGranted = permissionState.hasOverlayPermission,
                onGrantClick = {
                    val intent = PermissionHandler.createOverlayPermissionIntent(context)
                    overlayLauncher.launch(intent)
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = TextSecondary)
                }

                Button(
                    onClick = {
                        val missing = PermissionHandler.getMissingRuntimePermissions(context)
                        if (missing.isNotEmpty()) {
                            runtimeLauncher.launch(missing.toTypedArray())
                        } else if (!permissionState.hasOverlayPermission) {
                            val intent = PermissionHandler.createOverlayPermissionIntent(context)
                            overlayLauncher.launch(intent)
                        } else {
                            coroutineScope.launch {
                                sheetState.hide()
                                onPermissionsCompleted()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraCyanPrimary,
                        contentColor = Color(0xFF070B13)
                    )
                ) {
                    Text(
                        text = if (permissionState.hasAudioPermission) "Start Service" else "Enable All",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItemRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.5f))
            .border(1.dp, if (isGranted) AuraSuccess.copy(alpha = 0.3f) else AuraCardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) AuraSuccess.copy(alpha = 0.15f) else AuraVioletSecondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) AuraSuccess else AuraVioletSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = description,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AuraSuccess.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AuraSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Granted", color = AuraSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraCyanPrimary,
                        contentColor = Color(0xFF070B13)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
