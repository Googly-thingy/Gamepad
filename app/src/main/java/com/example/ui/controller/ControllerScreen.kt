package com.example.ui.controller

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ConnectionState
import com.example.network.ProtocolType
import com.example.protocol.GamepadState
import com.example.receiver.TvReceiverActivity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ButtonBorder
import com.example.ui.theme.CyberBlack
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
fun ControllerScreen(
    viewModel: ControllerViewModel
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val latencyStats by viewModel.latencyStats.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val savedServers by viewModel.savedServers.collectAsState()

    var showConnectionDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(CyberDarkNavy, CyberBlack),
                    radius = 1200f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("controller_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top HUD Bar
            TopHudBar(
                connectionState = connectionState,
                latencyStats = latencyStats,
                onOpenConnection = { showConnectionDialog = true },
                onOpenSettings = { showSettingsDialog = true },
                onSwitchToTv = {
                    context.startActivity(Intent(context, TvReceiverActivity::class.java))
                }
            )

            // Main Controller Wings Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT WING (L2, L1, Thumbstick, D-Pad)
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Shoulder buttons row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnalogTrigger(
                            label = "L2",
                            hapticEnabled = profile.hapticEnabled,
                            testTag = "trigger_l2",
                            onTriggerChanged = { viewModel.setL2Trigger(it) }
                        )
                        ShoulderBumper(
                            label = "L1",
                            hapticEnabled = profile.hapticEnabled,
                            testTag = "bumper_l1",
                            onPressChanged = { viewModel.setButtonPressed(GamepadState.BTN_L1, it) }
                        )
                    }

                    // Left Thumbstick & D-Pad
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VirtualJoystick(
                            size = 142.dp,
                            label = "L-STICK",
                            deadzone = profile.stickDeadzone,
                            sensitivity = profile.stickSensitivity,
                            hapticEnabled = profile.hapticEnabled,
                            testTag = "left_virtual_joystick",
                            onValueChange = { x, y -> viewModel.setLeftStick(x, y) },
                            onStickClick = { viewModel.setButtonPressed(GamepadState.BTN_L3, true) }
                        )

                        DpadControl(
                            size = 142.dp,
                            hapticEnabled = profile.hapticEnabled,
                            onDirectionChange = { up, down, left, right ->
                                viewModel.setButtonPressed(GamepadState.BTN_DPAD_UP, up)
                                viewModel.setButtonPressed(GamepadState.BTN_DPAD_DOWN, down)
                                viewModel.setButtonPressed(GamepadState.BTN_DPAD_LEFT, left)
                                viewModel.setButtonPressed(GamepadState.BTN_DPAD_RIGHT, right)
                            }
                        )
                    }
                }

                // CENTER CHASSIS (System Buttons & Logo)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Top System Buttons (SELECT / START)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SystemPillButton(
                            label = "SELECT",
                            hapticEnabled = profile.hapticEnabled,
                            testTag = "btn_select",
                            onPressChanged = { viewModel.setButtonPressed(GamepadState.BTN_SELECT, it) }
                        )
                        SystemPillButton(
                            label = "START",
                            hapticEnabled = profile.hapticEnabled,
                            testTag = "btn_start",
                            onPressChanged = { viewModel.setButtonPressed(GamepadState.BTN_START, it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Center Home & Turbo
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SystemPillButton(
                            label = "TURBO",
                            hapticEnabled = profile.hapticEnabled,
                            testTag = "btn_turbo",
                            onPressChanged = { viewModel.setButtonPressed(GamepadState.BTN_TURBO, it) }
                        )
                        SystemPillButton(
                            label = "HOME",
                            hapticEnabled = profile.hapticEnabled,
                            testTag = "btn_home",
                            onPressChanged = { viewModel.setButtonPressed(GamepadState.BTN_HOME, it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Center Audio/Haptic Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberCardBg)
                            .border(1.dp, ButtonBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ULTRA-LOW LATENCY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // RIGHT WING (R1, R2, Action Buttons, Right Thumbstick)
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Shoulder buttons row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShoulderBumper(
                            label = "R1",
                            hapticEnabled = profile.hapticEnabled,
                            testTag = "bumper_r1",
                            onPressChanged = { viewModel.setButtonPressed(GamepadState.BTN_R1, it) }
                        )
                        AnalogTrigger(
                            label = "R2",
                            hapticEnabled = profile.hapticEnabled,
                            testTag = "trigger_r2",
                            onTriggerChanged = { viewModel.setR2Trigger(it) }
                        )
                    }

                    // Action Buttons Diamond & Right Thumbstick
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DiamondActionButtons(
                            size = 142.dp,
                            layoutStyle = profile.buttonLayout,
                            hapticEnabled = profile.hapticEnabled,
                            onButtonPress = { mask, pressed ->
                                viewModel.setButtonPressed(mask, pressed)
                            }
                        )

                        VirtualJoystick(
                            size = 142.dp,
                            label = "R-STICK",
                            deadzone = profile.stickDeadzone,
                            sensitivity = profile.stickSensitivity,
                            hapticEnabled = profile.hapticEnabled,
                            testTag = "right_virtual_joystick",
                            onValueChange = { x, y -> viewModel.setRightStick(x, y) },
                            onStickClick = { viewModel.setButtonPressed(GamepadState.BTN_R3, true) }
                        )
                    }
                }
            }
        }

        // Connection Dialog
        if (showConnectionDialog) {
            ConnectionDialog(
                discoveredTvsState = viewModel.discoveredTvs,
                savedServers = savedServers,
                currentProtocol = ProtocolType.WEBSOCKET,
                connectionState = connectionState,
                phoneIp = viewModel.getPhoneIpAddress(),
                onRefreshDiscovery = { viewModel.refreshDiscovery() },
                onGetBondedDevices = { viewModel.getBondedBluetoothDevices() },
                onDismiss = { showConnectionDialog = false },
                onConnect = { target, port, protocol, saveToHistory ->
                    viewModel.connect(target, port, protocol, saveToHistory)
                }
            )
        }

        // Settings Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                profile = profile,
                onDismiss = { showSettingsDialog = false },
                onSaveProfile = { viewModel.saveProfile(it) }
            )
        }
    }
}

