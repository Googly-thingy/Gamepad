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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

class WebSocketControllerClient(
    private val scope: CoroutineScope
) : IControllerClient {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _latencyStats = MutableStateFlow(LatencyStats())
    override val latencyStats: StateFlow<LatencyStats> = _latencyStats.asStateFlow()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var hzJob: Job? = null

    private var packetsSentCounter = 0L
    private var packetsSentThisSec = 0
    private var lastPingSentTime = 0L
    private val pingHistory = ArrayDeque<Long>(20)

    // Ultra low latency OkHttpClient with zero-delay sockets
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .socketFactory(LowLatencySocketFactory())
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for long-lived socket
            .pingInterval(10, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun connect(target: String, port: Int): Boolean {
        disconnect()

        val cleanIp = target.trim().removePrefix("ws://").removePrefix("http://").split(":")[0]
        val wsUrl = "ws://$cleanIp:$port/ws"
        _connectionState.value = ConnectionState.Connecting("Connecting to $wsUrl...")

        val request = Request.Builder()
            .url(wsUrl)
            .header("Sec-WebSocket-Protocol", "gamepad-v1")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.Connected(
                    targetName = "TV @ $cleanIp",
                    endpoint = wsUrl,
                    protocol = ProtocolType.WEBSOCKET
                )
                startPingAndRateMonitor()
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.isNotEmpty() && data[0] == GamepadState.PONG_PACKET && data.size >= 17) {
                    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.get() // consume pong magic
                    val origTimestamp = buffer.long
                    val receiverTimestamp = buffer.long
                    val now = System.currentTimeMillis()
                    val rtt = (now - origTimestamp).coerceAtLeast(0)
                    recordPing(rtt)
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (text.startsWith("{\"pong\":")) {
                    val now = System.currentTimeMillis()
                    if (lastPingSentTime > 0) {
                        recordPing((now - lastPingSentTime).coerceAtLeast(0))
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.Failed(t.message ?: "Connection failed")
                stopMonitors()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.Disconnected
                stopMonitors()
            }
        })

        return true
    }

    override fun sendState(state: GamepadState) {
        val ws = webSocket ?: return
        if (_connectionState.value !is ConnectionState.Connected) return

        val binary = state.toByteArray()
        ws.send(binary.toByteString())
        packetsSentCounter++
        packetsSentThisSec++
    }

    private fun startPingAndRateMonitor() {
        stopMonitors()

        // High frequency ping for exact RTT latency
        pingJob = scope.launch(Dispatchers.IO) {
            var seq: Short = 0
            while (isActive) {
                val ws = webSocket
                if (ws != null && _connectionState.value is ConnectionState.Connected) {
                    lastPingSentTime = System.currentTimeMillis()
                    val pingBytes = GamepadState.createPingPacket(seq++)
                    ws.send(pingBytes.toByteString())
                }
                delay(250) // Ping 4 times per second
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

                // Update connection state with live ping & hz
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

    private fun stopMonitors() {
        pingJob?.cancel()
        pingJob = null
        hzJob?.cancel()
        hzJob = null
    }

    override fun disconnect() {
        stopMonitors()
        try {
            webSocket?.close(1000, "Client disconnected")
        } catch (_: Exception) {}
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Custom SocketFactory that configures TCP_NODELAY = true for instant delivery.
     */
    private class LowLatencySocketFactory : SocketFactory() {
        private val defaultFactory = getDefault()

        override fun createSocket(): Socket {
            val socket = defaultFactory.createSocket()
            socket.tcpNoDelay = true
            socket.sendBufferSize = 64 * 1024
            socket.trafficClass = 0x10 // Low-delay IPTOS
            return socket
        }

        override fun createSocket(host: String?, port: Int): Socket {
            val socket = defaultFactory.createSocket(host, port)
            socket.tcpNoDelay = true
            return socket
        }

        override fun createSocket(host: String?, port: Int, localHost: java.net.InetAddress?, localPort: Int): Socket {
            val socket = defaultFactory.createSocket(host, port, localHost, localPort)
            socket.tcpNoDelay = true
            return socket
        }

        override fun createSocket(host: java.net.InetAddress?, port: Int): Socket {
            val socket = defaultFactory.createSocket(host, port)
            socket.tcpNoDelay = true
            return socket
        }

        override fun createSocket(address: java.net.InetAddress?, port: Int, localAddress: java.net.InetAddress?, localPort: Int): Socket {
            val socket = defaultFactory.createSocket(address, port, localAddress, localPort)
            socket.tcpNoDelay = true
            return socket
        }
    }
}
