package com.example.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import com.example.protocol.GamepadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BluetoothHidControllerClient(
    private val context: Context,
    private val scope: CoroutineScope
) : IControllerClient {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _latencyStats = MutableStateFlow(LatencyStats())
    override val latencyStats: StateFlow<LatencyStats> = _latencyStats.asStateFlow()

    private var hidManager: BluetoothHidGamepadManager? = null
    private var packetsSentCounter = 0L
    private var packetsThisSec = 0

    init {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val rate = packetsThisSec
                packetsThisSec = 0
                _latencyStats.value = _latencyStats.value.copy(
                    packetsSent = packetsSentCounter,
                    packetRateHz = rate,
                    currentPingMs = 2 // Direct Bluetooth HID report latency is typically 1-3ms
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(target: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            _connectionState.value = ConnectionState.Failed("Bluetooth HID requires Android 9.0 (API 28)+")
            return@withContext false
        }

        _connectionState.value = ConnectionState.Connecting("Starting Bluetooth HID Gamepad Profile...")

        val manager = BluetoothHidGamepadManager(context)
        hidManager = manager
        manager.start()

        // Observe HID state changes
        scope.launch {
            manager.hidState.collect { state ->
                when (state) {
                    is HidConnectionState.Idle -> {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    is HidConnectionState.Initializing -> {
                        _connectionState.value = ConnectionState.Connecting("Initializing Bluetooth HID...")
                    }
                    is HidConnectionState.Registered -> {
                        _connectionState.value = ConnectionState.Connecting(state.message)
                    }
                    is HidConnectionState.Connected -> {
                        _connectionState.value = ConnectionState.Connected(
                            targetName = state.deviceName,
                            endpoint = state.deviceAddress,
                            protocol = ProtocolType.BLUETOOTH_HID,
                            pingMs = 2,
                            packetRateHz = 60
                        )
                    }
                    is HidConnectionState.Disconnected -> {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    is HidConnectionState.Error -> {
                        _connectionState.value = ConnectionState.Failed(state.error)
                    }
                }
            }
        }

        // If target MAC address is provided and valid, attempt to initiate connection to it
        if (BluetoothAdapter.checkBluetoothAddress(target)) {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val device = try { adapter?.getRemoteDevice(target) } catch (_: Throwable) { null }
            if (device != null) {
                // Wait briefly for HID registration
                delay(800)
                manager.connectToDevice(device)
            }
        }

        true
    }

    override fun sendState(state: GamepadState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            hidManager?.sendGamepadState(state)
            packetsSentCounter++
            packetsThisSec++
        }
    }

    override fun disconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            hidManager?.stop()
        }
        hidManager = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
