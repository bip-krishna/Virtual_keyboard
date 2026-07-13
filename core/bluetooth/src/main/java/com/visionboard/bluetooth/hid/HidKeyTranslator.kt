package com.visionboard.bluetooth.hid

import android.view.KeyEvent
import javax.inject.Inject
import javax.inject.Singleton

enum class Modifier(val bitPosition: Int) {
    SHIFT(1),   // bit 1 — Left Shift
    CTRL(0),    // bit 0 — Left Ctrl
    ALT(2),     // bit 2 — Left Alt
    GUI(3),     // bit 3 — Left GUI
}

data class HidKeyEvent(
    val keycode: Int,
    val hidUsage: Int,
    val modifiers: Set<Modifier>,
    val isDown: Boolean,
)

@Singleton
class HidKeyTranslator @Inject constructor() {

    private val charToKeycode: Map<Char, Int> = mapOf(
        'a' to KeyEvent.KEYCODE_A, 'b' to KeyEvent.KEYCODE_B, 'c' to KeyEvent.KEYCODE_C,
        'd' to KeyEvent.KEYCODE_D, 'e' to KeyEvent.KEYCODE_E, 'f' to KeyEvent.KEYCODE_F,
        'g' to KeyEvent.KEYCODE_G, 'h' to KeyEvent.KEYCODE_H, 'i' to KeyEvent.KEYCODE_I,
        'j' to KeyEvent.KEYCODE_J, 'k' to KeyEvent.KEYCODE_K, 'l' to KeyEvent.KEYCODE_L,
        'm' to KeyEvent.KEYCODE_M, 'n' to KeyEvent.KEYCODE_N, 'o' to KeyEvent.KEYCODE_O,
        'p' to KeyEvent.KEYCODE_P, 'q' to KeyEvent.KEYCODE_Q, 'r' to KeyEvent.KEYCODE_R,
        's' to KeyEvent.KEYCODE_S, 't' to KeyEvent.KEYCODE_T, 'u' to KeyEvent.KEYCODE_U,
        'v' to KeyEvent.KEYCODE_V, 'w' to KeyEvent.KEYCODE_W, 'x' to KeyEvent.KEYCODE_X,
        'y' to KeyEvent.KEYCODE_Y, 'z' to KeyEvent.KEYCODE_Z,
        '0' to KeyEvent.KEYCODE_0, '1' to KeyEvent.KEYCODE_1, '2' to KeyEvent.KEYCODE_2,
        '3' to KeyEvent.KEYCODE_3, '4' to KeyEvent.KEYCODE_4, '5' to KeyEvent.KEYCODE_5,
        '6' to KeyEvent.KEYCODE_6, '7' to KeyEvent.KEYCODE_7, '8' to KeyEvent.KEYCODE_8,
        '9' to KeyEvent.KEYCODE_9,
        ' ' to KeyEvent.KEYCODE_SPACE, '\t' to KeyEvent.KEYCODE_TAB,
        '\n' to KeyEvent.KEYCODE_ENTER, '\r' to KeyEvent.KEYCODE_ENTER,
        '.' to KeyEvent.KEYCODE_PERIOD, ',' to KeyEvent.KEYCODE_COMMA,
        ';' to KeyEvent.KEYCODE_SEMICOLON, ':' to KeyEvent.KEYCODE_COLON,
        '!' to KeyEvent.KEYCODE_EXCLAMATION, '?' to KeyEvent.KEYCODE_QUESTION_MARK,
        '\'' to KeyEvent.KEYCODE_APOSTROPHE, '"' to KeyEvent.KEYCODE_APOSTROPHE,
        '-' to KeyEvent.KEYCODE_MINUS, '_' to KeyEvent.KEYCODE_MINUS,
        '+' to KeyEvent.KEYCODE_EQUALS, '=' to KeyEvent.KEYCODE_EQUALS,
        '/' to KeyEvent.KEYCODE_SLASH, '\\' to KeyEvent.KEYCODE_BACKSLASH,
        '(' to KeyEvent.KEYCODE_9, ')' to KeyEvent.KEYCODE_0,
        '[' to KeyEvent.KEYCODE_LEFT_BRACKET, ']' to KeyEvent.KEYCODE_RIGHT_BRACKET,
        '{' to KeyEvent.KEYCODE_LEFT_BRACKET, '}' to KeyEvent.KEYCODE_RIGHT_BRACKET,
        '@' to KeyEvent.KEYCODE_AT, '#' to KeyEvent.KEYCODE_POUND,
        '$' to KeyEvent.KEYCODE_DOLLAR, '%' to KeyEvent.KEYCODE_PERCENT,
        '^' to KeyEvent.KEYCODE_STAR, '&' to KeyEvent.KEYCODE_AMPERSAND,
        '*' to KeyEvent.KEYCODE_STAR, '~' to KeyEvent.KEYCODE_GRAVE,
        '`' to KeyEvent.KEYCODE_GRAVE, '<' to KeyEvent.KEYCODE_COMMA,
        '>' to KeyEvent.KEYCODE_PERIOD, '|' to KeyEvent.KEYCODE_BACKSLASH,
    )

