package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssistantListeningState
import com.example.ui.AuraViewModel
import com.example.ui.components.AudioWaveform
import com.example.ui.components.AuraGlassCard
import com.example.ui.components.VoiceOrb
import com.example.ui.theme.AuraCardBorder
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraDarkBackground
import com.example.ui.theme.AuraDarkSurface
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraVioletSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VoiceEnrollmentScreen(
    viewModel: AuraViewModel,
    onEnrollmentComplete: () -> Unit
) {
    val step by viewModel.currentEnrollmentStep.collectAsState()
    val isEnrolled by viewModel.isVoiceEnrolled.collectAsState()
    val listeningState by viewModel.listeningState.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val audioRms by viewModel.audioRms.collectAsState()
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.recordEnrollmentPhrase()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraDarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Voice-Lock Biometrics",
                color = AuraCyanPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            if (isEnrolled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = AuraSuccess, modifier = Modifier.size(16.dp))
                    Text(text = "Voiceprint Stored", color = AuraSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isEnrolled) "Voice Lock Activated" else "Voice Enrollment",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = if (isEnrolled) {
                "Aura's acoustic biometric model is calibrated to your voice. Any stranger speaking will be rejected."
            } else {
                "Train the on-device voiceprint model. Please read each phrase aloud 3–5 times so Aura learns your acoustic timbre."
            },
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // Progress indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            for (i in 0..3) {
                val isDone = i < step || isEnrolled
                val isCurrent = i == step && !isEnrolled
                Box(
                    modifier = Modifier
                        .size(width = if (isCurrent) 28.dp else 12.dp, height = 12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isDone -> AuraSuccess
                                isCurrent -> AuraCyanPrimary
                                else -> AuraCardBorder
                            }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Central Voice Orb
        VoiceOrb(
            state = listeningState,
            audioRms = audioRms,
            onClick = {
                if (!isEnrolled) {
                    viewModel.recordEnrollmentPhrase()
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Phrase Display Card
        if (!isEnrolled) {
            val currentPhrase = viewModel.enrollmentPhrases.getOrElse(step) { "Say: Hey Aura" }
            AuraGlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PHRASE ${step + 1} OF 4",
                        color = AuraVioletSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$currentPhrase\"",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AudioWaveform(
                        audioLevel = audioRms,
                        isListening = listeningState == AssistantListeningState.SPEAKER_VERIFYING
                    )
                }
            }
        } else {
            AuraGlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AuraSuccess, modifier = Modifier.size(32.dp))
                    Text(
                        text = "32-Dimensional Acoustic Vector Saved",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Cosine similarity threshold: 76%. Raw audio is discarded immediately after spectral embedding computation for complete privacy.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.2f))

        Text(
            text = statusMsg,
            color = AuraCyanPrimary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Action Buttons
        if (!isEnrolled) {
            Button(
                onClick = {
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        viewModel.recordEnrollmentPhrase()
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuraCyanPrimary,
                    contentColor = Color(0xFF070B13)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Record Phrase ${step + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onEnrollmentComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraSuccess,
                        contentColor = Color(0xFF070B13)
                    )
                ) {
                    Text(
                        text = "Launch Aura Assistant",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.resetEnrollment() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AuraCyanPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Re-enroll Voiceprint", color = AuraCyanPrimary)
                }
            }
        }
    }
}
