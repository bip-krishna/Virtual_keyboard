package com.visionboard.domain.usecase.tracking

import com.visionboard.common.models.TouchEvent
import javax.inject.Inject

data class HidKeyEventResult(
    val keycode: Int,
    val modifiers: List<Int>,
    val isDown: Boolean,
)

class GenerateKeyEventUseCase @Inject constructor() {

    private val modifierKeycodes = setOf(
        KEYCODE_LEFT_SHIFT,
        KEYCODE_RIGHT_SHIFT,
        KEYCODE_LEFT_CTRL,
        KEYCODE_RIGHT_CTRL,
        KEYCODE_LEFT_ALT,
        KEYCODE_RIGHT_ALT,
        KEYCODE_LEFT_META,
        KEYCODE_RIGHT_META,
    )

    operator fun invoke(touch: TouchEvent): HidKeyEventResult {
        val keycode = resolveKeycode(touch.keyLabel, touch.keycode)
        val modifiers = resolveModifiers(touch.keyLabel)
        val isDown = true

        return HidKeyEventResult(
            keycode = keycode,
            modifiers = modifiers,
            isDown = isDown,
        )
    }

    private fun resolveKeycode(label: String, fallbackKeycode: Int): Int {
        if (fallbackKeycode != 0) return fallbackKeycode
        return KEYCODE_MAP[label.lowercase()] ?: 0
    }

    private fun resolveModifiers(label: String): List<Int> {
        val modifiers = mutableListOf<Int>()
        if (label.length == 1 && label[0].isUpperCase()) {
            modifiers.add(KEYCODE_LEFT_SHIFT)
        }
        val specialModifier = MODIFIER_MAP[label.lowercase()]
        if (specialModifier != null) {
            modifiers.add(specialModifier)
        }
        return modifiers
    }

    companion object {
        const val KEYCODE_LEFT_SHIFT = 0xE1
        const val KEYCODE_RIGHT_SHIFT = 0xE5
        const val KEYCODE_LEFT_CTRL = 0xE0
        const val KEYCODE_RIGHT_CTRL = 0xE4
        const val KEYCODE_LEFT_ALT = 0xE2
        const val KEYCODE_RIGHT_ALT = 0xE6
        const val KEYCODE_LEFT_META = 0xE3
        const val KEYCODE_RIGHT_META = 0xE7
        const val KEYCODE_DELETE = 0x4A
        const val KEYCODE_TAB = 0x2B
        const val KEYCODE_ENTER = 0x28
        const val KEYCODE_SPACE = 0x2C
        const val KEYCODE_ESC = 0x29
        const val KEYCODE_BACKSPACE = 0x2A
        const val KEYCODE_CAPS_LOCK = 0x39
        const val KEYCODE_UP = 0x52
        const val KEYCODE_DOWN = 0x51
        const val KEYCODE_LEFT = 0x50
        const val KEYCODE_RIGHT = 0x4F
        const val KEYCODE_HOME = 0x4A
        const val KEYCODE_END = 0x4D
        const val KEYCODE_PAGE_UP = 0x4B
        const val KEYCODE_PAGE_DOWN = 0x4E

        private val KEYCODE_MAP = mapOf(
            "a" to 0x04, "b" to 0x05, "c" to 0x06, "d" to 0x07,
            "e" to 0x08, "f" to 0x09, "g" to 0x0A, "h" to 0x0B,
            "i" to 0x0C, "j" to 0x0D, "k" to 0x0E, "l" to 0x0F,
            "m" to 0x10, "n" to 0x11, "o" to 0x12, "p" to 0x13,
            "q" to 0x14, "r" to 0x15, "s" to 0x16, "t" to 0x17,
            "u" to 0x18, "v" to 0x19, "w" to 0x1A, "x" to 0x1B,
            "y" to 0x1C, "z" to 0x1D,
            "1" to 0x1E, "2" to 0x1F, "3" to 0x20, "4" to 0x21,
            "5" to 0x22, "6" to 0x23, "7" to 0x24, "8" to 0x25,
            "9" to 0x26, "0" to 0x27,
            "enter" to KEYCODE_ENTER,
            "escape" to KEYCODE_ESC,
            "backspace" to KEYCODE_BACKSPACE,
            "tab" to KEYCODE_TAB,
            "space" to KEYCODE_SPACE,
            "capslock" to KEYCODE_CAPS_LOCK,
            "delete" to KEYCODE_DELETE,
            "up" to KEYCODE_UP,
            "down" to KEYCODE_DOWN,
            "left" to KEYCODE_LEFT,
            "right" to KEYCODE_RIGHT,
            "home" to KEYCODE_HOME,
            "end" to KEYCODE_END,
            "pageup" to KEYCODE_PAGE_UP,
            "pagedown" to KEYCODE_PAGE_DOWN,
            "-" to 0x2D, "=" to 0x2E, "[" to 0x2F, "]" to 0x30,
            "\\" to 0x31, ";" to 0x33, "'" to 0x34, "`" to 0x35,
            "," to 0x36, "." to 0x37, "/" to 0x38,
        )

        private val MODIFIER_MAP = mapOf(
            "shift" to KEYCODE_LEFT_SHIFT,
            "ctrl" to KEYCODE_LEFT_CTRL,
            "alt" to KEYCODE_LEFT_ALT,
            "meta" to KEYCODE_LEFT_META,
        )
    }
}
