package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AuraCardBorder
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraDarkSurface
import com.example.ui.theme.AuraVioletSecondary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.sin

@Composable
fun AudioWaveform(
    audioLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 14
    val transition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val sineFactor = (sin(phase + (i * 0.45f)) + 1f) / 2f
            val calculatedHeight = if (isListening) {
                (8.dp + (36.dp * audioLevel) + (16.dp * sineFactor)).coerceIn(6.dp, 44.dp)
            } else {
                (6.dp + (6.dp * sineFactor))
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(calculatedHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(AuraCyanPrimary, AuraVioletSecondary)
                        )
                    )
            )
        }
    }
}

@Composable
fun AuraGlassCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = AuraDarkSurface.copy(alpha = 0.85f),
    borderColor: Color = AuraCardBorder,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun StatBadge(
    icon: ImageVector,
    value: String,
    label: String,
    tintColor: Color = AuraCyanPrimary,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AuraDarkSurface.copy(alpha = 0.9f))
            .border(1.dp, AuraCardBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tintColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tintColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = value,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
