package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.model.AssistantListeningState
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraError
import com.example.ui.theme.AuraPinkTertiary
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraVioletSecondary

class FloatingComposeLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

object AuraFloatingBubbleManager {
    private const val TAG = "AuraFloatingBubble"
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: FloatingComposeLifecycleOwner? = null

    fun isBubbleShowing(): Boolean = composeView != null

    fun showBubble(context: Context) {
        val prefs = com.example.AuraApplication.instance.preferences
        if (!prefs.isFloatingBubbleEnabled) {
            Log.d(TAG, "Floating bubble is disabled in preferences. Skipping.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show floating bubble: SYSTEM_ALERT_WINDOW permission is not granted.")
            return
        }

        if (composeView != null) {
            Log.d(TAG, "Floating bubble is already showing.")
            return
        }

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val density = context.resources.displayMetrics.density
            val sizePx = (80 * density).toInt()

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = prefs.floatingBubbleX
                y = prefs.floatingBubbleY
            }

            val owner = FloatingComposeLifecycleOwner()
            owner.onCreate()
            owner.onStart()
            owner.onResume()
            lifecycleOwner = owner

            val view = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    FloatingBubbleContent(
                        context = context,
                        onDrag = { dx, dy ->
                            params.x = (params.x + dx).toInt()
                            params.y = (params.y + dy).toInt()
                            try {
                                windowManager?.updateViewLayout(this, params)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating window layout during drag", e)
                            }
                        },
                        onDragEnd = {
                            prefs.floatingBubbleX = params.x
                            prefs.floatingBubbleY = params.y
                            Log.d(TAG, "Saved bubble position: X=${params.x}, Y=${params.y}")
                        },
                        onTap = {
                            launchAppWithTrigger(context)
                        }
                    )
                }
            }

            windowManager?.addView(view, params)
            composeView = view
            Log.i(TAG, "Floating bubble added to WindowManager.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating bubble", e)
        }
    }

    fun hideBubble() {
        if (composeView != null) {
            try {
                windowManager?.removeView(composeView)
                Log.i(TAG, "Floating bubble removed from WindowManager.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove floating bubble view", e)
            } finally {
                composeView = null
                lifecycleOwner?.onPause()
                lifecycleOwner?.onStop()
                lifecycleOwner?.onDestroy()
                lifecycleOwner = null
                windowManager = null
            }
        }
    }

    private fun launchAppWithTrigger(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("AUTO_TRIGGER_LISTEN", true)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app from bubble", e)
        }
    }
}

@Composable
fun FloatingBubbleContent(
    context: Context,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit
) {
    val assistantState by AuraVoiceService.assistantState.collectAsState()
    val audioRms by AuraVoiceService.assistantAudioRms.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "bubble_pulse")
    val idleScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_scale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking_rotation"
    )

    val baseColor = when (assistantState) {
        AssistantListeningState.VOICE_MISMATCH_ALERT -> AuraError
        AssistantListeningState.SPEAKER_VERIFYING -> AuraVioletSecondary
        AssistantListeningState.SPEECH_LISTENING -> AuraCyanPrimary
        AssistantListeningState.PROCESSING_LLM -> AuraPinkTertiary
        AssistantListeningState.EXECUTING_ACTION -> AuraPinkTertiary
        AssistantListeningState.SPEAKING -> AuraSuccess
        else -> AuraCyanPrimary
    }

    val glowColor = when (assistantState) {
        AssistantListeningState.VOICE_MISMATCH_ALERT -> Color(0xFFFF758C)
        AssistantListeningState.SPEAKER_VERIFYING -> Color(0xFFC084FC)
        AssistantListeningState.SPEECH_LISTENING -> Color(0xFF38BDF8)
        AssistantListeningState.PROCESSING_LLM -> Color(0xFFF472B6)
        AssistantListeningState.EXECUTING_ACTION -> Color(0xFFF472B6)
        AssistantListeningState.SPEAKING -> Color(0xFF34D399)
        else -> AuraVioletSecondary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .clickable { onTap() }
    ) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val coreRadius = size.minDimension / 3.2f

            drawCircle(
                color = Color(0xFF070B13).copy(alpha = 0.85f),
                radius = coreRadius * 1.3f,
                center = center
            )

            when (assistantState) {
                AssistantListeningState.SPEECH_LISTENING,
                AssistantListeningState.SPEAKER_VERIFYING -> {
                    val rmsFactor = (audioRms * 12f).coerceAtMost(28f)
                    
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                baseColor.copy(alpha = 0.35f),
                                glowColor.copy(alpha = 0.1f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = (coreRadius + rmsFactor) * 1.4f
                        ),
                        radius = (coreRadius + rmsFactor) * 1.4f,
                        center = center
                    )

                    drawCircle(
                        color = baseColor.copy(alpha = 0.8f),
                        radius = coreRadius + rmsFactor * 0.7f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(baseColor, glowColor),
                            center = center,
                            radius = coreRadius
                        ),
                        radius = coreRadius + rmsFactor * 0.2f,
                        center = center
                    )
                }

                AssistantListeningState.SPEAKING -> {
                    val rmsFactor = (audioRms * 10f).coerceAtMost(24f)
                    
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                baseColor.copy(alpha = 0.45f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = coreRadius * 1.6f + rmsFactor
                        ),
                        radius = coreRadius * 1.6f + rmsFactor,
                        center = center
                    )

                    drawCircle(
                        color = baseColor.copy(alpha = 0.6f),
                        radius = coreRadius * 1.25f + rmsFactor * 0.4f,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(baseColor, glowColor),
                            center = center,
                            radius = coreRadius
                        ),
                        radius = coreRadius,
                        center = center
                    )
                }

                AssistantListeningState.PROCESSING_LLM,
                AssistantListeningState.EXECUTING_ACTION -> {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0.0f to baseColor,
                                0.5f to glowColor,
                                1.0f to baseColor
                            ),
                            center = center
                        ),
                        radius = coreRadius * 1.2f,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    drawCircle(
                        color = baseColor.copy(alpha = 0.9f),
                        radius = coreRadius * 0.9f,
                        center = center
                    )
                }

                else -> {
                    val animatedRadius = coreRadius * idleScale

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                baseColor.copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = animatedRadius * 1.6f
                        ),
                        radius = animatedRadius * 1.6f,
                        center = center
                    )

                    drawCircle(
                        color = baseColor.copy(alpha = 0.5f),
                        radius = coreRadius * 1.2f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(baseColor, glowColor),
                            center = center,
                            radius = animatedRadius
                        ),
                        radius = animatedRadius,
                        center = center
                    )
                }
            }
        }
    }
}
