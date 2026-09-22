package com.example.ui.controller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.TvServerEntity
import com.example.network.ConnectionState
import com.example.network.DiscoveredTv
import com.example.network.ProtocolType
import com.example.ui.theme.AmberWarning
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
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ConnectionDialog(
    discoveredTvsState: StateFlow<List<DiscoveredTv>>,
    savedServers: List<TvServerEntity>,
    currentProtocol: ProtocolType,
    connectionState: ConnectionState,
    phoneIp: String,
    onRefreshDiscovery: () -> Unit,
    onGetBondedDevices: () -> List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConnect: (target: String, port: Int, protocol: ProtocolType, saveToHistory: Boolean) -> Unit
) {
    val context = LocalContext.current
    val discoveredTvs by discoveredTvsState.collectAsState()
    var selectedProtocol by remember { mutableStateOf(currentProtocol) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("8765") }
    var pairedDevices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var hasBtPermission by remember { mutableStateOf(true) }

    fun refreshBluetooth() {
        pairedDevices = onGetBondedDevices()
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasBtPermission = results.values.all { it }
        if (hasBtPermission) {
            refreshBluetooth()
        }
    }

    LaunchedEffect(selectedProtocol) {
        if (selectedProtocol == ProtocolType.BLUETOOTH || selectedProtocol == ProtocolType.BLUETOOTH_HID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                hasBtPermission = granted
                if (!granted) {
                    btPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        )
                    )
                }
            }
            refreshBluetooth()
        } else {
            onRefreshDiscovery()
            if (selectedProtocol == ProtocolType.UDP && manualPort == "8765") {
                manualPort = "8766"
            } else if (selectedProtocol == ProtocolType.WEBSOCKET && manualPort == "8766") {
                manualPort = "8765"
            }
        }
    }

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .testTag("connection_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberDarkNavy),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
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
                        Column {
                            Text(
                                text = "Connect to TV Receiver",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Your Phone IP: $phoneIp",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("close_conn_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Connection State Feedback Banner (Connecting, Failed, or Connected)
                when (connectionState) {
                    is ConnectionState.Connecting -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AmberWarning.copy(alpha = 0.15f))
                                .border(1.dp, AmberWarning, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AmberWarning, strokeWidth = 2.dp)
                            Text(
                                text = connectionState.message,
                                fontSize = 11.sp,
                                color = AmberWarning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    is ConnectionState.Failed -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SwitchRed.copy(alpha = 0.15f))
                                .border(1.dp, SwitchRed, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = "Error", tint = SwitchRed, modifier = Modifier.size(16.dp))
                            Text(
                                text = connectionState.reason,
                                fontSize = 11.sp,
                                color = SwitchRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    is ConnectionState.Connected -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(XboxGreen.copy(alpha = 0.15f))
                                .border(1.dp, XboxGreen, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Connected", tint = XboxGreen, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Connected to ${connectionState.targetName}",
                                    fontSize = 11.sp,
                                    color = XboxGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = XboxGreen, contentColor = CyberBlack),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    ConnectionState.Disconnected -> {}
                }

                // Protocol Selector Tabs
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
                                .background(if (isSelected) (if (proto == ProtocolType.BLUETOOTH_HID) XboxGreen else NeonCyan) else Color.Transparent)
                                .clickable { selectedProtocol = proto }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (proto) {
                                    ProtocolType.WEBSOCKET -> "Wi-Fi WS"
                                    ProtocolType.UDP -> "UDP Fast"
                                    ProtocolType.BLUETOOTH -> "BT App"
                                    ProtocolType.BLUETOOTH_HID -> "BT HID"
                                },
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) CyberBlack else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Content for Bluetooth Tabs vs Wi-Fi Tabs
                if (selectedProtocol == ProtocolType.BLUETOOTH || selectedProtocol == ProtocolType.BLUETOOTH_HID) {
                    // Explanatory banner for Bluetooth HID mode
                    if (selectedProtocol == ProtocolType.BLUETOOTH_HID) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(XboxGreen.copy(alpha = 0.15f))
                                .border(1.dp, XboxGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Gamepad, contentDescription = "Native HID", tint = XboxGreen, modifier = Modifier.size(16.dp))
                            Column {
                                Text(
                                    text = "Native Hardware Gamepad Profile",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = XboxGreen
                                )
                                Text(
                                    text = "Emulates an official gamepad. Detected automatically in Beach Buggy Racing & PPSSPP!",
                                    fontSize = 9.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // BLUETOOTH VIEW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PAIRED BLUETOOTH DEVICES (${pairedDevices.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedProtocol == ProtocolType.BLUETOOTH_HID) XboxGreen else NeonCyan,
                            letterSpacing = 1.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { refreshBluetooth() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonCyan, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                    } catch (_: Throwable) {}
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Settings", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (!hasBtPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Button(
                            onClick = {
                                btPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                        Manifest.permission.BLUETOOTH_SCAN
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = CyberBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Grant Bluetooth Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (pairedDevices.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pairedDevices.forEach { (name, addr) ->
                                val isHid = selectedProtocol == ProtocolType.BLUETOOTH_HID
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberCardBg)
                                        .border(1.dp, if (isHid) XboxGreen.copy(alpha = 0.3f) else CyberCardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            onConnect(addr, 0, selectedProtocol, false)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            if (isHid) Icons.Default.Gamepad else Icons.Default.Bluetooth,
                                            contentDescription = "BT",
                                            tint = if (isHid) XboxGreen else NeonCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(text = name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Text(text = addr, fontSize = 10.sp, color = TextMuted)
                                        }
                                    }
                                    Button(
                                        onClick = { onConnect(addr, 0, selectedProtocol, false) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isHid) XboxGreen else NeonCyan,
                                            contentColor = CyberBlack
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(if (isHid) "Connect HID" else "Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCardBg)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No paired TV or Bluetooth devices found.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                    } catch (_: Throwable) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet, contentColor = TextPrimary),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Open Phone Bluetooth Settings to Pair TV", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // WI-FI (WebSocket / UDP) VIEW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DISCOVERED ON LOCAL NETWORK (${discoveredTvs.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 1.sp
                        )
                        IconButton(
                            onClick = { onRefreshDiscovery() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = NeonCyan, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (discoveredTvs.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            discoveredTvs.forEach { tv ->
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
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(XboxGreen)
                                        )
                                        Column {
                                            Text(text = tv.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Text(
                                                text = "${tv.ip}:${if (selectedProtocol == ProtocolType.UDP) tv.udpPort else tv.wsPort}",
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val port = if (selectedProtocol == ProtocolType.UDP) tv.udpPort else tv.wsPort
                                            onConnect(tv.ip, port, selectedProtocol, true)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBlack),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCardBg)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Searching local network for TV Receiver...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonCyan
                            )
                            Text(
                                text = "1. Open 'TV Receiver' app on your TV (make sure both are on same Wi-Fi).\n2. Note the IP shown on your TV screen (e.g. 192.168.1.150).\n3. Enter it in the manual box below if auto-discovery takes time.",
                                fontSize = 10.sp,
                                color = TextMuted,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Manual Entry (Only for Wi-Fi and Bluetooth App modes)
                if (selectedProtocol != ProtocolType.BLUETOOTH_HID) {
                    Spacer(modifier = Modifier.height(10.dp))

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
                                    text = if (selectedProtocol == ProtocolType.BLUETOOTH) "e.g. Living Room TV or MAC" else "e.g. 192.168.1.150",
                                    fontSize = 11.sp,
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
                                modifier = Modifier.width(68.dp).testTag("manual_port_input"),
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
                                    val port = manualPort.toIntOrNull() ?: if (selectedProtocol == ProtocolType.UDP) 8766 else 8765
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
                            Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
