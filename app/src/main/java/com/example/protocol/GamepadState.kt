package com.example.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compact low-latency gamepad state data model and binary serialization protocol.
 * Packet size is only 21 bytes for sub-millisecond transmission over WebSocket, UDP, or Bluetooth.
 */
data class GamepadState(
    val buttons: Int = 0,
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val l2Trigger: Float = 0f,
    val r2Trigger: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val sequenceNumber: Short = 0
) {
    fun isPressed(mask: Int): Boolean = (buttons and mask) != 0

    fun withButtonPressed(mask: Int, pressed: Boolean): GamepadState {
        val newButtons = if (pressed) (buttons or mask) else (buttons and mask.inv())
        return copy(buttons = newButtons)
    }

    /**
     * Serializes to ultra-compact 21-byte binary buffer.
     */
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(21).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MAGIC_PACKET)
        buffer.putShort(sequenceNumber)
        buffer.putLong(timestamp)
        buffer.putInt(buttons)
        buffer.put((leftStickX.coerceIn(-1f, 1f) * 127).toInt().toByte())
        buffer.put((leftStickY.coerceIn(-1f, 1f) * 127).toInt().toByte())
        buffer.put((rightStickX.coerceIn(-1f, 1f) * 127).toInt().toByte())
        buffer.put((rightStickY.coerceIn(-1f, 1f) * 127).toInt().toByte())
        buffer.put((l2Trigger.coerceIn(0f, 1f) * 255).toInt().toByte())
        buffer.put((r2Trigger.coerceIn(0f, 1f) * 255).toInt().toByte())
        return buffer.array()
    }

    /**
     * Serializes to compact JSON string as alternative transport.
     */
    fun toJsonString(): String {
        return "{\"m\":71,\"s\":$sequenceNumber,\"t\":$timestamp,\"b\":$buttons,\"lx\":${"%.2f".format(leftStickX)},\"ly\":${"%.2f".format(leftStickY)},\"rx\":${"%.2f".format(rightStickX)},\"ry\":${"%.2f".format(rightStickY)},\"l2\":${"%.2f".format(l2Trigger)},\"r2\":${"%.2f".format(r2Trigger)}}"
    }

    companion object {
        const val MAGIC_PACKET: Byte = 0x47 // 'G' for Gamepad
        const val PING_PACKET: Byte = 0x50  // 'P' for Ping
        const val PONG_PACKET: Byte = 0x51  // 'Q' for Pong

        // Button Bitmasks
        const val BTN_A: Int = 1 shl 0
        const val BTN_B: Int = 1 shl 1
        const val BTN_X: Int = 1 shl 2
        const val BTN_Y: Int = 1 shl 3
        const val BTN_DPAD_UP: Int = 1 shl 4
        const val BTN_DPAD_DOWN: Int = 1 shl 5
        const val BTN_DPAD_LEFT: Int = 1 shl 6
        const val BTN_DPAD_RIGHT: Int = 1 shl 7
        const val BTN_L1: Int = 1 shl 8
        const val BTN_R1: Int = 1 shl 9
        const val BTN_L2: Int = 1 shl 10
        const val BTN_R2: Int = 1 shl 11
        const val BTN_L3: Int = 1 shl 12
        const val BTN_R3: Int = 1 shl 13
        const val BTN_SELECT: Int = 1 shl 14
        const val BTN_START: Int = 1 shl 15
        const val BTN_HOME: Int = 1 shl 16
        const val BTN_TURBO: Int = 1 shl 17

        /**
         * Deserializes from 21-byte binary buffer.
         */
        fun fromByteArray(data: ByteArray): GamepadState? {
            if (data.size < 21 || data[0] != MAGIC_PACKET) return null
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buffer.get() // consume magic
            val seq = buffer.short
            val timestamp = buffer.long
            val buttons = buffer.int
            val lx = buffer.get() / 127f
            val ly = buffer.get() / 127f
            val rx = buffer.get() / 127f
            val ry = buffer.get() / 127f
            val l2 = (buffer.get().toInt() and 0xFF) / 255f
            val r2 = (buffer.get().toInt() and 0xFF) / 255f

            return GamepadState(
                buttons = buttons,
                leftStickX = lx.coerceIn(-1f, 1f),
                leftStickY = ly.coerceIn(-1f, 1f),
                rightStickX = rx.coerceIn(-1f, 1f),
                rightStickY = ry.coerceIn(-1f, 1f),
                l2Trigger = l2.coerceIn(0f, 1f),
                r2Trigger = r2.coerceIn(0f, 1f),
                timestamp = timestamp,
                sequenceNumber = seq
            )
        }

        fun createPingPacket(seq: Short, timestamp: Long = System.currentTimeMillis()): ByteArray {
            val buffer = ByteBuffer.allocate(11).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(PING_PACKET)
            buffer.putShort(seq)
            buffer.putLong(timestamp)
            return buffer.array()
        }

        fun createPongPacket(originalTimestamp: Long): ByteArray {
            val buffer = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(PONG_PACKET)
            buffer.putLong(originalTimestamp)
            buffer.putLong(System.currentTimeMillis())
            return buffer.array()
        }
    }
}
