package com.example.network

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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UdpControllerClient(
    private val scope: CoroutineScope
) : IControllerClient {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _latencyStats = MutableStateFlow(LatencyStats())
    override val latencyStats: StateFlow<LatencyStats> = _latencyStats.asStateFlow()

    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = 8766

    private var listenJob: Job? = null
    private var pingJob: Job? = null
    private var hzJob: Job? = null

    private var packetsSentCounter = 0L
    private var packetsSentThisSec = 0
    private var lastPingSentTime = 0L
    private val pingHistory = ArrayDeque<Long>(20)

    override suspend fun connect(target: String, port: Int): Boolean {
        disconnect()

        val cleanIp = target.trim().removePrefix("ws://").removePrefix("http://").split(":")[0]
        _connectionState.value = ConnectionState.Connecting("Connecting UDP to $cleanIp:$port...")

        return try {
            val address = InetAddress.getByName(cleanIp)
            targetAddress = address
            targetPort = port

            val ds = DatagramSocket()
            ds.trafficClass = 0x10 // IPTOS_LOWDELAY
            socket = ds

            _connectionState.value = ConnectionState.Connected(
                targetName = "TV @ $cleanIp",
                endpoint = "udp://$cleanIp:$port",
                protocol = ProtocolType.UDP
            )

            startListenerAndPing()
            true
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Failed(e.message ?: "Failed to open UDP socket")
            false
        }
    }

    override fun sendState(state: GamepadState) {
        val ds = socket ?: return
        val addr = targetAddress ?: return
        if (_connectionState.value !is ConnectionState.Connected) return

        try {
            val bytes = state.toByteArray()
            val packet = DatagramPacket(bytes, bytes.size, addr, targetPort)
            ds.send(packet)
            packetsSentCounter++
            packetsSentThisSec++
        } catch (_: Exception) {}
    }

    private fun startListenerAndPing() {
        val ds = socket ?: return

        // Listen for pong datagrams
        listenJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(64)
            val packet = DatagramPacket(buffer, buffer.size)

            while (isActive && !ds.isClosed) {
                try {
                    ds.receive(packet)
                    if (packet.length >= 17 && buffer[0] == GamepadState.PONG_PACKET) {
                        val byteBuf = ByteBuffer.wrap(buffer, 0, packet.length).order(ByteOrder.LITTLE_ENDIAN)
                        byteBuf.get() // magic
                        val origTime = byteBuf.long
                        val now = System.currentTimeMillis()
                        val rtt = (now - origTime).coerceAtLeast(0)
                        recordPing(rtt)
                    }
                } catch (e: Exception) {
                    if (!isActive || ds.isClosed) break
                }
            }
        }

        // Send ping datagrams
        pingJob = scope.launch(Dispatchers.IO) {
            var seq: Short = 0
            while (isActive && !ds.isClosed) {
                val addr = targetAddress
                if (addr != null) {
                    lastPingSentTime = System.currentTimeMillis()
                    val pingBytes = GamepadState.createPingPacket(seq++)
                    val p = DatagramPacket(pingBytes, pingBytes.size, addr, targetPort)
                    try {
                        ds.send(p)
                    } catch (_: Exception) {}
                }
                delay(250)
            }
        }

        // Measure send rate (Hz)
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
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        targetAddress = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
