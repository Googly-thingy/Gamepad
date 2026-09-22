package com.example.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.network.BluetoothControllerClient
import com.example.protocol.GamepadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class ConnectedClient(
    val id: String,
    val address: String,
    val protocol: String,
    val connectedAt: Long = System.currentTimeMillis(),
    val packetsReceived: Long = 0,
    val lastPingMs: Long = 0
)

data class ReceiverServerStats(
    val isRunning: Boolean = false,
    val localIp: String = "127.0.0.1",
    val wsPort: Int = 8765,
    val udpPort: Int = 8766,
    val btEnabled: Boolean = false,
    val btName: String = "",
    val totalPacketsReceived: Long = 0,
    val packetsPerSecond: Int = 0,
    val connectedClientsCount: Int = 0,
    val statusMessage: String = "Ready"
)

class TvReceiverServer private constructor(
    private val context: Context
) {
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _stats = MutableStateFlow(ReceiverServerStats())
    val stats: StateFlow<ReceiverServerStats> = _stats.asStateFlow()

    private val _latestGamepadState = MutableStateFlow(GamepadState())
    val latestGamepadState: StateFlow<GamepadState> = _latestGamepadState.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients.asStateFlow()

    private var wsServerSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var btServerSocket: BluetoothServerSocket? = null

    private var wsJob: Job? = null
    private var udpJob: Job? = null
    private var btJob: Job? = null
    private var beaconJob: Job? = null
    private var beaconListenJob: Job? = null
    private var statsJob: Job? = null

    private var totalPacketsCounter = 0L
    private var packetsThisSec = 0
    private var multicastLock: WifiManager.MulticastLock? = null

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getSafeBluetoothName(): String {
        return try {
            if (hasBluetoothPermission()) {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                adapter?.name?.takeIf { it.isNotBlank() } ?: Build.MODEL
            } else {
                Build.MODEL
            }
        } catch (_: Throwable) {
            Build.MODEL
        }
    }

    private fun isSafeBluetoothEnabled(): Boolean {
        return try {
            if (hasBluetoothPermission()) {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                adapter?.isEnabled == true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun startServer(wsPort: Int = 8765, udpPort: Int = 8766) {
        if (_stats.value.isRunning) return

        try {
            val localIp = getLocalIpAddress()
            val btName = getSafeBluetoothName()
            val btEnabled = isSafeBluetoothEnabled()

            _stats.value = ReceiverServerStats(
                isRunning = true,
                localIp = localIp,
                wsPort = wsPort,
                udpPort = udpPort,
                btEnabled = btEnabled,
                btName = btName,
                statusMessage = "Listening on ws://$localIp:$wsPort"
            )

            try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifi?.createMulticastLock("TvReceiverMulticast")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
            } catch (_: Throwable) {}

            startWebSocketServer(wsPort)
            startUdpServer(udpPort)
            startBluetoothServer()
            startBeaconBroadcast(localIp, wsPort, udpPort)
            startBeaconListener(localIp, wsPort, udpPort)
            startStatsMonitor()
        } catch (t: Throwable) {
            Log.e("TvReceiverServer", "Error starting TV server", t)
            _stats.value = _stats.value.copy(
                isRunning = false,
                statusMessage = "Start failed: ${t.localizedMessage}"
            )
        }
    }

    private fun startWebSocketServer(port: Int) {
        wsJob = serverScope.launch {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(port))
                wsServerSocket = server

                while (isActive && !server.isClosed) {
                    try {
                        val clientSocket = server.accept()
                        clientSocket.tcpNoDelay = true
                        clientSocket.sendBufferSize = 64 * 1024
                        launch(Dispatchers.IO) {
                            handleWebSocketClient(clientSocket)
                        }
                    } catch (_: Throwable) {
                        if (!isActive) break
                    }
                }
            } catch (e: Throwable) {
                Log.w("TvReceiverServer", "WebSocket bind on $port failed", e)
            }
        }
    }

    private fun processIncomingGamepadState(state: GamepadState) {
        _latestGamepadState.value = state
        totalPacketsCounter++
        packetsThisSec++

        // Inject KeyEvents into external TV games (PPSSPP, Beach Buggy Racing, etc.)
        try {
            TvGamepadImeService.instance?.dispatchGamepadState(state)
        } catch (_: Throwable) {}

        try {
            TvInputInjector.dispatchGamepadState(state)
        } catch (_: Throwable) {}
    }

    private suspend fun handleWebSocketClient(socket: Socket) = withContext(Dispatchers.IO) {
        val clientAddress = socket.remoteSocketAddress.toString()
        val clientId = "WS-${socket.port}"
        addClient(ConnectedClient(id = clientId, address = clientAddress, protocol = "WebSocket"))

        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Perform HTTP WebSocket Upgrade Handshake
            if (!performWebSocketHandshake(input, output)) {
                socket.close()
                removeClient(clientId)
                return@withContext
            }

            // 2. Read WebSocket frames in a high-speed loop
            val headerBuf = ByteArray(2)
            while (isActive && !socket.isClosed) {
                val readHeader = readFully(input, headerBuf, 2)
                if (!readHeader) break

                val b0 = headerBuf[0].toInt() and 0xFF
                val b1 = headerBuf[1].toInt() and 0xFF

                val opcode = b0 and 0x0F
                val isMasked = (b1 and 0x80) != 0
                var payloadLength: Long = (b1 and 0x7F).toLong()

                if (opcode == 0x8) { // Close frame
                    break
                }

                if (payloadLength == 126L) {
                    val extLen = ByteArray(2)
                    readFully(input, extLen, 2)
                    payloadLength = (((extLen[0].toInt() and 0xFF) shl 8) or (extLen[1].toInt() and 0xFF)).toLong()
                } else if (payloadLength == 127L) {
                    val extLen = ByteArray(8)
                    readFully(input, extLen, 8)
                    payloadLength = ByteBuffer.wrap(extLen).long
                }

                val maskingKey = ByteArray(4)
                if (isMasked) {
                    readFully(input, maskingKey, 4)
                }

                val payload = ByteArray(payloadLength.toInt().coerceAtLeast(0))
                readFully(input, payload, payload.size)

                if (isMasked) {
                    for (i in payload.indices) {
                        payload[i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
                    }
                }

                // Opcode 0x2 is binary gamepad packet, 0x1 is text JSON
                if (opcode == 0x2) {
                    val state = GamepadState.fromByteArray(payload)
                    if (state != null) {
                        processIncomingGamepadState(state)
                    } else if (payload.isNotEmpty() && payload[0] == GamepadState.PING_PACKET && payload.size >= 11) {
                        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                        buffer.get() // magic
                        buffer.short // seq
                        val clientTime = buffer.long
                        val pong = GamepadState.createPongPacket(clientTime)
                        sendWebSocketBinary(output, pong)
                    }
                } else if (opcode == 0x9) { // Ping opcode: reply with Pong 0xA
                    sendWebSocketPong(output, payload)
                }
            }
        } catch (_: Throwable) {
        } finally {
            try { socket.close() } catch (_: Throwable) {}
            removeClient(clientId)
        }
    }

    private fun performWebSocketHandshake(input: InputStream, output: OutputStream): Boolean {
        try {
            val reader = input.bufferedReader(Charsets.UTF_8)
            var key: String? = null
            var line: String? = reader.readLine()

            while (!line.isNullOrEmpty()) {
                if (line.startsWith("Sec-WebSocket-Key:", ignoreCase = true)) {
                    key = line.substringAfter(":").trim()
                }
                line = reader.readLine()
            }

            if (key.isNullOrEmpty()) return false

            val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
            val digest = MessageDigest.getInstance("SHA-1").digest((key + magic).toByteArray())
            val acceptKey = Base64.encodeToString(digest, Base64.NO_WRAP)

            val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $acceptKey\r\n" +
                    "\r\n"
            output.write(response.toByteArray(Charsets.UTF_8))
            output.flush()
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    private fun sendWebSocketBinary(output: OutputStream, data: ByteArray) {
        try {
            synchronized(output) {
                val len = data.size
                if (len <= 125) {
                    output.write(byteArrayOf(0x82.toByte(), len.toByte()))
                } else {
                    output.write(byteArrayOf(0x82.toByte(), 126.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte()))
                }
                output.write(data)
                output.flush()
            }
        } catch (_: Throwable) {}
    }

    private fun sendWebSocketPong(output: OutputStream, payload: ByteArray) {
        try {
            synchronized(output) {
                output.write(byteArrayOf(0x8A.toByte(), payload.size.toByte()))
                output.write(payload)
                output.flush()
            }
        } catch (_: Throwable) {}
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Boolean {
        var totalRead = 0
        while (totalRead < length) {
            val bytesRead = input.read(buffer, totalRead, length - totalRead)
            if (bytesRead < 0) return false
            totalRead += bytesRead
        }
        return true
    }

    private fun startUdpServer(port: Int) {
        udpJob = serverScope.launch {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                try {
                    socket.trafficClass = 0x10 // Low-latency
                } catch (_: Throwable) {}
                udpSocket = socket

                val buffer = ByteArray(256)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive && !socket.isClosed) {
                    try {
                        socket.receive(packet)
                        val data = packet.data
                        val length = packet.length

                        if (length >= 21 && data[0] == GamepadState.MAGIC_PACKET) {
                            val state = GamepadState.fromByteArray(data.copyOf(length))
                            if (state != null) {
                                processIncomingGamepadState(state)
                            }
                        } else if (length >= 11 && data[0] == GamepadState.PING_PACKET) {
                            val byteBuf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)
                            byteBuf.get() // magic
                            byteBuf.short // seq
                            val clientTime = byteBuf.long
                            val pong = GamepadState.createPongPacket(clientTime)
                            val pongPacket = DatagramPacket(pong, pong.size, packet.address, packet.port)
                            socket.send(pongPacket)
                        }
                    } catch (_: Throwable) {
                        if (!isActive) break
                    }
                }
            } catch (e: Throwable) {
                Log.w("TvReceiverServer", "UDP bind on $port failed", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothServer() {
        if (!hasBluetoothPermission()) return

        btJob = serverScope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@launch
                if (!adapter.isEnabled) return@launch

                val server = adapter.listenUsingRfcommWithServiceRecord(
                    "TvGamepadReceiver",
                    BluetoothControllerClient.BT_GAMEPAD_UUID
                )
                btServerSocket = server

                while (isActive) {
                    try {
                        val socket = server.accept()
                        launch(Dispatchers.IO) {
                            handleBluetoothClient(socket)
                        }
                    } catch (_: Throwable) {
                        if (!isActive) break
                    }
                }
            } catch (t: Throwable) {
                Log.w("TvReceiverServer", "Bluetooth server start failed", t)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleBluetoothClient(socket: BluetoothSocket) = withContext(Dispatchers.IO) {
        val clientId = try {
            "BT-${socket.remoteDevice?.address ?: "Client"}"
        } catch (_: Throwable) {
            "BT-Client"
        }
        val clientName = try {
            socket.remoteDevice?.name ?: "Bluetooth Gamepad"
        } catch (_: Throwable) {
            "Bluetooth Gamepad"
        }

        addClient(ConnectedClient(id = clientId, address = clientName, protocol = "Bluetooth"))

        try {
            val input = socket.inputStream
            val output = socket.outputStream
            val buffer = ByteArray(256)

            while (isActive && socket.isConnected) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break

                if (bytesRead >= 21 && buffer[0] == GamepadState.MAGIC_PACKET) {
                    val state = GamepadState.fromByteArray(buffer.copyOf(bytesRead))
                    if (state != null) {
                        processIncomingGamepadState(state)
                    }
                } else if (bytesRead >= 11 && buffer[0] == GamepadState.PING_PACKET) {
                    val byteBuf = ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)
                    byteBuf.get()
                    byteBuf.short
                    val clientTime = byteBuf.long
                    val pong = GamepadState.createPongPacket(clientTime)
                    synchronized(output) {
                        output.write(pong)
                        output.flush()
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            try { socket.close() } catch (_: Throwable) {}
            removeClient(clientId)
        }
    }

    private fun buildBeaconPayload(localIp: String, wsPort: Int, udpPort: Int): ByteArray {
        val json = JSONObject().apply {
            put("type", "tv_beacon")
            put("tv", Build.MODEL ?: "Android TV")
            put("ip", localIp)
            put("ws_port", wsPort)
            put("udp_port", udpPort)
        }.toString()
        return json.toByteArray(Charsets.UTF_8)
    }

    private fun startBeaconBroadcast(localIp: String, wsPort: Int, udpPort: Int) {
        beaconJob = serverScope.launch {
            var bSocket: DatagramSocket? = null
            try {
                bSocket = DatagramSocket().apply { broadcast = true }
                val payload = buildBeaconPayload(localIp, wsPort, udpPort)

                while (isActive) {
                    val broadcastTargets = getBroadcastAddresses()
                    for (dest in broadcastTargets) {
                        try {
                            val packet = DatagramPacket(payload, payload.size, dest, 8764)
                            bSocket.send(packet)
                        } catch (_: Throwable) {}
                    }
                    delay(1500)
                }
            } catch (_: Throwable) {
            } finally {
                bSocket?.close()
            }
        }
    }

    // Proactively responds to any mobile phone sending a discovery request ping
    private fun startBeaconListener(localIp: String, wsPort: Int, udpPort: Int) {
        beaconListenJob = serverScope.launch {
            var listenSocket: DatagramSocket? = null
            try {
                listenSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(8764))
                    broadcast = true
                }
                val buffer = ByteArray(512)
                val inPacket = DatagramPacket(buffer, buffer.size)
                val replyPayload = buildBeaconPayload(localIp, wsPort, udpPort)

                while (isActive && !listenSocket.isClosed) {
                    try {
                        listenSocket.receive(inPacket)
                        val text = String(inPacket.data, 0, inPacket.length, Charsets.UTF_8)
                        if (text.contains("tv_discover_query")) {
                            val replyPacket = DatagramPacket(
                                replyPayload,
                                replyPayload.size,
                                inPacket.address,
                                inPacket.port
                            )
                            listenSocket.send(replyPacket)
                        }
                    } catch (_: Throwable) {
                        if (!isActive) break
                    }
                }
            } catch (_: Throwable) {
            } finally {
                listenSocket?.close()
            }
        }
    }

    private fun startStatsMonitor() {
        statsJob = serverScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val rate = packetsThisSec
                packetsThisSec = 0
                _stats.value = _stats.value.copy(
                    totalPacketsReceived = totalPacketsCounter,
                    packetsPerSecond = rate,
                    connectedClientsCount = _connectedClients.value.size
                )
            }
        }
    }

    private fun addClient(client: ConnectedClient) {
        val list = _connectedClients.value.toMutableList()
        list.add(client)
        _connectedClients.value = list
        _stats.value = _stats.value.copy(connectedClientsCount = list.size)
    }

    private fun removeClient(id: String) {
        val list = _connectedClients.value.filter { it.id != id }
        _connectedClients.value = list
        _stats.value = _stats.value.copy(connectedClientsCount = list.size)
    }

    fun stopServer() {
        beaconJob?.cancel()
        beaconJob = null
        beaconListenJob?.cancel()
        beaconListenJob = null
        statsJob?.cancel()
        statsJob = null
        wsJob?.cancel()
        wsJob = null
        udpJob?.cancel()
        udpJob = null
        btJob?.cancel()
        btJob = null

        try { wsServerSocket?.close() } catch (_: Throwable) {}
        try { udpSocket?.close() } catch (_: Throwable) {}
        try { btServerSocket?.close() } catch (_: Throwable) {}
        try { multicastLock?.release() } catch (_: Throwable) {}

        wsServerSocket = null
        udpSocket = null
        btServerSocket = null
        multicastLock = null

        try {
            TvGamepadImeService.instance?.releaseAllKeys()
        } catch (_: Throwable) {}

        _connectedClients.value = emptyList()
        _stats.value = _stats.value.copy(
            isRunning = false,
            connectedClientsCount = 0,
            packetsPerSecond = 0,
            statusMessage = "Server stopped"
        )
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            val candidates = mutableListOf<Pair<String, String>>()

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        candidates.add(iface.name to host)
                    }
                }
            }

            // Priority: eth (wired TV) > wlan (Wi-Fi) > others
            val eth = candidates.firstOrNull { it.first.startsWith("eth", ignoreCase = true) }
            if (eth != null) return eth.second

            val wlan = candidates.firstOrNull { it.first.startsWith("wlan", ignoreCase = true) }
            if (wlan != null) return wlan.second

            val firstValid = candidates.firstOrNull { !it.second.startsWith("127.") }
            if (firstValid != null) return firstValid.second
        } catch (_: Throwable) {}
        return "127.0.0.1"
    }

    fun getBroadcastAddresses(): List<InetAddress> {
        val broadcastList = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val bcast = ifaceAddr.broadcast
                    if (bcast != null) {
                        broadcastList.add(bcast)
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            broadcastList.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Throwable) {}

        return broadcastList.distinct()
    }

    companion object {
        @Volatile
        private var INSTANCE: TvReceiverServer? = null

        fun getInstance(context: Context): TvReceiverServer {
            return INSTANCE ?: synchronized(this) {
                val inst = TvReceiverServer(context.applicationContext)
                INSTANCE = inst
                inst
            }
        }
    }
}
