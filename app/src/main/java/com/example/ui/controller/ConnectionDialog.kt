package com.example.ui.controller

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.TvServerEntity
import com.example.network.DiscoveredTv
import com.example.network.ProtocolType
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberDarkNavy
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ConnectionDialog(
    discoveredTvsState: StateFlow<List<DiscoveredTv>>,
    savedServers: List<TvServerEntity>,
    currentProtocol: ProtocolType,
    onDismiss: () -> Unit,
    onConnect: (target: String, port: Int, protocol: ProtocolType, saveToHistory: Boolean) -> Unit
) {
    val discoveredTvs by discoveredTvsState.collectAsState()
    var selectedProtocol by remember { mutableStateOf(currentProtocol) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("8765") }

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_rot"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 420.dp)
                .testTag("connection_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberDarkNavy),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Connect",
                            tint = NeonCyan,
                            modifier = Modifier.rotate(rotation)
                        )
                        Text(
                            text = "Connect to TV Receiver",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_conn_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Protocol selector tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberCardBg)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProtocolType.values().forEach { proto ->
                        val isSelected = selectedProtocol == proto
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) NeonCyan else Color.Transparent)
                                .clickable { selectedProtocol = proto }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (proto) {
                                    ProtocolType.WEBSOCKET -> "WebSocket"
                                    ProtocolType.UDP -> "UDP Fast"
                                    ProtocolType.BLUETOOTH -> "Bluetooth"
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) CyberBlack else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto-Discovered TVs list
                Text(
                    text = "DISCOVERED ON LOCAL NETWORK (${discoveredTvs.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (discoveredTvs.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 130.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(discoveredTvs) { tv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberCardBg)
                                    .border(1.dp, CyberCardBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        val port = if (selectedProtocol == ProtocolType.UDP) tv.udpPort else tv.wsPort
                                        onConnect(tv.ip, port, selectedProtocol, true)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(NeonCyan)
                                    )
                                    Column {
                                        Text(text = tv.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(text = "${tv.ip}:${if (selectedProtocol == ProtocolType.UDP) tv.udpPort else tv.wsPort}", fontSize = 11.sp, color = TextMuted)
                                    }
                                }

                                Button(
                                    onClick = {
                                        val port = if (selectedProtocol == ProtocolType.UDP) tv.udpPort else tv.wsPort
                                        onConnect(tv.ip, port, selectedProtocol, true)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBlack),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberCardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Scanning for active TV receivers on Wi-Fi...",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Manual IP / Bluetooth Device Entry
                Text(
                    text = if (selectedProtocol == ProtocolType.BLUETOOTH) "MANUAL BLUETOOTH MAC / NAME" else "MANUAL TV IP ADDRESS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        placeholder = {
                            Text(
                                text = if (selectedProtocol == ProtocolType.BLUETOOTH) "e.g. Living Room TV or MAC" else "192.168.1.150",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("manual_ip_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = CyberCardBg,
                            unfocusedContainerColor = CyberCardBg
                        )
                    )

                    if (selectedProtocol != ProtocolType.BLUETOOTH) {
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it },
                            modifier = Modifier.width(72.dp).testTag("manual_port_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = CyberCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = CyberCardBg,
                                unfocusedContainerColor = CyberCardBg
                            )
                        )
                    }

                    Button(
                        onClick = {
                            if (manualIp.isNotBlank()) {
                                val port = manualPort.toIntOrNull() ?: 8765
                                onConnect(manualIp.trim(), port, selectedProtocol, true)
                            }
                        },
                        enabled = manualIp.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricViolet,
                            contentColor = TextPrimary,
                            disabledContainerColor = CyberCardBg
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(48.dp).testTag("manual_connect_button")
                    ) {
                        Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
