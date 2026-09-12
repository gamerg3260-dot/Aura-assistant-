package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.AuraApplication
import com.example.ui.AuraViewModel
import com.example.ui.components.AuraGlassCard
import com.example.ui.theme.AuraCardBorder
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraDarkSurface
import com.example.ui.theme.AuraError
import com.example.ui.theme.AuraPinkTertiary
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraVioletSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.voice.VoiceprintEngine

@Composable
fun VoiceLockTesterScreen(
    viewModel: AuraViewModel,
    modifier: Modifier = Modifier
) {
    val prefs = AuraApplication.instance.preferences
    val lastResult by viewModel.lastTestResult.collectAsState()
    val sensitivity by viewModel.voiceSensitivity.collectAsState()
    val isEnrolled by viewModel.isVoiceEnrolled.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Security, contentDescription = null, tint = AuraCyanPrimary, modifier = Modifier.size(24.dp))
                Text(
                    text = "Voice-Lock Biometric Sandbox",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Test and verify that Aura executes commands ONLY for your voice, automatically ignoring unauthorized speakers.",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Live Verification Result Display
        item {
            val score = lastResult?.similarityScore ?: 0.88f
            val isVerified = lastResult?.isVerified ?: true
            val threshold = lastResult?.threshold ?: sensitivity

            AuraGlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (isVerified) AuraSuccess.copy(alpha = 0.5f) else AuraError.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                if (isVerified) AuraSuccess.copy(alpha = 0.15f) else AuraError.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Block,
                            contentDescription = null,
                            tint = if (isVerified) AuraSuccess else AuraError,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = if (isVerified) "ACCESS GRANTED: OWNER VERIFIED" else "ACCESS DENIED: VOICE MISMATCH",
                        color = if (isVerified) AuraSuccess else AuraError,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )

                    // Cosine Similarity Gauge
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Cosine Similarity Score", color = TextSecondary, fontSize = 13.sp)
                            Text(
                                text = "${(score * 100).toInt()}%",
                                color = if (isVerified) AuraSuccess else AuraError,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { score.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = if (isVerified) AuraSuccess else AuraError,
                            trackColor = AuraDarkSurface,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "0% (Dissimilar)", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = "Gate Threshold: ${(threshold * 100).toInt()}%",
                                color = AuraCyanPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = "100% (Identical)", color = TextMuted, fontSize = 10.sp)
                        }
                    }

                    Text(
                        text = lastResult?.details ?: "Aura compares 32 spectral acoustic bands against your stored voiceprint centroid.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Action Testing Buttons
        item {
            Text(
                text = "SIMULATE SPEAKER VERIFICATION",
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
                Button(
                    onClick = { viewModel.testVoiceLock(isOwnerSpeaking = true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraCyanPrimary,
                        contentColor = Color(0xFF070B13)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Speak as Owner", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = { viewModel.testVoiceLock(isOwnerSpeaking = false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraError.copy(alpha = 0.85f),
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Speak as Stranger", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Spectral Embedding Vector Visualizer
        item {
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = AuraVioletSecondary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Acoustic Timbre Embedding (32-D)",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = if (isEnrolled) "Calibrated" else "Default Model",
                            color = if (isEnrolled) AuraSuccess else AuraVioletSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 32-dim mini frequency bars
                    val embedding = VoiceprintEngine.deserializeEmbedding(prefs.voiceprintEmbeddingString)
                        ?: FloatArray(32) { (Math.sin(it * 0.35) * 0.4 + 0.5).toFloat() }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AuraDarkSurface)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        for (value in embedding.take(32)) {
                            val barHeight = (value * 38f).coerceIn(4f, 38f)
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(AuraCyanPrimary, AuraVioletSecondary)
                                        )
                                    )
                            )
                        }
                    }
                    Text(
                        text = "Encodes formant ratios, pitch variance, and harmonic spectral distribution. Stored locally without recording raw voice.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Sensitivity Slider
        item {
            AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = AuraCyanPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Verification Sensitivity",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "${(sensitivity * 100).toInt()}%",
                            color = AuraCyanPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Slider(
                        value = sensitivity,
                        onValueChange = { viewModel.setVoiceSensitivity(it) },
                        valueRange = 0.60f..0.92f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = AuraCyanPrimary,
                            activeTrackColor = AuraCyanPrimary,
                            inactiveTrackColor = AuraCardBorder
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lenient (Accepts minor hoarseness)", color = TextMuted, fontSize = 11.sp)
                        Text("Strict (Highest Security)", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
