package com.example.network

import com.example.protocol.GamepadState
import kotlinx.coroutines.flow.StateFlow

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val message: String) : ConnectionState
    data class Connected(
        val targetName: String,
        val endpoint: String,
        val protocol: ProtocolType,
        val pingMs: Long = 0,
        val packetRateHz: Int = 0
    ) : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

enum class ProtocolType(val label: String) {
    WEBSOCKET("WebSocket (Wi-Fi)"),
    UDP("UDP Fast-Packet"),
    BLUETOOTH("Bluetooth RFCOMM")
}

data class LatencyStats(
    val currentPingMs: Long = 0,
    val minPingMs: Long = 0,
    val maxPingMs: Long = 0,
    val avgPingMs: Long = 0,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val packetRateHz: Int = 0
)

data class DiscoveredTv(
    val name: String,
    val ip: String,
    val wsPort: Int = 8765,
    val udpPort: Int = 8766,
    val lastSeen: Long = System.currentTimeMillis()
)

interface IControllerClient {
    val connectionState: StateFlow<ConnectionState>
    val latencyStats: StateFlow<LatencyStats>

    suspend fun connect(target: String, port: Int = 8765): Boolean
    fun sendState(state: GamepadState)
    fun disconnect()
}
