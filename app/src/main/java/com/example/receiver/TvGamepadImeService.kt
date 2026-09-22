package com.example.receiver

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.example.protocol.GamepadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GamepadKeyMapping {
    UNIVERSAL_GAMES, // Best for Beach Buggy Racing, PPSSPP, & TV Arcade: A/R2=Gas (DPAD_UP + Button_A), B/L2=Brake (DPAD_DOWN + Button_B), X=Powerup (Space + Button_X)
    GAMEPAD_BUTTONS, // Standard Android Button A, B, X, Y, DPAD, Triggers
    KEYBOARD_KEYS    // PC-style Enter, Space, Arrows, Esc
}

/**
 * Android TV Virtual Gamepad InputMethodService.
 * When enabled in Android TV Settings -> Device Preferences -> Keyboard,
 * this IME injects real hardware KeyEvents into whatever game is active (PPSSPP, Beach Buggy Racing, etc.).
 */
class TvGamepadImeService : InputMethodService() {

    private var previousState = GamepadState()
    private val pressedKeyCodes = mutableSetOf<Int>()

    // Analog stick direction state tracking
    private var stickDpadLeftPressed = false
    private var stickDpadRightPressed = false
    private var stickDpadUpPressed = false
    private var stickDpadDownPressed = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isImeActive.value = true
        Log.i("TvGamepadIme", "TvGamepadImeService created and active")
    }

    override fun onDestroy() {
        releaseAllKeys()
        if (instance == this) {
            instance = null
            _isImeActive.value = false
        }
        super.onDestroy()
    }

    override fun onCreateInputView(): View? {
        // Return an empty or minimal view so it doesn't cover game screens
        return View(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // Never go full-screen so games remain visible
        return false
    }

    fun dispatchGamepadState(state: GamepadState) {
        val mapping = currentMapping

        when (mapping) {
            GamepadKeyMapping.UNIVERSAL_GAMES -> {
                // Button A / Accelerate: KEYCODE_DPAD_UP (Beach Buggy Racing Gas) + KEYCODE_BUTTON_A
                val aPressed = state.btnA || state.btnR2 || state.r2Trigger > 0.4f
                val prevAPressed = previousState.btnA || previousState.btnR2 || previousState.r2Trigger > 0.4f
                checkMultiButton(aPressed, prevAPressed, listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BUTTON_A))

                // Button B / Brake: KEYCODE_DPAD_DOWN (Beach Buggy Racing Brake) + KEYCODE_BUTTON_B
                val bPressed = state.btnB || state.btnL2 || state.l2Trigger > 0.4f
                val prevBPressed = previousState.btnB || previousState.btnL2 || previousState.l2Trigger > 0.4f
                checkMultiButton(bPressed, prevBPressed, listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BUTTON_B))

                // Button X / Powerup: KEYCODE_SPACE (Beach Buggy Racing item) + KEYCODE_BUTTON_X
                checkMultiButton(state.btnX, previousState.btnX, listOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_X))

                // Button Y: KEYCODE_SHIFT_LEFT + KEYCODE_BUTTON_Y
                checkMultiButton(state.btnY, previousState.btnY, listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_BUTTON_Y))

                // D-Pad
                checkButton(state.dpadUp, previousState.dpadUp, KeyEvent.KEYCODE_DPAD_UP)
                checkButton(state.dpadDown, previousState.dpadDown, KeyEvent.KEYCODE_DPAD_DOWN)
                checkButton(state.dpadLeft, previousState.dpadLeft, KeyEvent.KEYCODE_DPAD_LEFT)
                checkButton(state.dpadRight, previousState.dpadRight, KeyEvent.KEYCODE_DPAD_RIGHT)

                // Bumpers
                checkButton(state.btnL1, previousState.btnL1, KeyEvent.KEYCODE_BUTTON_L1)
                checkButton(state.btnR1, previousState.btnR1, KeyEvent.KEYCODE_BUTTON_R1)
            }
            GamepadKeyMapping.GAMEPAD_BUTTONS -> {
                // Button A
                checkButton(state.btnA, previousState.btnA, KeyEvent.KEYCODE_BUTTON_A)

                // Button B
                checkButton(state.btnB, previousState.btnB, KeyEvent.KEYCODE_BUTTON_B)

                // Button X
                checkButton(state.btnX, previousState.btnX, KeyEvent.KEYCODE_BUTTON_X)

                // Button Y
                checkButton(state.btnY, previousState.btnY, KeyEvent.KEYCODE_BUTTON_Y)

                // D-Pad
                checkButton(state.dpadUp, previousState.dpadUp, KeyEvent.KEYCODE_DPAD_UP)
                checkButton(state.dpadDown, previousState.dpadDown, KeyEvent.KEYCODE_DPAD_DOWN)
                checkButton(state.dpadLeft, previousState.dpadLeft, KeyEvent.KEYCODE_DPAD_LEFT)
                checkButton(state.dpadRight, previousState.dpadRight, KeyEvent.KEYCODE_DPAD_RIGHT)

                // Bumpers & Triggers
                checkButton(state.btnL1, previousState.btnL1, KeyEvent.KEYCODE_BUTTON_L1)
                checkButton(state.btnR1, previousState.btnR1, KeyEvent.KEYCODE_BUTTON_R1)
                checkButton(state.btnL2 || state.l2Trigger > 0.4f, previousState.btnL2 || previousState.l2Trigger > 0.4f, KeyEvent.KEYCODE_BUTTON_L2)
                checkButton(state.btnR2 || state.r2Trigger > 0.4f, previousState.btnR2 || previousState.r2Trigger > 0.4f, KeyEvent.KEYCODE_BUTTON_R2)
            }
            GamepadKeyMapping.KEYBOARD_KEYS -> {
                checkButton(state.btnA, previousState.btnA, KeyEvent.KEYCODE_ENTER)
                checkButton(state.btnB, previousState.btnB, KeyEvent.KEYCODE_BACK)
                checkButton(state.btnX, previousState.btnX, KeyEvent.KEYCODE_SPACE)
                checkButton(state.btnY, previousState.btnY, KeyEvent.KEYCODE_SHIFT_LEFT)
                checkButton(state.dpadUp, previousState.dpadUp, KeyEvent.KEYCODE_DPAD_UP)
                checkButton(state.dpadDown, previousState.dpadDown, KeyEvent.KEYCODE_DPAD_DOWN)
                checkButton(state.dpadLeft, previousState.dpadLeft, KeyEvent.KEYCODE_DPAD_LEFT)
                checkButton(state.dpadRight, previousState.dpadRight, KeyEvent.KEYCODE_DPAD_RIGHT)
                checkButton(state.btnL1, previousState.btnL1, KeyEvent.KEYCODE_MINUS)
                checkButton(state.btnR1, previousState.btnR1, KeyEvent.KEYCODE_EQUALS)
                checkButton(state.btnL2 || state.l2Trigger > 0.4f, previousState.btnL2 || previousState.l2Trigger > 0.4f, KeyEvent.KEYCODE_LEFT_BRACKET)
                checkButton(state.btnR2 || state.r2Trigger > 0.4f, previousState.btnR2 || previousState.r2Trigger > 0.4f, KeyEvent.KEYCODE_RIGHT_BRACKET)
            }
        }

        // Start & Select
        checkButton(state.btnStart, previousState.btnStart, KeyEvent.KEYCODE_BUTTON_START)
        checkButton(state.btnSelect, previousState.btnSelect, KeyEvent.KEYCODE_BUTTON_SELECT)
        checkButton(state.btnL3, previousState.btnL3, KeyEvent.KEYCODE_BUTTON_THUMBL)
        checkButton(state.btnR3, previousState.btnR3, KeyEvent.KEYCODE_BUTTON_THUMBR)
        checkButton(state.btnHome, previousState.btnHome, KeyEvent.KEYCODE_HOME)

        // Analog Left Stick translated to directional keys for car steering / movement
        handleAnalogStick(state.leftStickX, state.leftStickY)

        previousState = state
    }

    private fun checkMultiButton(current: Boolean, previous: Boolean, keyCodes: List<Int>) {
        if (current && !previous) {
            keyCodes.forEach { sendKeyEventDown(it) }
        } else if (!current && previous) {
            keyCodes.forEach { sendKeyEventUp(it) }
        }
    }

    private fun handleAnalogStick(x: Float, y: Float) {
        val threshold = 0.40f
        val resetThreshold = 0.25f

        // Left / Right steering
        if (x < -threshold && !stickDpadLeftPressed) {
            stickDpadLeftPressed = true
            sendKeyEventDown(KeyEvent.KEYCODE_DPAD_LEFT)
        } else if (x > -resetThreshold && stickDpadLeftPressed) {
            stickDpadLeftPressed = false
            sendKeyEventUp(KeyEvent.KEYCODE_DPAD_LEFT)
        }

        if (x > threshold && !stickDpadRightPressed) {
            stickDpadRightPressed = true
            sendKeyEventDown(KeyEvent.KEYCODE_DPAD_RIGHT)
        } else if (x < resetThreshold && stickDpadRightPressed) {
            stickDpadRightPressed = false
            sendKeyEventUp(KeyEvent.KEYCODE_DPAD_RIGHT)
        }

        // Up / Down acceleration & braking
        if (y < -threshold && !stickDpadUpPressed) {
            stickDpadUpPressed = true
            sendKeyEventDown(KeyEvent.KEYCODE_DPAD_UP)
        } else if (y > -resetThreshold && stickDpadUpPressed) {
            stickDpadUpPressed = false
            sendKeyEventUp(KeyEvent.KEYCODE_DPAD_UP)
        }

        if (y > threshold && !stickDpadDownPressed) {
            stickDpadDownPressed = true
            sendKeyEventDown(KeyEvent.KEYCODE_DPAD_DOWN)
        } else if (y < resetThreshold && stickDpadDownPressed) {
            stickDpadDownPressed = false
            sendKeyEventUp(KeyEvent.KEYCODE_DPAD_DOWN)
        }
    }

    private fun checkButton(current: Boolean, previous: Boolean, keyCode: Int) {
        if (current && !previous) {
            sendKeyEventDown(keyCode)
        } else if (!current && previous) {
            sendKeyEventUp(keyCode)
        }
    }

    private fun sendKeyEventDown(keyCode: Int) {
        pressedKeyCodes.add(keyCode)
        val event = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        try {
            val ic = currentInputConnection
            if (ic != null) {
                ic.sendKeyEvent(event)
            } else {
                // Fallback to InputMethodService sendDownUpKeyEvents
                sendKeyChar(keyCode.toChar())
            }
        } catch (t: Throwable) {
            Log.w("TvGamepadIme", "Failed to send key down $keyCode: ${t.message}")
        }
    }

    private fun sendKeyEventUp(keyCode: Int) {
        pressedKeyCodes.remove(keyCode)
        val event = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        try {
            val ic = currentInputConnection
            ic?.sendKeyEvent(event)
        } catch (t: Throwable) {
            Log.w("TvGamepadIme", "Failed to send key up $keyCode: ${t.message}")
        }
    }

    fun releaseAllKeys() {
        pressedKeyCodes.toList().forEach { keyCode ->
            try {
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            } catch (_: Throwable) {}
        }
        pressedKeyCodes.clear()

        if (stickDpadLeftPressed) {
            try { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)) } catch (_: Throwable) {}
            stickDpadLeftPressed = false
        }
        if (stickDpadRightPressed) {
            try { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)) } catch (_: Throwable) {}
            stickDpadRightPressed = false
        }
        if (stickDpadUpPressed) {
            try { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP)) } catch (_: Throwable) {}
            stickDpadUpPressed = false
        }
        if (stickDpadDownPressed) {
            try { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)) } catch (_: Throwable) {}
            stickDpadDownPressed = false
        }
    }

    companion object {
        @Volatile
        var instance: TvGamepadImeService? = null

        private val _isImeActive = MutableStateFlow(false)
        val isImeActive: StateFlow<Boolean> = _isImeActive.asStateFlow()

        var currentMapping: GamepadKeyMapping = GamepadKeyMapping.UNIVERSAL_GAMES

        /**
         * Checks whether this IME is enabled in system settings.
         */
        fun isImeEnabled(context: Context): Boolean {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return false
            val enabledMethods = imm.enabledInputMethodList
            val myPackage = context.packageName
            return enabledMethods.any { it.packageName == myPackage }
        }

        /**
         * Checks whether this IME is currently the selected/active input method.
         */
        fun isImeSelected(context: Context): Boolean {
            val defaultIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            ) ?: return false
            return defaultIme.contains(context.packageName)
        }
    }
}
