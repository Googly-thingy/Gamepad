package com.example.ui.controller

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ControllerProfileEntity
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberDarkNavy
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsDialog(
    profile: ControllerProfileEntity,
    onDismiss: () -> Unit,
    onSaveProfile: (ControllerProfileEntity) -> Unit
) {
    var hapticEnabled by remember { mutableStateOf(profile.hapticEnabled) }
    var deadzone by remember { mutableFloatStateOf(profile.stickDeadzone) }
    var sensitivity by remember { mutableFloatStateOf(profile.stickSensitivity) }
    var layoutStyle by remember { mutableStateOf(profile.buttonLayout) }

    Dialog(onDismissRequest = {
        onSaveProfile(profile.copy(
            hapticEnabled = hapticEnabled,
            stickDeadzone = deadzone,
            stickSensitivity = sensitivity,
            buttonLayout = layoutStyle
        ))
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("settings_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberDarkNavy),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Settings", tint = NeonCyan)
                        Text(
                            text = "Controller Calibration",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = {
                            onSaveProfile(profile.copy(
                                hapticEnabled = hapticEnabled,
                                stickDeadzone = deadzone,
                                stickSensitivity = sensitivity,
                                buttonLayout = layoutStyle
                            ))
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp).testTag("close_settings_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Haptic Feedback Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Tactile Haptics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Vibrate on button & thumbstick touches", fontSize = 11.sp, color = TextMuted)
                    }
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = { hapticEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberBlack,
                            checkedTrackColor = NeonCyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CyberCardBg
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stick Deadzone Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Stick Deadzone", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("${(deadzone * 100).toInt()}%", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = deadzone,
                        onValueChange = { deadzone = it },
                        valueRange = 0.02f..0.30f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = CyberCardBg
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stick Sensitivity Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Stick Sensitivity", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("${"%.1f".format(sensitivity)}x", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = sensitivity,
                        onValueChange = { sensitivity = it },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = CyberCardBg
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Button Layout Preset
                Text("Button Glyph Style", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberCardBg)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("XBOX" to "Xbox (Y/X/B/A)", "PLAYSTATION" to "PS (▲/■/●/✖)", "NINTENDO" to "Switch (X/Y/A/B)").forEach { (style, name) ->
                        val isSelected = layoutStyle == style
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ElectricViolet else Color.Transparent)
                                .clickable { layoutStyle = style }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
