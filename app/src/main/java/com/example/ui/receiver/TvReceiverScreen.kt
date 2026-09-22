package com.example.ui.receiver

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.protocol.GamepadState
import com.example.receiver.ReceiverServerStats
import com.example.receiver.TvReceiverServer
import com.example.receiver.TvReceiverService
import com.example.ui.theme.ButtonBorder
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
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
fun TvReceiverScreen(
    server: TvReceiverServer
) {
    val context = LocalContext.current
    val stats by server.stats.collectAsState()
    val state by server.latestGamepadState.collectAsState()
    val clients by server.connectedClients.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Visualizer HUD, 1 = Spaceship Test

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .padding(14.dp)
            .testTag("tv_receiver_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TV Header Bar
            TvTopHeader(
                stats = stats,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onToggleBackground = {
                    if (stats.isRunning) {
                        TvReceiverService.start(context)
                    } else {
                        TvReceiverService.stop(context)
                    }
                },
                onSwitchToController = {
                    context.startActivity(Intent(context, MainActivity::class.java))
                }
            )

            // Main Display Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberDarkNavy)
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            ) {
                if (selectedTab == 0) {
                    GamepadVisualizerHud(
                        state = state,
                        stats = stats,
                        connectedClientsCount = clients.size
                    )
                } else {
                    SpaceshipTestGame(
                        gamepadState = state
                    )
                }
            }

            // Bottom TV Navigation & Status Bar
            TvBottomStatusBar(
                stats = stats,
                onMinimizeToBackground = {
                    try {
                        TvReceiverService.start(context)
                    } catch (_: Throwable) {}
                    try {
                        (context as? android.app.Activity)?.moveTaskToBack(true)
                    } catch (_: Throwable) {
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }
            )
        }
    }
}

