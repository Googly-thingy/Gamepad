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
import java.net.InetSocketAddress

class TvDiscoveryClient(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs.asStateFlow()

    private var listenJob: Job? = null
    private var cleanupJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startDiscovery() {
        if (listenJob != null) return

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("TvDiscoveryLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}

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

                while (isActive) {
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        if (json.optString("type") == "tv_beacon") {
                            val name = json.optString("tv", "Android TV")
                            val ip = packet.address.hostAddress ?: json.optString("ip", "")
                            val wsPort = json.optInt("ws_port", 8765)
                            val udpPort = json.optInt("udp_port", 8766)

                            if (ip.isNotEmpty()) {
                                updateTv(DiscoveredTv(name = name, ip = ip, wsPort = wsPort, udpPort = udpPort))
                            }
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                // socket bind failed or network unavailable
            } finally {
                socket?.close()
            }
        }

        // Periodically remove TVs not seen in last 10 seconds
        cleanupJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(3000)
                val cutoff = System.currentTimeMillis() - 10_000
                _discoveredTvs.value = _discoveredTvs.value.filter { it.lastSeen > cutoff }
            }
        }
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
        cleanupJob?.cancel()
        cleanupJob = null

        try {
            multicastLock?.release()
        } catch (_: Exception) {}
        multicastLock = null
    }

    companion object {
        const val DISCOVERY_PORT = 8764
    }
}
