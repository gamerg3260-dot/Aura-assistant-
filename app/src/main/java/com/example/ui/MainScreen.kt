package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AuraApplication
import com.example.ui.components.PermissionHandlerSheet
import com.example.system.PermissionHandler
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceEnrollmentScreen
import com.example.ui.screens.VoiceLockTesterScreen
import com.example.ui.theme.AuraCardBorder
import com.example.ui.theme.AuraCyanBright
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraDarkBackground
import com.example.ui.theme.AuraDarkSurface
import com.example.ui.theme.AuraError
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraVioletSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

sealed class ScreenDestination {
    object Onboarding : ScreenDestination()
    object VoiceEnrollment : ScreenDestination()
    object MainApp : ScreenDestination()
}

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val index: Int
)

@Composable
fun MainScreen(
    viewModel: AuraViewModel
) {
    val context = LocalContext.current
    val prefs = AuraApplication.instance.preferences
    val isVoiceEnrolled by viewModel.isVoiceEnrolled.collectAsState()
    val isBgServiceEnabled by viewModel.isBackgroundServiceEnabled.collectAsState()
    val stats by viewModel.deviceStats.collectAsState()

    var showMainPermissionSheet by remember { mutableStateOf(false) }

    PermissionHandlerSheet(
        isVisible = showMainPermissionSheet,
        onDismiss = { showMainPermissionSheet = false },
        onPermissionsCompleted = {
            showMainPermissionSheet = false
            viewModel.toggleBackgroundService(true)
        }
    )

    var currentScreen by remember {
        mutableStateOf<ScreenDestination>(ScreenDestination.MainApp)
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "main_screen_nav"
    ) { screen ->
        when (screen) {
            ScreenDestination.Onboarding -> {
                OnboardingScreen(
                    viewModel = viewModel,
                    onOnboardingComplete = {
                        currentScreen = ScreenDestination.VoiceEnrollment
                    }
                )
            }
            ScreenDestination.VoiceEnrollment -> {
                VoiceEnrollmentScreen(
                    viewModel = viewModel,
                    onEnrollmentComplete = {
                        currentScreen = ScreenDestination.MainApp
                    }
                )
            }
            ScreenDestination.MainApp -> {
                val isPorcupineActive by viewModel.isPorcupineActive.collectAsState()

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AuraDarkBackground),
                    containerColor = AuraDarkBackground,
                    topBar = {
                        AuraTopAppBar(
                            userName = prefs.userName,
                            isVoiceEnrolled = isVoiceEnrolled,
                            isBgServiceRunning = isBgServiceEnabled,
                            isPorcupineActive = isPorcupineActive,
                            batteryPct = stats.batteryPercent,
                            isCharging = stats.isCharging,
                            onToggleService = {
                                if (!isBgServiceEnabled) {
                                    val permState = PermissionHandler.checkPermissions(context)
                                    if (permState.hasAudioPermission) {
                                        viewModel.toggleBackgroundService(true)
                                    } else {
                                        showMainPermissionSheet = true
                                    }
                                } else {
                                    viewModel.toggleBackgroundService(false)
                                }
                            }
                        )
                    },
                    bottomBar = {
                        AuraBottomNav(
                            selectedIndex = selectedTabIndex,
                            onSelectTab = { selectedTabIndex = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTabIndex) {
                            0 -> HomeScreen(viewModel = viewModel)
                            1 -> VoiceLockTesterScreen(viewModel = viewModel)
                            2 -> HistoryScreen(viewModel = viewModel)
                            3 -> SettingsScreen(
                                viewModel = viewModel,
                                onNavigateToEnrollment = {
                                    currentScreen = ScreenDestination.VoiceEnrollment
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuraTopAppBar(
    userName: String,
    isVoiceEnrolled: Boolean,
    isBgServiceRunning: Boolean,
    isPorcupineActive: Boolean = false,
    batteryPct: Int,
    isCharging: Boolean,
    onToggleService: () -> Unit
) {
    val topBarInfiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "topbar_porcupine")
    val porcupineAlphaPulse by topBarInfiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "porcupine_topbar_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Logo and Name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AuraCyanPrimary.copy(alpha = 0.15f))
                    .border(1.dp, AuraCyanPrimary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Aura Logo",
                    tint = AuraCyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "AURA",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "AI",
                        color = AuraCyanPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = "Personal Voice Assistant",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Status Indicators & Service Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Porcupine Active Status Indicator Pill
            androidx.compose.animation.AnimatedVisibility(visible = isPorcupineActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AuraCyanPrimary.copy(alpha = 0.18f))
                        .border(
                            width = 1.2.dp,
                            color = AuraCyanPrimary.copy(alpha = porcupineAlphaPulse),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(AuraSuccess.copy(alpha = porcupineAlphaPulse * 0.5f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AuraSuccess)
                            )
                        }
                        Text(
                            text = "DSP LISTENING",
                            color = AuraCyanBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Background Guard Badge / Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isBgServiceRunning) AuraSuccess.copy(alpha = 0.15f) else AuraDarkSurface)
                    .border(
                        1.dp,
                        if (isBgServiceRunning) AuraSuccess.copy(alpha = 0.4f) else AuraCardBorder,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable(onClick = onToggleService)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isBgServiceRunning) AuraSuccess else TextMuted)
                    )
                    Text(
                        text = if (isBgServiceRunning) "Guard: Active" else "Guard: Paused",
                        color = if (isBgServiceRunning) AuraSuccess else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Battery mini pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AuraDarkSurface)
                    .border(1.dp, AuraCardBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (batteryPct < 20) AuraError else AuraCyanPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "$batteryPct%",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AuraBottomNav(
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit
) {
    val items = listOf(
        NavTabItem("Home", Icons.Default.Home, 0),
        NavTabItem("Voice Lock", Icons.Default.Security, 1),
        NavTabItem("History", Icons.Default.History, 2),
        NavTabItem("Settings", Icons.Default.Settings, 3)
    )

    NavigationBar(
        containerColor = AuraDarkSurface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .border(1.dp, AuraCardBorder.copy(alpha = 0.5f))
    ) {
        items.forEach { tab ->
            val isSelected = selectedIndex == tab.index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectTab(tab.index) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AuraCyanPrimary,
                    selectedTextColor = AuraCyanPrimary,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = AuraCyanPrimary.copy(alpha = 0.15f)
                )
            )
        }
    }
}
