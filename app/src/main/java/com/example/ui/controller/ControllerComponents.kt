package com.example.ui.controller

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowLeft
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.protocol.GamepadState
import com.example.ui.theme.ButtonBorder
import com.example.ui.theme.ButtonInactive
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberDarkNavy
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.SwitchRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.XboxGreen

@Composable
fun DpadControl(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    hapticEnabled: Boolean = true,
    onDirectionChange: (up: Boolean, down: Boolean, left: Boolean, right: Boolean) -> Unit
) {
    val view = LocalView.current
    var upPressed by remember { mutableStateOf(false) }
    var downPressed by remember { mutableStateOf(false) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    val armWidth = size * 0.34f
    val armLength = size * 0.44f

    Box(
        modifier = modifier
            .size(size)
            .testTag("dpad_control")
            .pointerInput(Unit) {
                val center = this.size.width / 2f
                val deadzonePx = center * 0.22f

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        if (changes.isEmpty() || changes.all { !it.pressed }) {
                            if (upPressed || downPressed || leftPressed || rightPressed) {
                                upPressed = false
                                downPressed = false
                                leftPressed = false
                                rightPressed = false
                                onDirectionChange(false, false, false, false)
                            }
                        } else {
                            val active = changes.firstOrNull { it.pressed }
                            if (active != null) {
                                val dx = active.position.x - center
                                val dy = active.position.y - center
                                val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                                var newUp = false
                                var newDown = false
                                var newLeft = false
                                var newRight = false

                                if (dist > deadzonePx) {
                                    val angleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))
                                    // 0 = right, 90 = down, 180/-180 = left, -90 = up
                                    newRight = angleDeg in -67.5..67.5
                                    newDown = angleDeg in 22.5..157.5
                                    newLeft = angleDeg > 112.5 || angleDeg < -112.5
                                    newUp = angleDeg in -157.5..-22.5
                                }

                                if (newUp != upPressed || newDown != downPressed || newLeft != leftPressed || newRight != rightPressed) {
                                    if (hapticEnabled && (newUp || newDown || newLeft || newRight)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    upPressed = newUp
                                    downPressed = newDown
                                    leftPressed = newLeft
                                    rightPressed = newRight
                                    onDirectionChange(newUp, newDown, newLeft, newRight)
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Background cross shape
        Box(
            modifier = Modifier
                .width(armWidth)
                .height(size)
                .clip(RoundedCornerShape(8.dp))
                .background(CyberDarkNavy)
                .border(1.5.dp, ButtonBorder, RoundedCornerShape(8.dp))
        )
        Box(
            modifier = Modifier
                .width(size)
                .height(armWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(CyberDarkNavy)
                .border(1.5.dp, ButtonBorder, RoundedCornerShape(8.dp))
        )

        // Up arrow
        DpadSegment(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
            isPressed = upPressed,
            icon = Icons.Default.ArrowDropUp,
            label = "UP"
        )
        // Down arrow
        DpadSegment(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            isPressed = downPressed,
            icon = Icons.Default.ArrowDropDown,
            label = "DOWN"
        )
        // Left arrow
        DpadSegment(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
            isPressed = leftPressed,
            icon = Icons.Default.ArrowLeft,
            label = "LEFT"
        )
        // Right arrow
        DpadSegment(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            isPressed = rightPressed,
            icon = Icons.Default.ArrowRight,
            label = "RIGHT"
        )

        // Center Pivot
        Box(
            modifier = Modifier
                .size(armWidth * 0.7f)
                .clip(CircleShape)
                .background(CyberCardBg)
                .border(1.dp, Color(0xFF384266), CircleShape)
        )
    }
}

@Composable
private fun DpadSegment(
    modifier: Modifier = Modifier,
    isPressed: Boolean,
    icon: ImageVector,
    label: String
) {
    val bgColor by animateColorAsState(if (isPressed) NeonCyan else Color.Transparent, label = "dpad_color")
    val tintColor by animateColorAsState(if (isPressed) CyberDarkNavy else TextPrimary, label = "dpad_tint")

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tintColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun DiamondActionButtons(
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    layoutStyle: String = "XBOX", // XBOX, PLAYSTATION, NINTENDO
    hapticEnabled: Boolean = true,
    onButtonPress: (mask: Int, pressed: Boolean) -> Unit
) {
    val buttonSize = 46.dp
    val spacing = size * 0.38f

    // Layout symbols:
    // Xbox: Y (top), X (left), B (right), A (bottom)
    // Nintendo: X (top), Y (left), A (right), B (bottom)
    // PlayStation: Triangle (top), Square (left), Circle (right), Cross (bottom)
    val (topLabel, topMask, topColor) = when (layoutStyle) {
        "NINTENDO" -> Triple("X", GamepadState.BTN_X, NeonCyan)
        "PLAYSTATION" -> Triple("▲", GamepadState.BTN_Y, XboxGreen)
        else -> Triple("Y", GamepadState.BTN_Y, Color(0xFFFFB703))
    }
    val (leftLabel, leftMask, leftColor) = when (layoutStyle) {
        "NINTENDO" -> Triple("Y", GamepadState.BTN_Y, Color(0xFFFFB703))
        "PLAYSTATION" -> Triple("■", GamepadState.BTN_X, NeonPink)
        else -> Triple("X", GamepadState.BTN_X, NeonCyan)
    }
    val (rightLabel, rightMask, rightColor) = when (layoutStyle) {
        "NINTENDO" -> Triple("A", GamepadState.BTN_A, SwitchRed)
        "PLAYSTATION" -> Triple("●", GamepadState.BTN_B, SwitchRed)
        else -> Triple("B", GamepadState.BTN_B, SwitchRed)
    }
    val (bottomLabel, bottomMask, bottomColor) = when (layoutStyle) {
        "NINTENDO" -> Triple("B", GamepadState.BTN_B, Color(0xFFFFB703))
        "PLAYSTATION" -> Triple("✖", GamepadState.BTN_A, NeonCyan)
        else -> Triple("A", GamepadState.BTN_A, XboxGreen)
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag("action_buttons_diamond"),
        contentAlignment = Alignment.Center
    ) {
        // Decorative center circle
        Box(
            modifier = Modifier
                .size(size * 0.85f)
                .clip(CircleShape)
                .background(Color(0x15FFFFFF))
        )

        // Top button
        ActionButton(
            modifier = Modifier.offset(y = -spacing),
            label = topLabel,
            buttonSize = buttonSize,
            accentColor = topColor,
            hapticEnabled = hapticEnabled,
            testTag = "btn_top",
            onPressChanged = { pressed -> onButtonPress(topMask, pressed) }
        )

        // Left button
        ActionButton(
            modifier = Modifier.offset(x = -spacing),
            label = leftLabel,
            buttonSize = buttonSize,
            accentColor = leftColor,
            hapticEnabled = hapticEnabled,
            testTag = "btn_left",
            onPressChanged = { pressed -> onButtonPress(leftMask, pressed) }
        )

        // Right button
        ActionButton(
            modifier = Modifier.offset(x = spacing),
            label = rightLabel,
            buttonSize = buttonSize,
            accentColor = rightColor,
            hapticEnabled = hapticEnabled,
            testTag = "btn_right",
            onPressChanged = { pressed -> onButtonPress(rightMask, pressed) }
        )

        // Bottom button
        ActionButton(
            modifier = Modifier.offset(y = spacing),
            label = bottomLabel,
            buttonSize = buttonSize,
            accentColor = bottomColor,
            hapticEnabled = hapticEnabled,
            testTag = "btn_bottom",
            onPressChanged = { pressed -> onButtonPress(bottomMask, pressed) }
        )
    }
}

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    label: String,
    buttonSize: Dp = 46.dp,
    accentColor: Color,
    hapticEnabled: Boolean = true,
    testTag: String = "action_btn",
    onPressChanged: (Boolean) -> Unit
) {
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(if (isPressed) 0.88f else 1.0f, label = "btn_scale")
    val glowColor by animateColorAsState(if (isPressed) accentColor else Color.Transparent, label = "btn_glow")

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .testTag(testTag)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        if (hapticEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        onPressChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressChanged(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.width / 2f - 2.dp.toPx()

            // Glowing halo
            if (isPressed) {
                drawCircle(
                    color = accentColor.copy(alpha = 0.4f),
                    radius = radius + 4.dp.toPx(),
                    center = center
                )
            }

            // Button body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isPressed) listOf(accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.5f))
                    else listOf(Color(0xFF262D45), CyberDarkNavy),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Outer ring
            drawCircle(
                color = if (isPressed) accentColor else accentColor.copy(alpha = 0.6f),
                radius = radius,
                center = center,
                style = Stroke(width = if (isPressed) 2.5.dp.toPx() else 1.5.dp.toPx())
            )
        }

        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = if (isPressed) CyberDarkNavy else accentColor
        )
    }
}

@Composable
fun ShoulderBumper(
    modifier: Modifier = Modifier,
    label: String, // "L1" or "R1"
    hapticEnabled: Boolean = true,
    testTag: String = "bumper_btn",
    onPressChanged: (Boolean) -> Unit
) {
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(if (isPressed) NeonCyan else CyberCardBg, label = "bumper_bg")
    val textColor by animateColorAsState(if (isPressed) CyberDarkNavy else TextPrimary, label = "bumper_text")

    Box(
        modifier = modifier
            .width(90.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.5.dp, if (isPressed) NeonCyan else ButtonBorder, RoundedCornerShape(8.dp))
            .testTag(testTag)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        if (hapticEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        onPressChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressChanged(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun AnalogTrigger(
    modifier: Modifier = Modifier,
    label: String, // "L2" or "R2"
    hapticEnabled: Boolean = true,
    testTag: String = "trigger_control",
    onTriggerChanged: (Float) -> Unit
) {
    val view = LocalView.current
    var triggerValue by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .width(90.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CyberDarkNavy)
            .border(1.5.dp, if (triggerValue > 0.05f) NeonPink else ButtonBorder, RoundedCornerShape(8.dp))
            .testTag(testTag)
            .pointerInput(Unit) {
                val fullHeight = this.size.height
                detectTapGestures(
                    onPress = { offset ->
                        val value = (offset.y / fullHeight).coerceIn(0.2f, 1f)
                        triggerValue = value
                        if (hapticEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        onTriggerChanged(value)
                        tryAwaitRelease()
                        triggerValue = 0f
                        onTriggerChanged(0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Visual fill gauge based on trigger squeeze
        Box(
            modifier = Modifier
                .fillMaxWidth(triggerValue)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(Brush.horizontalGradient(listOf(NeonPink.copy(alpha = 0.3f), NeonPink.copy(alpha = 0.7f))))
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (triggerValue > 0.05f) NeonPink else TextPrimary
            )
            if (triggerValue > 0.05f) {
                Text(
                    text = "${(triggerValue * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = NeonPink,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun SystemPillButton(
    modifier: Modifier = Modifier,
    label: String,
    hapticEnabled: Boolean = true,
    testTag: String = "system_btn",
    onPressChanged: (Boolean) -> Unit
) {
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(if (isPressed) ElectricViolet else CyberDarkNavy, label = "sys_btn_bg")

    Box(
        modifier = modifier
            .width(62.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, if (isPressed) NeonCyan else ButtonBorder, RoundedCornerShape(14.dp))
            .testTag(testTag)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        if (hapticEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        onPressChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressChanged(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPressed) TextPrimary else TextSecondary
        )
    }
}
