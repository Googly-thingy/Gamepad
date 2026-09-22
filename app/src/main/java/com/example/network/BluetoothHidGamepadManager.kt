package com.example.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.protocol.GamepadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

sealed class HidConnectionState {
    object Idle : HidConnectionState()
    object Initializing : HidConnectionState()
    data class Registered(val message: String) : HidConnectionState()
    data class Connected(val deviceName: String, val deviceAddress: String) : HidConnectionState()
    data class Disconnected(val reason: String = "") : HidConnectionState()
    data class Error(val error: String) : HidConnectionState()
}

@RequiresApi(Build.VERSION_CODES.P)
class BluetoothHidGamepadManager(private val context: Context) {

    private val _hidState = MutableStateFlow<HidConnectionState>(HidConnectionState.Idle)
    val hidState: StateFlow<HidConnectionState> = _hidState.asStateFlow()

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var isRegistered = false
    private var pendingDeviceToConnect: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as? BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                connectedDevice = null
                isRegistered = false
                _hidState.value = HidConnectionState.Disconnected("HID Service disconnected")
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isRegistered = registered
            if (registered) {
                _hidState.value = HidConnectionState.Registered("HID Gamepad Ready")
                val pending = pendingDeviceToConnect
                if (pending != null) {
                    pendingDeviceToConnect = null
                    try {
                        Log.d("BluetoothHidGamepad", "Connecting to queued device ${pending.address}")
                        hidDevice?.connect(pending)
                    } catch (t: Throwable) {
                        Log.e("BluetoothHidGamepad", "Error connecting to queued device", t)
                    }
                }
            } else {
                _hidState.value = HidConnectionState.Disconnected("HID Application unregistered")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    val name = try { device?.name ?: "Android TV" } catch (_: Throwable) { "Android TV" }
                    val addr = try { device?.address ?: "" } catch (_: Throwable) { "" }
                    _hidState.value = HidConnectionState.Connected(name, addr)

                    // Immediately transmit initial neutral report so TV host handshake completes smoothly
                    executor.execute {
                        try {
                            Thread.sleep(80)
                            val report = buildHidReport(lastGamepadState)
                            hidDevice?.sendReport(device, REPORT_ID.toInt(), report)
                        } catch (_: Throwable) {}
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedDevice == device) {
                        connectedDevice = null
                    }
                    _hidState.value = HidConnectionState.Disconnected("Device disconnected")
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _hidState.value = HidConnectionState.Registered("Connecting to device...")
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && (id == REPORT_ID || id == 0.toByte())) {
                val report = buildHidReport(lastGamepadState)
                hidDevice?.replyReport(device, type, REPORT_ID, report)
            } else {
                hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
            val dev = device ?: connectedDevice
            if (dev != null) {
                val report = buildHidReport(lastGamepadState)
                hidDevice?.sendReport(dev, REPORT_ID.toInt(), report)
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            if (connectedDevice == device) {
                connectedDevice = null
                _hidState.value = HidConnectionState.Disconnected("Unplugged by TV")
            }
        }
    }

    @Volatile
    private var lastGamepadState = GamepadState()

