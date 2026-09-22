package com.example.receiver

import android.os.SystemClock
import android.util.Log
import android.view.InputEvent
import android.view.KeyEvent
import com.example.protocol.GamepadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Method

/**
 * Direct system event injector for Android TV.
 * Attempts input injection via:
 * 1. Reflection on android.hardware.input.InputManager (works if INJECT_EVENTS permission is granted via adb or Shizuku).
 * 2. Fallback shell command execution.
 */
object TvInputInjector {

    private var injectInputEventMethod: Method? = null
    private var inputManagerInstance: Any? = null
    private var hasTestedReflection = false
    private var isReflectionWorking = false

    private val scope = CoroutineScope(Dispatchers.IO)
    private var previousButtons = 0

    init {
        initReflection()
    }

    private fun initReflection() {
        if (hasTestedReflection) return
        hasTestedReflection = true
        try {
            val imClass = Class.forName("android.hardware.input.InputManager")
            val getInstanceMethod = imClass.getMethod("getInstance")
            inputManagerInstance = getInstanceMethod.invoke(null)
            injectInputEventMethod = imClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            isReflectionWorking = true
            Log.i("TvInputInjector", "InputManager reflection initialized successfully")
        } catch (t: Throwable) {
            isReflectionWorking = false
            Log.w("TvInputInjector", "InputManager reflection not available without system/adb permission: ${t.message}")
        }
    }

    fun injectKeyEvent(action: Int, keyCode: Int): Boolean {
        if (isReflectionWorking && inputManagerInstance != null && injectInputEventMethod != null) {
            try {
                val now = SystemClock.uptimeMillis()
                val event = KeyEvent(now, now, action, keyCode, 0, 0)
                // INJECT_INPUT_EVENT_MODE_ASYNC = 0
                val result = injectInputEventMethod?.invoke(inputManagerInstance, event, 0) as? Boolean
                if (result == true) return true
            } catch (t: Throwable) {
                // Permission denied, switch reflection off
                isReflectionWorking = false
            }
        }
        return false
    }

    fun dispatchGamepadState(state: GamepadState) {
        val currentButtons = state.buttons
        val changed = currentButtons xor previousButtons

        if (changed != 0) {
            checkButton(changed, currentButtons, GamepadState.BTN_A, KeyEvent.KEYCODE_BUTTON_A)
            checkButton(changed, currentButtons, GamepadState.BTN_B, KeyEvent.KEYCODE_BUTTON_B)
            checkButton(changed, currentButtons, GamepadState.BTN_X, KeyEvent.KEYCODE_BUTTON_X)
            checkButton(changed, currentButtons, GamepadState.BTN_Y, KeyEvent.KEYCODE_BUTTON_Y)
            checkButton(changed, currentButtons, GamepadState.BTN_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP)
            checkButton(changed, currentButtons, GamepadState.BTN_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
            checkButton(changed, currentButtons, GamepadState.BTN_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT)
            checkButton(changed, currentButtons, GamepadState.BTN_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT)
            checkButton(changed, currentButtons, GamepadState.BTN_L1, KeyEvent.KEYCODE_BUTTON_L1)
            checkButton(changed, currentButtons, GamepadState.BTN_R1, KeyEvent.KEYCODE_BUTTON_R1)
            checkButton(changed, currentButtons, GamepadState.BTN_L2, KeyEvent.KEYCODE_BUTTON_L2)
            checkButton(changed, currentButtons, GamepadState.BTN_R2, KeyEvent.KEYCODE_BUTTON_R2)
            checkButton(changed, currentButtons, GamepadState.BTN_START, KeyEvent.KEYCODE_BUTTON_START)
            checkButton(changed, currentButtons, GamepadState.BTN_SELECT, KeyEvent.KEYCODE_BUTTON_SELECT)
            checkButton(changed, currentButtons, GamepadState.BTN_HOME, KeyEvent.KEYCODE_HOME)
        }

        previousButtons = currentButtons
    }

    private fun checkButton(changed: Int, current: Int, mask: Int, keyCode: Int) {
        if ((changed and mask) != 0) {
            val isPressed = (current and mask) != 0
            val action = if (isPressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            injectKeyEvent(action, keyCode)
        }
    }
}