@Composable
private fun TvTopHeader(
    stats: ReceiverServerStats,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onToggleBackground: () -> Unit,
    onSwitchToController: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CyberDarkNavy)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: TV Host & IP Display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (stats.isRunning) XboxGreen else SwitchRed)
            )

            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TV INPUT RECEIVER HUB",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ElectricViolet)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text("BACKGROUND READY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
                Text(
                    text = "WebSocket: ws://${stats.localIp}:${stats.wsPort} • UDP: ${stats.udpPort} • BT: ${if (stats.btEnabled && stats.btName.isNotBlank()) stats.btName else if (stats.btEnabled) "Active" else "Off"}",
                    fontSize = 11.sp,
                    color = NeonCyan
                )
            }
        }

        // Center: Mode Tabs
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(CyberCardBg)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Live Gamepad HUD" to Icons.Default.Visibility, "Flight Latency Test" to Icons.Default.RocketLaunch).forEachIndexed { index, (title, icon) ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) NeonCyan else Color.Transparent)
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(icon, contentDescription = title, tint = if (isSelected) CyberBlack else TextSecondary, modifier = Modifier.size(14.dp))
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CyberBlack else TextSecondary
                        )
                    }
                }
            }
        }

        // Right Action: Switch to Controller app
        Button(
            onClick = onSwitchToController,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet, contentColor = TextPrimary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(34.dp).testTag("switch_to_controller_app")
        ) {
            Icon(Icons.Default.Gamepad, contentDescription = "Controller", modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Controller Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GamepadVisualizerHud(
    state: GamepadState,
    stats: ReceiverServerStats,
    connectedClientsCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left telemetry panel
        Card(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "NETWORK TELEMETRY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )

                    TelemetryRow("Connected Clients", "$connectedClientsCount", NeonCyan)
                    TelemetryRow("Packet Polling Rate", "${stats.packetsPerSecond} Hz", XboxGreen)
                    TelemetryRow("Total Input Packets", "${stats.totalPacketsReceived}", TextPrimary)
                    TelemetryRow("Socket Protocol", "RFC-6455 Binary", ElectricViolet)
                    TelemetryRow("Zero-Delay Nagle", "TCP_NODELAY ON", XboxGreen)
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ANALOG AXES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Text("LX: ${"%.2f".format(state.leftStickX)}  LY: ${"%.2f".format(state.leftStickY)}", fontSize = 11.sp, color = TextPrimary)
                    Text("RX: ${"%.2f".format(state.rightStickX)}  RY: ${"%.2f".format(state.rightStickY)}", fontSize = 11.sp, color = TextPrimary)
                    Text("L2: ${"%.2f".format(state.l2Trigger)}  R2: ${"%.2f".format(state.r2Trigger)}", fontSize = 11.sp, color = NeonPink)
                }
            }
        }

        // Center & Right: Visual Gamepad Schematic
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            GamepadCanvasVisualizer(state = state)
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String, color: Color) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextMuted)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun GamepadCanvasVisualizer(state: GamepadState) {
    Canvas(modifier = Modifier.size(460.dp, 280.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Gamepad chassis outline
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF1E2538), Color(0xFF121624))),
            topLeft = Offset(40f, 40f),
            size = Size(size.width - 80f, size.height - 80f),
            cornerRadius = CornerRadius(40f, 40f)
        )
        drawRoundRect(
            color = Color(0xFF384266),
            topLeft = Offset(40f, 40f),
            size = Size(size.width - 80f, size.height - 80f),
            cornerRadius = CornerRadius(40f, 40f),
            style = Stroke(width = 3f)
        )

        // Bumpers L1 & R1
        drawRoundRect(
            color = if (state.isPressed(GamepadState.BTN_L1)) NeonCyan else Color(0xFF28334E),
            topLeft = Offset(70f, 20f),
            size = Size(90f, 24f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = if (state.isPressed(GamepadState.BTN_R1)) NeonCyan else Color(0xFF28334E),
            topLeft = Offset(size.width - 160f, 20f),
            size = Size(90f, 24f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Triggers L2 & R2 pressure indicators
        if (state.l2Trigger > 0.05f) {
            drawRoundRect(
                color = NeonPink,
                topLeft = Offset(70f, 10f),
                size = Size(90f * state.l2Trigger, 8f),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
        if (state.r2Trigger > 0.05f) {
            drawRoundRect(
                color = NeonPink,
                topLeft = Offset(size.width - 160f, 10f),
                size = Size(90f * state.r2Trigger, 8f),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }

        // Left Analog Stick
        val leftStickBase = Offset(cx - 120f, cy + 30f)
        val stickRadius = 36f
        drawCircle(Color(0xFF14192B), radius = stickRadius, center = leftStickBase)
        drawCircle(Color(0xFF384266), radius = stickRadius, center = leftStickBase, style = Stroke(2f))

        val leftThumbOffset = Offset(
            leftStickBase.x + state.leftStickX * (stickRadius * 0.7f),
            leftStickBase.y + state.leftStickY * (stickRadius * 0.7f)
        )
        drawCircle(
            if (state.isPressed(GamepadState.BTN_L3)) NeonCyan else Color(0xFF2D3754),
            radius = 16f,
            center = leftThumbOffset
        )
        drawCircle(NeonCyan, radius = 16f, center = leftThumbOffset, style = Stroke(2f))

        // Right Analog Stick
        val rightStickBase = Offset(cx + 60f, cy + 50f)
        drawCircle(Color(0xFF14192B), radius = stickRadius, center = rightStickBase)
        drawCircle(Color(0xFF384266), radius = stickRadius, center = rightStickBase, style = Stroke(2f))

        val rightThumbOffset = Offset(
            rightStickBase.x + state.rightStickX * (stickRadius * 0.7f),
            rightStickBase.y + state.rightStickY * (stickRadius * 0.7f)
        )
        drawCircle(
            if (state.isPressed(GamepadState.BTN_R3)) NeonCyan else Color(0xFF2D3754),
            radius = 16f,
            center = rightThumbOffset
        )
        drawCircle(NeonCyan, radius = 16f, center = rightThumbOffset, style = Stroke(2f))

        // D-Pad Cross
        val dpadCenter = Offset(cx - 130f, cy - 35f)
        val dpadArm = 18f
        // Up
        drawRect(if (state.isPressed(GamepadState.BTN_DPAD_UP)) NeonCyan else Color(0xFF252D42), Offset(dpadCenter.x - 7f, dpadCenter.y - dpadArm - 8f), Size(14f, 16f))
        // Down
        drawRect(if (state.isPressed(GamepadState.BTN_DPAD_DOWN)) NeonCyan else Color(0xFF252D42), Offset(dpadCenter.x - 7f, dpadCenter.y + 8f), Size(14f, 16f))
        // Left
        drawRect(if (state.isPressed(GamepadState.BTN_DPAD_LEFT)) NeonCyan else Color(0xFF252D42), Offset(dpadCenter.x - dpadArm - 8f, dpadCenter.y - 7f), Size(16f, 14f))
        // Right
        drawRect(if (state.isPressed(GamepadState.BTN_DPAD_RIGHT)) NeonCyan else Color(0xFF252D42), Offset(dpadCenter.x + 8f, dpadCenter.y - 7f), Size(16f, 14f))

        // Action Buttons ABXY Diamond
        val abxyCenter = Offset(cx + 120f, cy - 30f)
        val dist = 24f

        // Y (Top - Yellow)
        drawCircle(if (state.isPressed(GamepadState.BTN_Y)) Color(0xFFFFB703) else Color(0x44FFB703), radius = 11f, center = Offset(abxyCenter.x, abxyCenter.y - dist))
        // X (Left - Blue/Cyan)
        drawCircle(if (state.isPressed(GamepadState.BTN_X)) NeonCyan else Color(0x4400F5D4), radius = 11f, center = Offset(abxyCenter.x - dist, abxyCenter.y))
        // B (Right - Red)
        drawCircle(if (state.isPressed(GamepadState.BTN_B)) SwitchRed else Color(0x44FF3344), radius = 11f, center = Offset(abxyCenter.x + dist, abxyCenter.y))
        // A (Bottom - Green)
        drawCircle(if (state.isPressed(GamepadState.BTN_A)) XboxGreen else Color(0x44107C10), radius = 11f, center = Offset(abxyCenter.x, abxyCenter.y + dist))

        // Center System Buttons
        drawCircle(if (state.isPressed(GamepadState.BTN_SELECT)) ElectricViolet else Color(0xFF2E3852), radius = 6f, center = Offset(cx - 24f, cy - 20f))
        drawCircle(if (state.isPressed(GamepadState.BTN_START)) ElectricViolet else Color(0xFF2E3852), radius = 6f, center = Offset(cx + 24f, cy - 20f))
        drawCircle(if (state.isPressed(GamepadState.BTN_HOME)) NeonCyan else Color(0xFF2E3852), radius = 8f, center = Offset(cx, cy))
    }
}

@Composable
private fun TvBottomStatusBar(
    stats: ReceiverServerStats,
    onMinimizeToBackground: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CyberDarkNavy)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(XboxGreen)
            )
            Text(
                text = "STATUS: ${if (stats.isRunning) "SERVER LISTENING" else "STOPPED"}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (stats.isRunning) XboxGreen else SwitchRed
            )
            Text(
                text = "• Packets will stream directly into background foreground service",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        Button(
            onClick = onMinimizeToBackground,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBlack),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(34.dp).testTag("minimize_to_background_button")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Background", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Send to Background & Launch Games", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