@Composable
private fun TopHudBar(
    connectionState: ConnectionState,
    latencyStats: com.example.network.LatencyStats,
    onOpenConnection: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchToTv: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CyberDarkNavy)
            .border(1.dp, ButtonBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Connection Status Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenConnection() }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            val (statusColor, statusText, subText) = when (connectionState) {
                is ConnectionState.Connected -> {
                    val ping = connectionState.pingMs
                    val pingColor = if (ping < 10) XboxGreen else if (ping < 30) AmberWarning else SwitchRed
                    Triple(pingColor, "CONNECTED • ${connectionState.targetName}", "${ping}ms • ${connectionState.packetRateHz}Hz")
                }
                is ConnectionState.Connecting -> Triple(AmberWarning, "CONNECTING...", connectionState.message)
                is ConnectionState.Failed -> Triple(SwitchRed, "ERROR", connectionState.reason)
                ConnectionState.Disconnected -> Triple(Color(0xFF6B7280), "DISCONNECTED", "Tap to connect TV")
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Column {
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subText,
                    fontSize = 9.sp,
                    color = TextSecondary
                )
            }
        }

        // Right Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Connect Button
            Button(
                onClick = onOpenConnection,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connectionState is ConnectionState.Connected) CyberCardBg else NeonCyan,
                    contentColor = if (connectionState is ConnectionState.Connected) TextPrimary else CyberBlack
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).testTag("hud_connect_button")
            ) {
                Icon(
                    imageVector = if (connectionState is ConnectionState.Connected) Icons.Default.Link else Icons.Default.Cable,
                    contentDescription = "Connect",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (connectionState is ConnectionState.Connected) "Switch TV" else "Pair TV",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Settings Button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(32.dp).testTag("hud_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Calibrate",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Switch to TV Receiver Mode Button
            Button(
                onClick = onSwitchToTv,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricViolet,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp).testTag("hud_switch_tv_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "TV Mode",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "TV Receiver",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
