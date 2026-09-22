package com.example.ui.controller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ControllerProfileEntity
import com.example.data.TvServerEntity
import com.example.network.BluetoothControllerClient
import com.example.network.ConnectionState
import com.example.network.DiscoveredTv
import com.example.network.IControllerClient
import com.example.network.LatencyStats
import com.example.network.ProtocolType
import com.example.network.TvDiscoveryClient
import com.example.network.UdpControllerClient
import com.example.network.WebSocketControllerClient
import com.example.protocol.GamepadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ControllerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val tvServerDao = db.tvServerDao()
    private val profileDao = db.controllerProfileDao()

    private val discoveryClient = TvDiscoveryClient(application, viewModelScope)
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = discoveryClient.discoveredTvs

    val savedServers: StateFlow<List<TvServerEntity>> = tvServerDao.getAllServers()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _profile = MutableStateFlow(ControllerProfileEntity())
    val profile: StateFlow<ControllerProfileEntity> = _profile.asStateFlow()

    private var activeClient: IControllerClient? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _latencyStats = MutableStateFlow(LatencyStats())
    val latencyStats: StateFlow<LatencyStats> = _latencyStats.asStateFlow()

    private val _gamepadState = MutableStateFlow(GamepadState())
    val gamepadState: StateFlow<GamepadState> = _gamepadState.asStateFlow()

    private var currentProtocol = ProtocolType.WEBSOCKET
    private var sequenceNumber: Short = 0

    init {
        discoveryClient.startDiscovery()

        viewModelScope.launch {
            profileDao.getProfile().collect { saved ->
                if (saved != null) {
                    _profile.value = saved
                } else {
                    profileDao.saveProfile(ControllerProfileEntity())
                }
            }
        }
    }

    fun connect(target: String, port: Int, protocol: ProtocolType, saveToHistory: Boolean = true) {
        currentProtocol = protocol
        activeClient?.disconnect()

        val newClient: IControllerClient = when (protocol) {
            ProtocolType.WEBSOCKET -> WebSocketControllerClient(viewModelScope)
            ProtocolType.UDP -> UdpControllerClient(viewModelScope)
            ProtocolType.BLUETOOTH -> BluetoothControllerClient(getApplication(), viewModelScope)
        }
        activeClient = newClient

        viewModelScope.launch {
            newClient.connectionState.collect { state ->
                _connectionState.value = state
            }
        }
        viewModelScope.launch {
            newClient.latencyStats.collect { stats ->
                _latencyStats.value = stats
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val success = newClient.connect(target, port)
            if (success && saveToHistory && protocol != ProtocolType.BLUETOOTH) {
                tvServerDao.insertServer(
                    TvServerEntity(
                        name = "TV @ $target",
                        ipAddress = target,
                        port = port,
                        protocol = protocol.name,
                        lastConnected = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun disconnect() {
        activeClient?.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun setButtonPressed(mask: Int, pressed: Boolean) {
        val current = _gamepadState.value
        val updated = current.withButtonPressed(mask, pressed).copy(
            sequenceNumber = sequenceNumber++,
            timestamp = System.currentTimeMillis()
        )
        _gamepadState.value = updated
        activeClient?.sendState(updated)
    }

    fun setLeftStick(x: Float, y: Float) {
        val current = _gamepadState.value
        val updated = current.copy(
            leftStickX = x,
            leftStickY = y,
            sequenceNumber = sequenceNumber++,
            timestamp = System.currentTimeMillis()
        )
        _gamepadState.value = updated
        activeClient?.sendState(updated)
    }

    fun setRightStick(x: Float, y: Float) {
        val current = _gamepadState.value
        val updated = current.copy(
            rightStickX = x,
            rightStickY = y,
            sequenceNumber = sequenceNumber++,
            timestamp = System.currentTimeMillis()
        )
        _gamepadState.value = updated
        activeClient?.sendState(updated)
    }

    fun setL2Trigger(value: Float) {
        val current = _gamepadState.value
        val updated = current.copy(
            l2Trigger = value,
            sequenceNumber = sequenceNumber++,
            timestamp = System.currentTimeMillis()
        )
        _gamepadState.value = updated
        activeClient?.sendState(updated)
    }

    fun setR2Trigger(value: Float) {
        val current = _gamepadState.value
        val updated = current.copy(
            r2Trigger = value,
            sequenceNumber = sequenceNumber++,
            timestamp = System.currentTimeMillis()
        )
        _gamepadState.value = updated
        activeClient?.sendState(updated)
    }

    fun saveProfile(profile: ControllerProfileEntity) {
        _profile.value = profile
        viewModelScope.launch(Dispatchers.IO) {
            profileDao.saveProfile(profile)
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryClient.stopDiscovery()
        activeClient?.disconnect()
    }
}
