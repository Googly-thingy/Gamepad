package com.example.ui.controller

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberDarkNavy
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    label: String = "L-STICK",
    deadzone: Float = 0.12f,
    sensitivity: Float = 1.0f,
    hapticEnabled: Boolean = true,
    testTag: String = "virtual_joystick",
    onValueChange: (x: Float, y: Float) -> Unit,
    onStickClick: (() -> Unit)? = null
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val animX = remember { Animatable(0f) }
    val animY = remember { Animatable(0f) }

    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .testTag(testTag)
            .pointerInput(deadzone, sensitivity) {
                val radius = this.size.width / 2f
                val maxDistance = radius * 0.75f

                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        if (hapticEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        val dx = (offset.x - radius).coerceIn(-maxDistance, maxDistance)
                        val dy = (offset.y - radius).coerceIn(-maxDistance, maxDistance)

                        coroutineScope.launch {
                            animX.snapTo(dx)
                            animY.snapTo(dy)
                        }

                        val (normX, normY) = calculateNormalized(dx, dy, maxDistance, deadzone, sensitivity)
                        onValueChange(normX, normY)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val currentX = animX.value + dragAmount.x
                        val currentY = animY.value + dragAmount.y

                        val distance = sqrt(currentX * currentX + currentY * currentY)
                        val angle = atan2(currentY, currentX)

                        val clampedDistance = distance.coerceAtMost(maxDistance)
                        val targetX = clampedDistance * cos(angle)
                        val targetY = clampedDistance * sin(angle)

                        coroutineScope.launch {
                            animX.snapTo(targetX)
                            animY.snapTo(targetY)
                        }

                        val (normX, normY) = calculateNormalized(targetX, targetY, maxDistance, deadzone, sensitivity)
                        onValueChange(normX, normY)
                    },
                    onDragEnd = {
                        isDragging = false
                        coroutineScope.launch {
                            animX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 1200f))
                        }
                        coroutineScope.launch {
                            animY.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 1200f))
                        }
                        onValueChange(0f, 0f)
                    },
                    onDragCancel = {
                        isDragging = false
                        coroutineScope.launch {
                            animX.snapTo(0f)
                            animY.snapTo(0f)
                        }
                        onValueChange(0f, 0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerRadius = this.size.width / 2f - 4.dp.toPx()
            val thumbRadius = outerRadius * 0.38f

            // Outer Base Ring
            drawCircle(
                color = CyberDarkNavy,
                radius = outerRadius,
                center = center
            )

            // Concentric Target Rings
            drawCircle(
                color = Color(0x332B3352),
                radius = outerRadius * 0.7f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = Color(0x2200F5D4),
                radius = outerRadius * deadzone,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Outer Glowing Border
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(NeonCyan, Color(0xFF2B3352), NeonPink, Color(0xFF2B3352), NeonCyan),
                    center = center
                ),
                radius = outerRadius,
                center = center,
                style = Stroke(width = if (isDragging) 2.5.dp.toPx() else 1.5.dp.toPx())
            )

            // Crosshair Guidelines
            drawLine(
                color = Color(0x22FFFFFF),
                start = Offset(center.x - outerRadius * 0.8f, center.y),
                end = Offset(center.x + outerRadius * 0.8f, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0x22FFFFFF),
                start = Offset(center.x, center.y - outerRadius * 0.8f),
                end = Offset(center.x, center.y + outerRadius * 0.8f),
                strokeWidth = 1.dp.toPx()
            )

            // Thumbstick Head
            val thumbCenter = Offset(center.x + animX.value, center.y + animY.value)

            // Thumb shadow / glow
            if (isDragging) {
                drawCircle(
                    color = Color(0x4400F5D4),
                    radius = thumbRadius + 6.dp.toPx(),
                    center = thumbCenter
                )
            }

            // Thumb gradient body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isDragging) listOf(Color(0xFF283658), CyberCardBg) else listOf(CyberCardBg, Color(0xFF14192B)),
                    center = thumbCenter,
                    radius = thumbRadius
                ),
                radius = thumbRadius,
                center = thumbCenter
            )

            // Thumb rim
            drawCircle(
                color = if (isDragging) NeonCyan else Color(0xFF4A5578),
                radius = thumbRadius,
                center = thumbCenter,
                style = Stroke(width = 2.dp.toPx())
            )

            // Thumb inner tactile grip dots
            drawCircle(
                color = if (isDragging) NeonCyan else TextMuted,
                radius = thumbRadius * 0.35f,
                center = thumbCenter,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Stick Center Label
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDragging) NeonCyan else TextMuted
        )
    }
}

private fun calculateNormalized(
    dx: Float,
    dy: Float,
    maxDistance: Float,
    deadzone: Float,
    sensitivity: Float
): Pair<Float, Float> {
    val distance = sqrt(dx * dx + dy * dy)
    val normalizedDist = (distance / maxDistance).coerceIn(0f, 1f)

    if (normalizedDist < deadzone) {
        return Pair(0f, 0f)
    }

    val rescaledDist = ((normalizedDist - deadzone) / (1f - deadzone)).coerceIn(0f, 1f) * sensitivity
    val angle = atan2(dy, dx)

    val normX = (rescaledDist * cos(angle)).coerceIn(-1f, 1f)
    val normY = (rescaledDist * sin(angle)).coerceIn(-1f, 1f)

    return Pair(normX, normY)
}
