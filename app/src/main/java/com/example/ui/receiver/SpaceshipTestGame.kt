package com.example.ui.receiver

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.protocol.GamepadState
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.SwitchRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.XboxGreen
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Laser(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var life: Float = 1.0f
)

data class TargetDrone(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float = 22f
)

@Composable
fun SpaceshipTestGame(
    gamepadState: GamepadState,
    modifier: Modifier = Modifier
) {
    var shipX by remember { mutableFloatStateOf(400f) }
    var shipY by remember { mutableFloatStateOf(300f) }
    var shipVx by remember { mutableFloatStateOf(0f) }
    var shipVy by remember { mutableFloatStateOf(0f) }
    var shipAngle by remember { mutableFloatStateOf(0f) }

    var score by remember { mutableIntStateOf(0) }
    var lastFireTime by remember { mutableStateOf(0L) }

    val lasers = remember { mutableStateListOf<Laser>() }
    val targets = remember {
        mutableStateListOf(
            TargetDrone(200f, 150f, 1.2f, 0.8f),
            TargetDrone(600f, 200f, -0.9f, 1.4f),
            TargetDrone(350f, 450f, 1.5f, -1.0f),
            TargetDrone(700f, 350f, -1.1f, -0.7f)
        )
    }

    // High performance game loop synced to display frames (60/120 Hz)
    LaunchedEffect(Unit) {
        var lastNano = System.nanoTime()
        while (true) {
            withFrameNanos { nowNano ->
                val dt = ((nowNano - lastNano) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastNano = nowNano

                // 1. Controller Inputs: Left Stick controls steering & thrust
                val lx = gamepadState.leftStickX
                val ly = gamepadState.leftStickY
                val mag = sqrt(lx * lx + ly * ly)

                if (mag > 0.15f) {
                    val targetAngle = atan2(ly.toDouble(), lx.toDouble()).toFloat()
                    shipAngle = targetAngle

                    val thrust = mag * 380f
                    shipVx += (cos(targetAngle) * thrust * dt)
                    shipVy += (sin(targetAngle) * thrust * dt)
                }

                // Drag friction
                shipVx *= (1f - 1.8f * dt)
                shipVy *= (1f - 1.8f * dt)

                shipX += shipVx * dt
                shipY += shipVy * dt

                // Wrap boundaries
                if (shipX < 0f) shipX = 900f
                if (shipX > 900f) shipX = 0f
                if (shipY < 0f) shipY = 600f
                if (shipY > 600f) shipY = 0f

                // 2. Firing Lasers with Button A, R1, or R2 trigger
                val isFiring = gamepadState.isPressed(GamepadState.BTN_A) ||
                        gamepadState.isPressed(GamepadState.BTN_R1) ||
                        gamepadState.r2Trigger > 0.5f

                val nowMs = System.currentTimeMillis()
                if (isFiring && nowMs - lastFireTime > 120) {
                    lastFireTime = nowMs
                    val speed = 650f
                    val lvx = cos(shipAngle) * speed + shipVx * 0.3f
                    val lvy = sin(shipAngle) * speed + shipVy * 0.3f
                    lasers.add(Laser(shipX, shipY, lvx, lvy))
                }

                // 3. Update Lasers
                val laserIterator = lasers.listIterator()
                while (laserIterator.hasNext()) {
                    val laser = laserIterator.next()
                    laser.x += laser.vx * dt
                    laser.y += laser.vy * dt
                    laser.life -= dt * 1.5f

                    if (laser.life <= 0f || laser.x < 0 || laser.x > 900 || laser.y < 0 || laser.y > 600) {
                        laserIterator.remove()
                    }
                }

                // 4. Update Targets & Collision
                for (target in targets) {
                    target.x += target.vx
                    target.y += target.vy

                    if (target.x < 20 || target.x > 880) target.vx = -target.vx
                    if (target.y < 20 || target.y > 580) target.vy = -target.vy

                    // Laser hits
                    val hitLaser = lasers.firstOrNull { l ->
                        val dx = l.x - target.x
                        val dy = l.y - target.y
                        (dx * dx + dy * dy) < (target.radius * target.radius)
                    }

                    if (hitLaser != null) {
                        lasers.remove(hitLaser)
                        score += 100
                        target.x = (100..800).random().toFloat()
                        target.y = (80..500).random().toFloat()
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBlack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / 900f
            val scaleY = size.height / 600f

            // Draw Background Starfield
            for (i in 0..40) {
                val sx = (i * 223 + 47) % size.width
                val sy = (i * 179 + 89) % size.height
                drawCircle(Color(0x33FFFFFF), radius = 1.5f, center = Offset(sx, sy))
            }

            // Draw Targets
            for (t in targets) {
                val cx = t.x * scaleX
                val cy = t.y * scaleY
                val r = t.radius * scaleX

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonPink, ElectricViolet),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = NeonPink,
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
            }

            // Draw Lasers
            for (l in lasers) {
                val lx = l.x * scaleX
                val ly = l.y * scaleY
                drawCircle(
                    color = NeonCyan,
                    radius = 4.5f * scaleX,
                    center = Offset(lx, ly)
                )
                drawLine(
                    color = NeonCyan.copy(alpha = 0.6f),
                    start = Offset(lx, ly),
                    end = Offset(lx - l.vx * 0.03f * scaleX, ly - l.vy * 0.03f * scaleY),
                    strokeWidth = 3f * scaleX
                )
            }

            // Draw Player Spaceship
            val px = shipX * scaleX
            val py = shipY * scaleY
            val sizeP = 20f * scaleX

            val p1 = Offset(px + cos(shipAngle) * sizeP * 1.5f, py + sin(shipAngle) * sizeP * 1.5f)
            val p2 = Offset(px + cos(shipAngle + 2.5f) * sizeP, py + sin(shipAngle + 2.5f) * sizeP)
            val p3 = Offset(px + cos(shipAngle - 2.5f) * sizeP, py + sin(shipAngle - 2.5f) * sizeP)

            val shipPath = Path().apply {
                moveTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(px, py)
                lineTo(p3.x, p3.y)
                close()
            }

            drawPath(
                path = shipPath,
                brush = Brush.linearGradient(listOf(NeonCyan, ElectricViolet), start = p1, end = Offset(px, py))
            )
            drawPath(
                path = shipPath,
                color = NeonCyan,
                style = Stroke(width = 2f * scaleX)
            )

            // Thruster flame if moving
            val mag = sqrt(gamepadState.leftStickX * gamepadState.leftStickX + gamepadState.leftStickY * gamepadState.leftStickY)
            if (mag > 0.2f) {
                val flame = Offset(px - cos(shipAngle) * sizeP * 1.2f, py - sin(shipAngle) * sizeP * 1.2f)
                drawCircle(SwitchRed, radius = 6f * scaleX, center = flame)
            }
        }

        // Overlay Score & Instructions
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text(
                text = "FLIGHT TEST SCORE: $score",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = NeonCyan
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) {
            Text(
                text = "Use Left Stick to steer • Press [A] or [R1] to fire lasers • Tests real-time input responsiveness",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
        }
    }
}
