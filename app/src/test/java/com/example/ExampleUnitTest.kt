package com.example

import com.example.protocol.GamepadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testGamepadStateBinarySerialization() {
        val original = GamepadState(
            buttons = GamepadState.BTN_A or GamepadState.BTN_R1 or GamepadState.BTN_DPAD_UP,
            leftStickX = 0.75f,
            leftStickY = -0.50f,
            rightStickX = -0.25f,
            rightStickY = 0.90f,
            l2Trigger = 0.80f,
            r2Trigger = 1.0f,
            sequenceNumber = 42
        )

        val bytes = original.toByteArray()
        assertEquals(21, bytes.size)
        assertEquals(GamepadState.MAGIC_PACKET, bytes[0])

        val deserialized = GamepadState.fromByteArray(bytes)
        assertNotNull(deserialized)
        deserialized!!

        assertEquals(original.buttons, deserialized.buttons)
        assertTrue(deserialized.isPressed(GamepadState.BTN_A))
        assertTrue(deserialized.isPressed(GamepadState.BTN_R1))
        assertTrue(deserialized.isPressed(GamepadState.BTN_DPAD_UP))
        assertTrue(!deserialized.isPressed(GamepadState.BTN_B))

        assertEquals(42.toShort(), deserialized.sequenceNumber)
        assertEquals(0.75f, deserialized.leftStickX, 0.02f)
        assertEquals(-0.50f, deserialized.leftStickY, 0.02f)
        assertEquals(-0.25f, deserialized.rightStickX, 0.02f)
        assertEquals(0.90f, deserialized.rightStickY, 0.02f)
        assertEquals(0.80f, deserialized.l2Trigger, 0.02f)
        assertEquals(1.0f, deserialized.r2Trigger, 0.02f)
    }

    @Test
    fun testPingPongPacketProtocol() {
        val pingBytes = GamepadState.createPingPacket(seq = 10, timestamp = 123456789L)
        assertEquals(11, pingBytes.size)
        assertEquals(GamepadState.PING_PACKET, pingBytes[0])

        val pongBytes = GamepadState.createPongPacket(originalTimestamp = 123456789L)
        assertEquals(17, pongBytes.size)
        assertEquals(GamepadState.PONG_PACKET, pongBytes[0])
    }
}