    fun isSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!isSupported()) {
            _hidState.value = HidConnectionState.Error("Bluetooth HID requires Android 9.0 (API 28)+")
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            _hidState.value = HidConnectionState.Error("Bluetooth is disabled or not supported")
            return
        }

        _hidState.value = HidConnectionState.Initializing
        try {
            adapter.getProfileProxy(context.applicationContext, serviceListener, BluetoothProfile.HID_DEVICE)
        } catch (t: Throwable) {
            _hidState.value = HidConnectionState.Error("Failed to access Bluetooth HID: ${t.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val hid = hidDevice ?: return
        try {
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "Wireless Gamepad",
                "Bluetooth Gamepad Controller",
                "Google",
                BluetoothHidDevice.SUBCLASS2_GAMEPAD,
                HID_GAMEPAD_DESCRIPTOR
            )

            hid.registerApp(sdp, null, null, executor, hidCallback)
        } catch (t: Throwable) {
            Log.e("BluetoothHidGamepad", "Error registering HID app", t)
            _hidState.value = HidConnectionState.Error("Registration failed: ${t.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null || !isRegistered) {
            Log.d("BluetoothHidGamepad", "HID not registered yet, queueing connection to ${device.address}")
            pendingDeviceToConnect = device
            return
        }
        try {
            Log.d("BluetoothHidGamepad", "Connecting to ${device.address}")
            hid.connect(device)
        } catch (t: Throwable) {
            Log.e("BluetoothHidGamepad", "Error connecting to device", t)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val hid = hidDevice ?: return
        val dev = connectedDevice ?: return
        try {
            hid.disconnect(dev)
        } catch (_: Throwable) {}
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val hid = hidDevice
        if (hid != null) {
            try {
                hid.unregisterApp()
            } catch (_: Throwable) {}
            try {
                BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
            } catch (_: Throwable) {}
            hidDevice = null
        }
        connectedDevice = null
        _hidState.value = HidConnectionState.Idle
    }

    @SuppressLint("MissingPermission")
    fun sendGamepadState(state: GamepadState) {
        lastGamepadState = state
        val hid = hidDevice ?: return
        val device = connectedDevice ?: return

        val report = buildHidReport(state)
        try {
            hid.sendReport(device, REPORT_ID.toInt(), report)
        } catch (t: Throwable) {
            Log.w("BluetoothHidGamepad", "sendReport error", t)
        }
    }

    private fun buildHidReport(state: GamepadState): ByteArray {
        val report = ByteArray(7)

        // Byte 0: Buttons 1-8
        // Bit 0: Button A
        // Bit 1: Button B
        // Bit 2: Button X
        // Bit 3: Button Y
        // Bit 4: L1
        // Bit 5: R1
        // Bit 6: L2 (digital threshold)
        // Bit 7: R2 (digital threshold)
        var b0 = 0
        if (state.btnA) b0 = b0 or (1 shl 0)
        if (state.btnB) b0 = b0 or (1 shl 1)
        if (state.btnX) b0 = b0 or (1 shl 2)
        if (state.btnY) b0 = b0 or (1 shl 3)
        if (state.btnL1) b0 = b0 or (1 shl 4)
        if (state.btnR1) b0 = b0 or (1 shl 5)
        if (state.btnL2 || state.l2Trigger > 0.4f) b0 = b0 or (1 shl 6)
        if (state.btnR2 || state.r2Trigger > 0.4f) b0 = b0 or (1 shl 7)
        report[0] = b0.toByte()

        // Byte 1: Buttons 9-16
        // Bit 0: Select / Back
        // Bit 1: Start / Menu
        // Bit 2: L3 (thumbstick click)
        // Bit 3: R3 (thumbstick click)
        // Bit 4: Home / Guide
        var b1 = 0
        if (state.btnSelect) b1 = b1 or (1 shl 0)
        if (state.btnStart) b1 = b1 or (1 shl 1)
        if (state.btnL3) b1 = b1 or (1 shl 2)
        if (state.btnR3) b1 = b1 or (1 shl 3)
        if (state.btnHome) b1 = b1 or (1 shl 4)
        report[1] = b1.toByte()

        // Byte 2: Hat switch (D-pad) 4 bits (1-8, 0 for null)
        val dUp = state.dpadUp
        val dDown = state.dpadDown
        val dLeft = state.dpadLeft
        val dRight = state.dpadRight

        val hat: Int = when {
            dUp && dRight -> 2
            dDown && dRight -> 4
            dDown && dLeft -> 6
            dUp && dLeft -> 8
            dUp -> 1
            dRight -> 3
            dDown -> 5
            dLeft -> 7
            else -> 0
        }
        report[2] = hat.toByte()

        // Unsigned 0..255 axes with 128 as neutral center
        // Byte 3: Left Stick X
        report[3] = ((state.leftStickX.coerceIn(-1f, 1f) + 1f) * 127.5f).toInt().coerceIn(0, 255).toByte()

        // Byte 4: Left Stick Y
        report[4] = ((state.leftStickY.coerceIn(-1f, 1f) + 1f) * 127.5f).toInt().coerceIn(0, 255).toByte()

        // Byte 5: Right Stick X
        report[5] = ((state.rightStickX.coerceIn(-1f, 1f) + 1f) * 127.5f).toInt().coerceIn(0, 255).toByte()

        // Byte 6: Right Stick Y
        report[6] = ((state.rightStickY.coerceIn(-1f, 1f) + 1f) * 127.5f).toInt().coerceIn(0, 255).toByte()

        return report
    }

    companion object {
        const val REPORT_ID: Byte = 1

        val HID_GAMEPAD_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x05.toByte(), // USAGE (Gamepad)
            0xa1.toByte(), 0x01.toByte(), // COLLECTION (Application)
            0x85.toByte(), REPORT_ID,    //   REPORT_ID (1)

            // 16 Buttons (A, B, X, Y, L1, R1, L2, R2, Select, Start, L3, R3, Home, etc.)
            0x05.toByte(), 0x09.toByte(), //   USAGE_PAGE (Button)
            0x19.toByte(), 0x01.toByte(), //   USAGE_MINIMUM (Button 1)
            0x29.toByte(), 0x10.toByte(), //   USAGE_MAXIMUM (Button 16)
            0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
            0x25.toByte(), 0x01.toByte(), //   LOGICAL_MAXIMUM (1)
            0x75.toByte(), 0x01.toByte(), //   REPORT_SIZE (1)
            0x95.toByte(), 0x10.toByte(), //   REPORT_COUNT (16)
            0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)

            // Hat switch (D-Pad)
            0x05.toByte(), 0x01.toByte(), //   USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x39.toByte(), //   USAGE (Hat switch)
            0x15.toByte(), 0x01.toByte(), //   LOGICAL_MINIMUM (1)
            0x25.toByte(), 0x08.toByte(), //   LOGICAL_MAXIMUM (8)
            0x75.toByte(), 0x04.toByte(), //   REPORT_SIZE (4)
            0x95.toByte(), 0x01.toByte(), //   REPORT_COUNT (1)
            0x81.toByte(), 0x42.toByte(), //   INPUT (Data,Var,Abs,Null)
            // Padding 4 bits to round up to full byte
            0x75.toByte(), 0x04.toByte(), //   REPORT_SIZE (4)
            0x95.toByte(), 0x01.toByte(), //   REPORT_COUNT (1)
            0x81.toByte(), 0x03.toByte(), //   INPUT (Cnst,Var,Abs)

            // 4 Analog Axes: X, Y, Z, Rz (0 to 255, center 128)
            0x05.toByte(), 0x01.toByte(), //   USAGE_PAGE (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), //   USAGE (X) - Left Stick X
            0x09.toByte(), 0x31.toByte(), //   USAGE (Y) - Left Stick Y
            0x09.toByte(), 0x32.toByte(), //   USAGE (Z) - Right Stick X
            0x09.toByte(), 0x35.toByte(), //   USAGE (Rz) - Right Stick Y
            0x15.toByte(), 0x00.toByte(), //   LOGICAL_MINIMUM (0)
            0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), // LOGICAL_MAXIMUM (255)
            0x75.toByte(), 0x08.toByte(), //   REPORT_SIZE (8)
            0x95.toByte(), 0x04.toByte(), //   REPORT_COUNT (4)
            0x81.toByte(), 0x02.toByte(), //   INPUT (Data,Var,Abs)

            0xc0.toByte()                  // END_COLLECTION
        )
    }
}
