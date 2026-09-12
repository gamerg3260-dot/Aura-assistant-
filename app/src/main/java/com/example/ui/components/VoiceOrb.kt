package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.model.AssistantListeningState
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraError
import com.example.ui.theme.AuraPinkTertiary
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraVioletSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceOrb(
    state: AssistantListeningState,
    audioRms: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    val primaryColor = when (state) {
        AssistantListeningState.VOICE_MISMATCH_ALERT -> AuraError
        AssistantListeningState.SPEAKER_VERIFYING -> AuraVioletSecondary
        AssistantListeningState.SPEECH_LISTENING -> AuraCyanPrimary
        AssistantListeningState.PROCESSING_LLM -> AuraPinkTertiary
        AssistantListeningState.SPEAKING -> AuraSuccess
        else -> AuraCyanPrimary
    }

    val secondaryColor = when (state) {
        AssistantListeningState.VOICE_MISMATCH_ALERT -> Color(0xFFFF758C)
        AssistantListeningState.SPEAKER_VERIFYING -> Color(0xFFC084FC)
        AssistantListeningState.SPEECH_LISTENING -> Color(0xFF38BDF8)
        AssistantListeningState.PROCESSING_LLM -> Color(0xFFF472B6)
        AssistantListeningState.SPEAKING -> Color(0xFF34D399)
        else -> AuraVioletSecondary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(200.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 3.4f) * pulseScale
            val dynamicRadius = baseRadius + (audioRms * 35f)

            // Outer ethereal glow ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f),
                        secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dynamicRadius * 1.55f
                ),
                radius = dynamicRadius * 1.55f,
                center = center
            )

            // Middle soundwave orbital ring
            val orbitalCount = 8
            for (i in 0 until orbitalCount) {
                val angle = Math.toRadians((rotationAngle + (i * (360f / orbitalCount))).toDouble())
                val orbitRadius = dynamicRadius * 1.18f
                val dotX = center.x + (orbitRadius * cos(angle)).toFloat()
                val dotY = center.y + (orbitRadius * sin(angle)).toFloat()
                drawCircle(
                    color = primaryColor.copy(alpha = 0.65f),
                    radius = 3.5f + (audioRms * 4f),
                    center = Offset(dotX, dotY)
                )
            }

            // Expanding wave stroke
            drawCircle(
                color = primaryColor.copy(alpha = 0.45f),
                radius = dynamicRadius * 1.12f,
                center = center,
                style = Stroke(width = 2.5f)
            )

            // Core glowing orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        primaryColor,
                        secondaryColor,
                        Color(0xFF070B13)
                    ),
                    center = Offset(center.x - 10f, center.y - 12f),
                    radius = dynamicRadius
                ),
                radius = dynamicRadius,
                center = center
            )

            // High-tech inner core ring
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = dynamicRadius * 0.32f,
                center = center,
                style = Stroke(width = 2f)
            )
        }
    }
}