    private val shiftedChars: Set<Char> = setOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        '~', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '_', '+',
        '{', '}', '|', ':', '"', '<', '>', '?',
    )

    private val ctrlCombos: Map<Char, Int> = mapOf(
        'a' to KeyEvent.KEYCODE_A, 'c' to KeyEvent.KEYCODE_C,
        'v' to KeyEvent.KEYCODE_V, 'x' to KeyEvent.KEYCODE_X,
        'z' to KeyEvent.KEYCODE_Z, 'y' to KeyEvent.KEYCODE_Y,
    )

    private val altCombos: Map<Char, Int> = mapOf(
        '\t' to KeyEvent.KEYCODE_TAB,
    )

    private val keycodeToHidUsage: Map<Int, Int> = buildMap {
        put(KeyEvent.KEYCODE_A, HID_USAGE_A)
        put(KeyEvent.KEYCODE_B, HID_USAGE_B)
        put(KeyEvent.KEYCODE_C, HID_USAGE_C)
        put(KeyEvent.KEYCODE_D, HID_USAGE_D)
        put(KeyEvent.KEYCODE_E, HID_USAGE_E)
        put(KeyEvent.KEYCODE_F, HID_USAGE_F)
        put(KeyEvent.KEYCODE_G, HID_USAGE_G)
        put(KeyEvent.KEYCODE_H, HID_USAGE_H)
        put(KeyEvent.KEYCODE_I, HID_USAGE_I)
        put(KeyEvent.KEYCODE_J, HID_USAGE_J)
        put(KeyEvent.KEYCODE_K, HID_USAGE_K)
        put(KeyEvent.KEYCODE_L, HID_USAGE_L)
        put(KeyEvent.KEYCODE_M, HID_USAGE_M)
        put(KeyEvent.KEYCODE_N, HID_USAGE_N)
        put(KeyEvent.KEYCODE_O, HID_USAGE_O)
        put(KeyEvent.KEYCODE_P, HID_USAGE_P)
        put(KeyEvent.KEYCODE_Q, HID_USAGE_Q)
        put(KeyEvent.KEYCODE_R, HID_USAGE_R)
        put(KeyEvent.KEYCODE_S, HID_USAGE_S)
        put(KeyEvent.KEYCODE_T, HID_USAGE_T)
        put(KeyEvent.KEYCODE_U, HID_USAGE_U)
        put(KeyEvent.KEYCODE_V, HID_USAGE_V)
        put(KeyEvent.KEYCODE_W, HID_USAGE_W)
        put(KeyEvent.KEYCODE_X, HID_USAGE_X)
        put(KeyEvent.KEYCODE_Y, HID_USAGE_Y)
        put(KeyEvent.KEYCODE_Z, HID_USAGE_Z)
        put(KeyEvent.KEYCODE_1, HID_USAGE_1)
        put(KeyEvent.KEYCODE_2, HID_USAGE_2)
        put(KeyEvent.KEYCODE_3, HID_USAGE_3)
        put(KeyEvent.KEYCODE_4, HID_USAGE_4)
        put(KeyEvent.KEYCODE_5, HID_USAGE_5)
        put(KeyEvent.KEYCODE_6, HID_USAGE_6)
        put(KeyEvent.KEYCODE_7, HID_USAGE_7)
        put(KeyEvent.KEYCODE_8, HID_USAGE_8)
        put(KeyEvent.KEYCODE_9, HID_USAGE_9)
        put(KeyEvent.KEYCODE_0, HID_USAGE_0)
        put(KeyEvent.KEYCODE_ENTER, HID_USAGE_ENTER)
        put(KeyEvent.KEYCODE_ESCAPE, HID_USAGE_ESC)
        put(KeyEvent.KEYCODE_DEL, HID_USAGE_BACKSPACE)
        put(KeyEvent.KEYCODE_TAB, HID_USAGE_TAB)
        put(KeyEvent.KEYCODE_SPACE, HID_USAGE_SPACE)
        put(KeyEvent.KEYCODE_MINUS, HID_USAGE_MINUS)
        put(KeyEvent.KEYCODE_EQUALS, HID_USAGE_EQUALS)
        put(KeyEvent.KEYCODE_LEFT_BRACKET, HID_USAGE_LEFT_BRACKET)
        put(KeyEvent.KEYCODE_RIGHT_BRACKET, HID_USAGE_RIGHT_BRACKET)
        put(KeyEvent.KEYCODE_BACKSLASH, HID_USAGE_BACKSLASH)
        put(KeyEvent.KEYCODE_SEMICOLON, HID_USAGE_SEMICOLON)
        put(KeyEvent.KEYCODE_APOSTROPHE, HID_USAGE_APOSTROPHE)
        put(KeyEvent.KEYCODE_GRAVE, HID_USAGE_GRAVE)
        put(KeyEvent.KEYCODE_COMMA, HID_USAGE_COMMA)
        put(KeyEvent.KEYCODE_PERIOD, HID_USAGE_PERIOD)
        put(KeyEvent.KEYCODE_SLASH, HID_USAGE_SLASH)
        put(KeyEvent.KEYCODE_CAPS_LOCK, HID_USAGE_CAPS_LOCK)
        put(KeyEvent.KEYCODE_F1, HID_USAGE_F1)
        put(KeyEvent.KEYCODE_F2, HID_USAGE_F2)
        put(KeyEvent.KEYCODE_F3, HID_USAGE_F3)
        put(KeyEvent.KEYCODE_F4, HID_USAGE_F4)
        put(KeyEvent.KEYCODE_F5, HID_USAGE_F5)
        put(KeyEvent.KEYCODE_F6, HID_USAGE_F6)
        put(KeyEvent.KEYCODE_F7, HID_USAGE_F7)
        put(KeyEvent.KEYCODE_F8, HID_USAGE_F8)
        put(KeyEvent.KEYCODE_F9, HID_USAGE_F9)
        put(KeyEvent.KEYCODE_F10, HID_USAGE_F10)
        put(KeyEvent.KEYCODE_F11, HID_USAGE_F11)
        put(KeyEvent.KEYCODE_F12, HID_USAGE_F12)
        put(KeyEvent.KEYCODE_FORWARD_DEL, HID_USAGE_DELETE)
        put(KeyEvent.KEYCODE_DPAD_UP, HID_USAGE_UP)
        put(KeyEvent.KEYCODE_DPAD_DOWN, HID_USAGE_DOWN)
        put(KeyEvent.KEYCODE_DPAD_LEFT, HID_USAGE_LEFT)
        put(KeyEvent.KEYCODE_DPAD_RIGHT, HID_USAGE_RIGHT)
        put(KeyEvent.KEYCODE_HOME, HID_USAGE_HOME)
        put(KeyEvent.KEYCODE_PAGE_UP, HID_USAGE_PAGE_UP)
        put(KeyEvent.KEYCODE_PAGE_DOWN, HID_USAGE_PAGE_DOWN)
        put(KeyEvent.KEYCODE_INSERT, HID_USAGE_INSERT)
        put(KeyEvent.KEYCODE_SYSRQ, HID_USAGE_PRINT_SCREEN)
        put(KeyEvent.KEYCODE_SCROLL_LOCK, HID_USAGE_SCROLL_LOCK)
        put(KeyEvent.KEYCODE_PAUSE, HID_USAGE_PAUSE)
        put(KeyEvent.KEYCODE_NUM_LOCK, HID_USAGE_NUM_LOCK)
    }

    fun translateCharToKeycode(char: Char): Int {
        return charToKeycode[char.lowercaseChar()]
            ?: error("No keycode mapping for character: '$char'")
    }

    fun translateKeycodeToHidUsage(keycode: Int): Int {
        return keycodeToHidUsage[keycode]
            ?: error("No HID usage mapping for keycode: $keycode")
    }

    fun getKeyModifiers(char: Char): Set<Modifier> {
        val modifiers = mutableSetOf<Modifier>()
        if (char in shiftedChars) {
            modifiers.add(Modifier.SHIFT)
        }
        return modifiers
    }

    fun translateStringToEvents(text: String): List<HidKeyEvent> {
        val events = mutableListOf<HidKeyEvent>()
        for (char in text) {
            val keycode = translateCharToKeycode(char)
            val hidUsage = translateKeycodeToHidUsage(keycode)
            val modifiers = getKeyModifiers(char)
            events.add(HidKeyEvent(keycode, hidUsage, modifiers, isDown = true))
            events.add(HidKeyEvent(keycode, hidUsage, modifiers, isDown = false))
        }
        return events
    }

    fun isModifierKey(keycode: Int): Boolean {
        return keycode in MODIFIER_KEYCODES
    }

    fun isCtrlCombo(char: Char): Boolean = char.lowercaseChar() in ctrlCombos

    fun getCtrlComboKeycode(char: Char): Int =
        ctrlCombos[char.lowercaseChar()] ?: error("No ctrl combo for '$char'")

    fun isAltCombo(char: Char): Boolean = char in altCombos

    fun getAltComboKeycode(char: Char): Int =
        altCombos[char] ?: error("No alt combo for '$char'")

    companion object {
        private const val HID_USAGE_A = 0x04
        private const val HID_USAGE_B = 0x05
        private const val HID_USAGE_C = 0x06
        private const val HID_USAGE_D = 0x07
        private const val HID_USAGE_E = 0x08
        private const val HID_USAGE_F = 0x09
        private const val HID_USAGE_G = 0x0A
        private const val HID_USAGE_H = 0x0B
        private const val HID_USAGE_I = 0x0C
        private const val HID_USAGE_J = 0x0D
        private const val HID_USAGE_K = 0x0E
        private const val HID_USAGE_L = 0x0F
        private const val HID_USAGE_M = 0x10
        private const val HID_USAGE_N = 0x11
        private const val HID_USAGE_O = 0x12
        private const val HID_USAGE_P = 0x13
        private const val HID_USAGE_Q = 0x14
        private const val HID_USAGE_R = 0x15
        private const val HID_USAGE_S = 0x16
        private const val HID_USAGE_T = 0x17
        private const val HID_USAGE_U = 0x18
        private const val HID_USAGE_V = 0x19
        private const val HID_USAGE_W = 0x1A
        private const val HID_USAGE_X = 0x1B
        private const val HID_USAGE_Y = 0x1C
        private const val HID_USAGE_Z = 0x1D
        private const val HID_USAGE_1 = 0x1E
        private const val HID_USAGE_2 = 0x1F
        private const val HID_USAGE_3 = 0x20
        private const val HID_USAGE_4 = 0x21
        private const val HID_USAGE_5 = 0x22
        private const val HID_USAGE_6 = 0x23
        private const val HID_USAGE_7 = 0x24
        private const val HID_USAGE_8 = 0x25
        private const val HID_USAGE_9 = 0x26
        private const val HID_USAGE_0 = 0x27
        private const val HID_USAGE_ENTER = 0x28
        private const val HID_USAGE_ESC = 0x29
        private const val HID_USAGE_BACKSPACE = 0x2A
        private const val HID_USAGE_TAB = 0x2B
        private const val HID_USAGE_SPACE = 0x2C
        private const val HID_USAGE_MINUS = 0x2D
        private const val HID_USAGE_EQUALS = 0x2E
        private const val HID_USAGE_LEFT_BRACKET = 0x2F
        private const val HID_USAGE_RIGHT_BRACKET = 0x30
        private const val HID_USAGE_BACKSLASH = 0x31
        private const val HID_USAGE_SEMICOLON = 0x33
        private const val HID_USAGE_APOSTROPHE = 0x34
        private const val HID_USAGE_GRAVE = 0x35
        private const val HID_USAGE_COMMA = 0x36
        private const val HID_USAGE_PERIOD = 0x37
        private const val HID_USAGE_SLASH = 0x38
        private const val HID_USAGE_CAPS_LOCK = 0x39
        private const val HID_USAGE_F1 = 0x3A
        private const val HID_USAGE_F2 = 0x3B
        private const val HID_USAGE_F3 = 0x3C
        private const val HID_USAGE_F4 = 0x3D
        private const val HID_USAGE_F5 = 0x3E
        private const val HID_USAGE_F6 = 0x3F
        private const val HID_USAGE_F7 = 0x40
        private const val HID_USAGE_F8 = 0x41
        private const val HID_USAGE_F9 = 0x42
        private const val HID_USAGE_F10 = 0x43
        private const val HID_USAGE_F11 = 0x44
        private const val HID_USAGE_F12 = 0x45
        private const val HID_USAGE_PRINT_SCREEN = 0x46
        private const val HID_USAGE_SCROLL_LOCK = 0x47
        private const val HID_USAGE_PAUSE = 0x48
        private const val HID_USAGE_INSERT = 0x49
        private const val HID_USAGE_HOME = 0x4A
        private const val HID_USAGE_PAGE_UP = 0x4B
        private const val HID_USAGE_DELETE = 0x4C
        private const val HID_USAGE_PAGE_DOWN = 0x4E
        private const val HID_USAGE_RIGHT = 0x4F
        private const val HID_USAGE_LEFT = 0x50
        private const val HID_USAGE_DOWN = 0x51
        private const val HID_USAGE_UP = 0x52
        private const val HID_USAGE_NUM_LOCK = 0x53

        private val MODIFIER_KEYCODES = setOf(
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
        )
    }
}
