package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

class TvDiscoveryClient(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs.asStateFlow()

    private var listenJob: Job? = null
    private var pingJob: Job? = null
    private var cleanupJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startDiscovery() {
        if (listenJob != null) {
            sendDiscoveryPing()
            return
        }

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("TvDiscoveryLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Throwable) {}

        listenJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                    broadcast = true
                }

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                // Immediately send a probe to trigger quick responses from TVs
                sendDiscoveryPing()

                while (isActive) {
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        if (json.optString("type") == "tv_beacon") {
                            val name = json.optString("tv", "Android TV")
                            val ip = packet.address?.hostAddress ?: json.optString("ip", "")
                            val wsPort = json.optInt("ws_port", 8765)
                            val udpPort = json.optInt("udp_port", 8766)

                            if (ip.isNotBlank() && !ip.startsWith("127.")) {
                                updateTv(DiscoveredTv(name = name, ip = ip, wsPort = wsPort, udpPort = udpPort))
                            }
                        }
                    } catch (e: Throwable) {
                        if (!isActive) break
                    }
                }
            } catch (e: Throwable) {
                // socket bind failed or network unavailable
            } finally {
                socket?.close()
            }
        }

        // Periodically ping network every 3 seconds to keep discovery active
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                sendDiscoveryPing()
            }
        }

        // Periodically remove TVs not seen in last 12 seconds
        cleanupJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(4000)
                val cutoff = System.currentTimeMillis() - 12_000
                _discoveredTvs.value = _discoveredTvs.value.filter { it.lastSeen > cutoff }
            }
        }
    }

    fun sendDiscoveryPing() {
        scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { broadcast = true }
                val query = JSONObject().apply {
                    put("type", "tv_discover_query")
                    put("client", "virtual_gamepad")
                }.toString().toByteArray(Charsets.UTF_8)

                val targets = getBroadcastAddresses()
                for (addr in targets) {
                    try {
                        val packet = DatagramPacket(query, query.size, addr, DISCOVERY_PORT)
                        socket.send(packet)
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
            } finally {
                socket?.close()
            }
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val bcast = ifaceAddr.broadcast
                    if (bcast != null) list.add(bcast)
                }
            }
        } catch (_: Throwable) {}
        try {
            list.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Throwable) {}
        return list.distinct()
    }

    fun getPhoneIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (_: Throwable) {}
        return "127.0.0.1"
    }

    private fun updateTv(tv: DiscoveredTv) {
        val current = _discoveredTvs.value.toMutableList()
        val index = current.indexOfFirst { it.ip == tv.ip }
        if (index >= 0) {
            current[index] = tv.copy(lastSeen = System.currentTimeMillis())
        } else {
            current.add(tv)
        }
        _discoveredTvs.value = current
    }

    fun stopDiscovery() {
        listenJob?.cancel()
        listenJob = null
        pingJob?.cancel()
        pingJob = null
        cleanupJob?.cancel()
        cleanupJob = null

        try {
            multicastLock?.release()
        } catch (_: Throwable) {}
        multicastLock = null
    }

    companion object {
        const val DISCOVERY_PORT = 8764
    }
}
