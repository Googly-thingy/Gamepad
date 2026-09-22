package com.example.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.protocol.GamepadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class BluetoothControllerClient(
    private val context: Context,
    private val scope: CoroutineScope
) : IControllerClient {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _latencyStats = MutableStateFlow(LatencyStats())
    override val latencyStats: StateFlow<LatencyStats> = _latencyStats.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private var listenJob: Job? = null
    private var pingJob: Job? = null
    private var hzJob: Job? = null

    private var packetsSentCounter = 0L
    private var packetsSentThisSec = 0
    private val pingHistory = ArrayDeque<Long>(20)

    @SuppressLint("MissingPermission")
    override suspend fun connect(target: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        disconnect()

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = ConnectionState.Failed("Bluetooth is disabled or not supported")
            return@withContext false
        }

        _connectionState.value = ConnectionState.Connecting("Connecting Bluetooth to $target...")

        try {
            val paired = try {
                adapter.bondedDevices ?: emptySet()
            } catch (se: SecurityException) {
                _connectionState.value = ConnectionState.Failed("Bluetooth permission (Nearby Devices) required")
                return@withContext false
            }

            val device: BluetoothDevice? = paired.firstOrNull {
                it.address.equals(target, ignoreCase = true) || it.name?.equals(target, ignoreCase = true) == true
            } ?: try {
                adapter.getRemoteDevice(target)
            } catch (_: Throwable) {
                null
            }

            if (device == null) {
                _connectionState.value = ConnectionState.Failed("Device '$target' not found in paired Bluetooth devices. Please pair TV in Android Bluetooth Settings first.")
                return@withContext false
            }

            try {
                adapter.cancelDiscovery()
            } catch (_: Throwable) {}

            val btSocket = device.createRfcommSocketToServiceRecord(BT_GAMEPAD_UUID)
            btSocket.connect()
            socket = btSocket
            outputStream = btSocket.outputStream
            inputStream = btSocket.inputStream

            val deviceName = try { device.name ?: target } catch (_: Throwable) { target }
            val deviceAddr = try { device.address ?: "" } catch (_: Throwable) { "" }

            _connectionState.value = ConnectionState.Connected(
                targetName = deviceName,
                endpoint = deviceAddr,
                protocol = ProtocolType.BLUETOOTH
            )

            startListenerAndPing()
            true
        } catch (se: SecurityException) {
            _connectionState.value = ConnectionState.Failed("Bluetooth permission (Nearby Devices) required")
            false
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Failed(e.message ?: "Bluetooth connection failed")
            false
        }
    }

    override fun sendState(state: GamepadState) {
        val out = outputStream ?: return
        if (_connectionState.value !is ConnectionState.Connected) return

        try {
            val bytes = state.toByteArray()
            synchronized(this) {
                out.write(bytes)
                out.flush()
            }
            packetsSentCounter++
            packetsSentThisSec++
        } catch (_: Exception) {}
    }

    private fun startListenerAndPing() {
        val input = inputStream ?: return

        listenJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(128)
            while (isActive) {
                try {
                    val bytesRead = input.read(buffer)
                    if (bytesRead >= 17 && buffer[0] == GamepadState.PONG_PACKET) {
                        val byteBuf = ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)
                        byteBuf.get() // magic
                        val origTime = byteBuf.long
                        val now = System.currentTimeMillis()
                        val rtt = (now - origTime).coerceAtLeast(0)
                        recordPing(rtt)
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    break
                }
            }
        }

        pingJob = scope.launch(Dispatchers.IO) {
            var seq: Short = 0
            while (isActive) {
                val out = outputStream
                if (out != null) {
                    val pingBytes = GamepadState.createPingPacket(seq++)
                    try {
                        synchronized(this@BluetoothControllerClient) {
                            out.write(pingBytes)
                            out.flush()
                        }
                    } catch (_: Exception) {}
                }
                delay(300)
            }
        }

        hzJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val currentRate = packetsSentThisSec
                packetsSentThisSec = 0
                _latencyStats.value = _latencyStats.value.copy(
                    packetsSent = packetsSentCounter,
                    packetRateHz = currentRate
                )

                val current = _connectionState.value
                if (current is ConnectionState.Connected) {
                    _connectionState.value = current.copy(
                        pingMs = _latencyStats.value.currentPingMs,
                        packetRateHz = currentRate
                    )
                }
            }
        }
    }

    private fun recordPing(rtt: Long) {
        synchronized(pingHistory) {
            if (pingHistory.size >= 20) pingHistory.removeFirst()
            pingHistory.addLast(rtt)
            val min = pingHistory.minOrNull() ?: rtt
            val max = pingHistory.maxOrNull() ?: rtt
            val avg = pingHistory.average().toLong()

            _latencyStats.value = _latencyStats.value.copy(
                currentPingMs = rtt,
                minPingMs = min,
                maxPingMs = max,
                avgPingMs = avg,
                packetsReceived = _latencyStats.value.packetsReceived + 1
            )
        }
    }

    override fun disconnect() {
        listenJob?.cancel()
        listenJob = null
        pingJob?.cancel()
        pingJob = null
        hzJob?.cancel()
        hzJob = null

        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}

        outputStream = null
        inputStream = null
        socket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    companion object {
        val BT_GAMEPAD_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        fun hasBluetoothPermission(context: Context): Boolean {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun getBondedDevices(context: Context): List<Pair<String, String>> {
            return try {
                if (!hasBluetoothPermission(context)) return emptyList()
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
                if (!adapter.isEnabled) return emptyList()
                val set = adapter.bondedDevices ?: emptySet()
                set.map { dev ->
                    val name = try { dev.name?.takeIf { it.isNotBlank() } ?: "Unknown TV" } catch (_: Throwable) { "Unknown TV" }
                    val addr = try { dev.address ?: "" } catch (_: Throwable) { "" }
                    name to addr
                }.filter { it.second.isNotBlank() }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }
}
