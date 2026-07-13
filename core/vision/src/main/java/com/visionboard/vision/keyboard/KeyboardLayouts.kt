package com.visionboard.vision.keyboard

import android.graphics.PointF
import android.view.KeyEvent
import com.visionboard.vision.model.KeyData

object KeyboardLayouts {

    fun usQwerty(): Map<String, KeyData> = buildMap {
        putAll(numberRow())
        putAll(letterRows())
        putAll(spaceRow())
        putAll(functionRow())
        putAll(modifierKeys())
    }

    private fun numberRow(): Map<String, KeyData> = mapOf(
        keyData("`", KeyEvent.KEYCODE_GRAVE, listOf(0.0f to 0.0f, 0.035f to 0.0f, 0.035f to 0.06f, 0.0f to 0.06f), listOf("1", "TAB")),
        keyData("1", KeyEvent.KEYCODE_1, listOf(0.035f to 0.0f, 0.07f to 0.0f, 0.07f to 0.06f, 0.035f to 0.06f), listOf("2", "`")),
        keyData("2", KeyEvent.KEYCODE_2, listOf(0.07f to 0.0f, 0.105f to 0.0f, 0.105f to 0.06f, 0.07f to 0.06f), listOf("3", "1")),
        keyData("3", KeyEvent.KEYCODE_3, listOf(0.105f to 0.0f, 0.14f to 0.0f, 0.14f to 0.06f, 0.105f to 0.06f), listOf("4", "2")),
        keyData("4", KeyEvent.KEYCODE_4, listOf(0.14f to 0.0f, 0.175f to 0.0f, 0.175f to 0.06f, 0.14f to 0.06f), listOf("5", "3")),
        keyData("5", KeyEvent.KEYCODE_5, listOf(0.175f to 0.0f, 0.21f to 0.0f, 0.21f to 0.06f, 0.175f to 0.06f), listOf("6", "4")),
        keyData("6", KeyEvent.KEYCODE_6, listOf(0.21f to 0.0f, 0.245f to 0.0f, 0.245f to 0.06f, 0.21f to 0.06f), listOf("7", "5")),
        keyData("7", KeyEvent.KEYCODE_7, listOf(0.245f to 0.0f, 0.28f to 0.0f, 0.28f to 0.06f, 0.245f to 0.06f), listOf("8", "6")),
        keyData("8", KeyEvent.KEYCODE_8, listOf(0.28f to 0.0f, 0.315f to 0.0f, 0.315f to 0.06f, 0.28f to 0.06f), listOf("9", "7")),
        keyData("9", KeyEvent.KEYCODE_9, listOf(0.315f to 0.0f, 0.35f to 0.0f, 0.35f to 0.06f, 0.315f to 0.06f), listOf("0", "8")),
        keyData("0", KeyEvent.KEYCODE_0, listOf(0.35f to 0.0f, 0.385f to 0.0f, 0.385f to 0.06f, 0.35f to 0.06f), listOf("-", "9")),
        keyData("-", KeyEvent.KEYCODE_MINUS, listOf(0.385f to 0.0f, 0.42f to 0.0f, 0.42f to 0.06f, 0.385f to 0.06f), listOf("=", "0")),
        keyData("=", KeyEvent.KEYCODE_EQUALS, listOf(0.42f to 0.0f, 0.455f to 0.0f, 0.455f to 0.06f, 0.42f to 0.06f), listOf("BACKSPACE", "-")),
        keyData("BACKSPACE", KeyEvent.KEYCODE_DEL, listOf(0.455f to 0.0f, 0.52f to 0.0f, 0.52f to 0.06f, 0.455f to 0.06f), listOf("=", "INS")),
        keyData("INS", KeyEvent.KEYCODE_INSERT, listOf(0.53f to 0.0f, 0.565f to 0.0f, 0.565f to 0.06f, 0.53f to 0.06f), listOf("HOME", "BACKSPACE")),
        keyData("HOME", KeyEvent.KEYCODE_MOVE_HOME, listOf(0.565f to 0.0f, 0.6f to 0.0f, 0.6f to 0.06f, 0.565f to 0.06f), listOf("PAGE_UP", "INS")),
        keyData("PAGE_UP", KeyEvent.KEYCODE_PAGE_UP, listOf(0.6f to 0.0f, 0.635f to 0.0f, 0.635f to 0.06f, 0.6f to 0.06f), listOf("NUM_LOCK", "HOME")),
        keyData("NUM_LOCK", KeyEvent.KEYCODE_NUM_LOCK, listOf(0.66f to 0.0f, 0.695f to 0.0f, 0.695f to 0.06f, 0.66f to 0.06f), listOf("/", "PAGE_UP")),
        keyData("/", KeyEvent.KEYCODE_SLASH, listOf(0.695f to 0.0f, 0.73f to 0.0f, 0.73f to 0.06f, 0.695f to 0.06f), listOf("*", "NUM_LOCK")),
        keyData("*", KeyEvent.KEYCODE_STAR, listOf(0.73f to 0.0f, 0.765f to 0.0f, 0.765f to 0.06f, 0.73f to 0.06f), listOf("-", "/")),
        keyData("-", KeyEvent.KEYCODE_MINUS, listOf(0.765f to 0.0f, 0.8f to 0.0f, 0.8f to 0.06f, 0.765f to 0.06f), listOf("NUM_PLUS", "*")),
        keyData("NUM_PLUS", KeyEvent.KEYCODE_PLUS, listOf(0.8f to 0.0f, 0.835f to 0.0f, 0.835f to 0.06f, 0.8f to 0.06f), listOf("NUM_ENTER", "-")),
        keyData("NUM_ENTER", KeyEvent.KEYCODE_NUMPAD_ENTER, listOf(0.835f to 0.0f, 0.87f to 0.0f, 0.87f to 0.06f, 0.835f to 0.06f), listOf("NUM_0", "NUM_PLUS")),
        keyData("NUM_0", KeyEvent.KEYCODE_NUMPAD_0, listOf(0.87f to 0.0f, 0.935f to 0.0f, 0.935f to 0.06f, 0.87f to 0.06f), listOf("NUM_1", "NUM_ENTER")),
        keyData("NUM_1", KeyEvent.KEYCODE_NUMPAD_1, listOf(0.935f to 0.0f, 0.97f to 0.0f, 0.97f to 0.06f, 0.935f to 0.06f), listOf("NUM_2", "NUM_0"))
    )

    private fun letterRows(): Map<String, KeyData> = mapOf(
        keyData("TAB", KeyEvent.KEYCODE_TAB, listOf(0.0f to 0.065f, 0.045f to 0.065f, 0.045f to 0.125f, 0.0f to 0.125f), listOf("Q", "CAPS")),
        keyData("Q", KeyEvent.KEYCODE_Q, listOf(0.045f to 0.065f, 0.08f to 0.065f, 0.08f to 0.125f, 0.045f to 0.125f), listOf("W", "1", "TAB")),
        keyData("W", KeyEvent.KEYCODE_W, listOf(0.08f to 0.065f, 0.115f to 0.065f, 0.115f to 0.125f, 0.08f to 0.125f), listOf("E", "2", "Q")),
        keyData("E", KeyEvent.KEYCODE_E, listOf(0.115f to 0.065f, 0.15f to 0.065f, 0.15f to 0.125f, 0.115f to 0.125f), listOf("R", "3", "W")),
        keyData("R", KeyEvent.KEYCODE_R, listOf(0.15f to 0.065f, 0.185f to 0.065f, 0.185f to 0.125f, 0.15f to 0.125f), listOf("T", "4", "E")),
        keyData("T", KeyEvent.KEYCODE_T, listOf(0.185f to 0.065f, 0.22f to 0.065f, 0.22f to 0.125f, 0.185f to 0.125f), listOf("Y", "5", "R")),
        keyData("Y", KeyEvent.KEYCODE_Y, listOf(0.22f to 0.065f, 0.255f to 0.065f, 0.255f to 0.125f, 0.22f to 0.125f), listOf("U", "6", "T")),
        keyData("U", KeyEvent.KEYCODE_U, listOf(0.255f to 0.065f, 0.29f to 0.065f, 0.29f to 0.125f, 0.255f to 0.125f), listOf("I", "7", "Y")),
        keyData("I", KeyEvent.KEYCODE_I, listOf(0.29f to 0.065f, 0.325f to 0.065f, 0.325f to 0.125f, 0.29f to 0.125f), listOf("O", "8", "U")),
        keyData("O", KeyEvent.KEYCODE_O, listOf(0.325f to 0.065f, 0.36f to 0.065f, 0.36f to 0.125f, 0.325f to 0.125f), listOf("P", "9", "I")),
        keyData("P", KeyEvent.KEYCODE_P, listOf(0.36f to 0.065f, 0.395f to 0.065f, 0.395f to 0.125f, 0.36f to 0.125f), listOf("[", "0", "O")),
        keyData("[", KeyEvent.KEYCODE_LEFT_BRACKET, listOf(0.395f to 0.065f, 0.43f to 0.065f, 0.43f to 0.125f, 0.395f to 0.125f), listOf("]", "-", "P")),
        keyData("]", KeyEvent.KEYCODE_RIGHT_BRACKET, listOf(0.43f to 0.065f, 0.465f to 0.065f, 0.465f to 0.125f, 0.43f to 0.125f), listOf("\\", "=", "[")),
        keyData("\\", KeyEvent.KEYCODE_BACKSLASH, listOf(0.465f to 0.065f, 0.5f to 0.065f, 0.5f to 0.125f, 0.465f to 0.125f), listOf("DEL", "]")),
        keyData("DEL", KeyEvent.KEYCODE_FORWARD_DEL, listOf(0.51f to 0.065f, 0.545f to 0.065f, 0.545f to 0.125f, 0.51f to 0.125f), listOf("END", "\\")),
        keyData("END", KeyEvent.KEYCODE_MOVE_END, listOf(0.545f to 0.065f, 0.58f to 0.065f, 0.58f to 0.125f, 0.545f to 0.125f), listOf("PAGE_DOWN", "DEL")),
        keyData("PAGE_DOWN", KeyEvent.KEYCODE_PAGE_DOWN, listOf(0.58f to 0.065f, 0.615f to 0.065f, 0.615f to 0.125f, 0.58f to 0.125f), listOf("7_NUMPAD", "END")),
        keyData("7_NUMPAD", KeyEvent.KEYCODE_NUMPAD_7, listOf(0.66f to 0.065f, 0.695f to 0.065f, 0.695f to 0.125f, 0.66f to 0.125f), listOf("8_NUMPAD", "PAGE_DOWN")),
        keyData("8_NUMPAD", KeyEvent.KEYCODE_NUMPAD_8, listOf(0.695f to 0.065f, 0.73f to 0.065f, 0.73f to 0.125f, 0.695f to 0.125f), listOf("9_NUMPAD", "7_NUMPAD")),
        keyData("9_NUMPAD", KeyEvent.KEYCODE_NUMPAD_9, listOf(0.73f to 0.065f, 0.765f to 0.065f, 0.765f to 0.125f, 0.73f to 0.125f), listOf("NUM_MINUS", "8_NUMPAD")),

        keyData("CAPS", KeyEvent.KEYCODE_CAPS_LOCK, listOf(0.0f to 0.13f, 0.05f to 0.13f, 0.05f to 0.19f, 0.0f to 0.19f), listOf("A", "SHIFT_L")),
        keyData("A", KeyEvent.KEYCODE_A, listOf(0.05f to 0.13f, 0.085f to 0.13f, 0.085f to 0.19f, 0.05f to 0.19f), listOf("S", "Q", "CAPS")),
        keyData("S", KeyEvent.KEYCODE_S, listOf(0.085f to 0.13f, 0.12f to 0.13f, 0.12f to 0.19f, 0.085f to 0.19f), listOf("D", "W", "A")),
        keyData("D", KeyEvent.KEYCODE_D, listOf(0.12f to 0.13f, 0.155f to 0.13f, 0.155f to 0.19f, 0.12f to 0.19f), listOf("F", "E", "S")),
        keyData("F", KeyEvent.KEYCODE_F, listOf(0.155f to 0.13f, 0.19f to 0.13f, 0.19f to 0.19f, 0.155f to 0.19f), listOf("G", "R", "D")),
        keyData("G", KeyEvent.KEYCODE_G, listOf(0.19f to 0.13f, 0.225f to 0.13f, 0.225f to 0.19f, 0.19f to 0.19f), listOf("H", "T", "F")),
        keyData("H", KeyEvent.KEYCODE_H, listOf(0.225f to 0.13f, 0.26f to 0.13f, 0.26f to 0.19f, 0.225f to 0.19f), listOf("J", "Y", "G")),
        keyData("J", KeyEvent.KEYCODE_J, listOf(0.26f to 0.13f, 0.295f to 0.13f, 0.295f to 0.19f, 0.26f to 0.19f), listOf("K", "U", "H")),
        keyData("K", KeyEvent.KEYCODE_K, listOf(0.295f to 0.13f, 0.33f to 0.13f, 0.33f to 0.19f, 0.295f to 0.19f), listOf("L", "I", "J")),
        keyData("L", KeyEvent.KEYCODE_L, listOf(0.33f to 0.13f, 0.365f to 0.13f, 0.365f to 0.19f, 0.33f to 0.19f), listOf(";", "O", "K")),
        keyData(";", KeyEvent.KEYCODE_SEMICOLON, listOf(0.365f to 0.13f, 0.4f to 0.13f, 0.4f to 0.19f, 0.365f to 0.19f), listOf("'", "P", "L")),
        keyData("'", KeyEvent.KEYCODE_APOSTROPHE, listOf(0.4f to 0.13f, 0.435f to 0.13f, 0.435f to 0.19f, 0.4f to 0.19f), listOf("ENTER", "[", ";")),
        keyData("ENTER", KeyEvent.KEYCODE_ENTER, listOf(0.435f to 0.13f, 0.5f to 0.13f, 0.5f to 0.19f, 0.435f to 0.19f), listOf("INS_NUMPAD", "'")),
        keyData("INS_NUMPAD", KeyEvent.KEYCODE_NUMPAD_COMMA, listOf(0.51f to 0.13f, 0.545f to 0.13f, 0.545f to 0.19f, 0.51f to 0.19f), listOf("UP", "ENTER")),
        keyData("UP", KeyEvent.KEYCODE_DPAD_UP, listOf(0.56f to 0.13f, 0.595f to 0.13f, 0.595f to 0.19f, 0.56f to 0.19f), listOf("4_NUMPAD", "INS_NUMPAD")),
        keyData("4_NUMPAD", KeyEvent.KEYCODE_NUMPAD_4, listOf(0.66f to 0.13f, 0.695f to 0.13f, 0.695f to 0.19f, 0.66f to 0.19f), listOf("5_NUMPAD", "UP")),
        keyData("5_NUMPAD", KeyEvent.KEYCODE_NUMPAD_5, listOf(0.695f to 0.13f, 0.73f to 0.13f, 0.73f to 0.19f, 0.695f to 0.19f), listOf("6_NUMPAD", "4_NUMPAD")),
        keyData("6_NUMPAD", KeyEvent.KEYCODE_NUMPAD_6, listOf(0.73f to 0.13f, 0.765f to 0.13f, 0.765f to 0.19f, 0.73f to 0.19f), listOf("NUM_PLUS_NUMPAD", "5_NUMPAD")),

        keyData("SHIFT_L", KeyEvent.KEYCODE_SHIFT_LEFT, listOf(0.0f to 0.195f, 0.06f to 0.195f, 0.06f to 0.255f, 0.0f to 0.255f), listOf("Z", "CAPS")),
        keyData("Z", KeyEvent.KEYCODE_Z, listOf(0.06f to 0.195f, 0.095f to 0.195f, 0.095f to 0.255f, 0.06f to 0.255f), listOf("X", "A", "SHIFT_L")),
        keyData("X", KeyEvent.KEYCODE_X, listOf(0.095f to 0.195f, 0.13f to 0.195f, 0.13f to 0.255f, 0.095f to 0.255f), listOf("C", "S", "Z")),
        keyData("C", KeyEvent.KEYCODE_C, listOf(0.13f to 0.195f, 0.165f to 0.195f, 0.165f to 0.255f, 0.13f to 0.255f), listOf("V", "D", "X")),
        keyData("V", KeyEvent.KEYCODE_V, listOf(0.165f to 0.195f, 0.2f to 0.195f, 0.2f to 0.255f, 0.165f to 0.255f), listOf("B", "F", "C")),
        keyData("B", KeyEvent.KEYCODE_B, listOf(0.2f to 0.195f, 0.235f to 0.195f, 0.235f to 0.255f, 0.2f to 0.255f), listOf("N", "G", "V")),
        keyData("N", KeyEvent.KEYCODE_N, listOf(0.235f to 0.195f, 0.27f to 0.195f, 0.27f to 0.255f, 0.235f to 0.255f), listOf("M", "H", "B")),
        keyData("M", KeyEvent.KEYCODE_M, listOf(0.27f to 0.195f, 0.305f to 0.195f, 0.305f to 0.255f, 0.27f to 0.255f), listOf(",", "J", "N")),
        keyData(",", KeyEvent.KEYCODE_COMMA, listOf(0.305f to 0.195f, 0.34f to 0.195f, 0.34f to 0.255f, 0.305f to 0.255f), listOf(".", "K", "M")),
        keyData(".", KeyEvent.KEYCODE_PERIOD, listOf(0.34f to 0.195f, 0.375f to 0.195f, 0.375f to 0.255f, 0.34f to 0.255f), listOf("/", "L", ",")),
        keyData("/", KeyEvent.KEYCODE_SLASH, listOf(0.375f to 0.195f, 0.41f to 0.195f, 0.41f to 0.255f, 0.375f to 0.255f), listOf("SHIFT_R", ".", ";")),
        keyData("SHIFT_R", KeyEvent.KEYCODE_SHIFT_RIGHT, listOf(0.41f to 0.195f, 0.5f to 0.195f, 0.5f to 0.255f, 0.41f to 0.255f), listOf("UP", "/")),
        keyData("DOWN", KeyEvent.KEYCODE_DPAD_DOWN, listOf(0.56f to 0.195f, 0.595f to 0.195f, 0.595f to 0.255f, 0.56f to 0.255f), listOf("LEFT", "SHIFT_R")),
        keyData("LEFT", KeyEvent.KEYCODE_DPAD_LEFT, listOf(0.52f to 0.195f, 0.555f to 0.195f, 0.555f to 0.255f, 0.52f to 0.255f), listOf("RIGHT", "DOWN")),
        keyData("RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT, listOf(0.565f to 0.255f, 0.6f to 0.255f, 0.6f to 0.315f, 0.565f to 0.315f), listOf("1_NUMPAD", "DOWN")),
        keyData("1_NUMPAD", KeyEvent.KEYCODE_NUMPAD_1, listOf(0.66f to 0.195f, 0.695f to 0.195f, 0.695f to 0.255f, 0.66f to 0.255f), listOf("2_NUMPAD", "RIGHT")),
        keyData("2_NUMPAD", KeyEvent.KEYCODE_NUMPAD_2, listOf(0.695f to 0.195f, 0.73f to 0.195f, 0.73f to 0.255f, 0.695f to 0.255f), listOf("3_NUMPAD", "1_NUMPAD")),
        keyData("3_NUMPAD", KeyEvent.KEYCODE_NUMPAD_3, listOf(0.73f to 0.195f, 0.765f to 0.195f, 0.765f to 0.255f, 0.73f to 0.255f), listOf("NUM_ENTER_NUMPAD", "2_NUMPAD")),

        keyData("CTRL_L", KeyEvent.KEYCODE_CTRL_LEFT, listOf(0.0f to 0.26f, 0.04f to 0.26f, 0.04f to 0.32f, 0.0f to 0.32f), listOf("ALT_L", "SHIFT_L")),
        keyData("ALT_L", KeyEvent.KEYCODE_ALT_LEFT, listOf(0.04f to 0.26f, 0.08f to 0.26f, 0.08f to 0.32f, 0.04f to 0.32f), listOf("CMD_L", "CTRL_L")),
        keyData("CMD_L", KeyEvent.KEYCODE_META_LEFT, listOf(0.08f to 0.26f, 0.12f to 0.26f, 0.12f to 0.32f, 0.08f to 0.32f), listOf("SPACE", "ALT_L")),
        keyData("SPACE", KeyEvent.KEYCODE_SPACE, listOf(0.12f to 0.26f, 0.38f to 0.26f, 0.38f to 0.32f, 0.12f to 0.32f), listOf("CMD_R", "CMD_L")),
        keyData("CMD_R", KeyEvent.KEYCODE_META_RIGHT, listOf(0.38f to 0.26f, 0.42f to 0.26f, 0.42f to 0.32f, 0.38f to 0.32f), listOf("ALT_R", "SPACE")),
        keyData("ALT_R", KeyEvent.KEYCODE_ALT_RIGHT, listOf(0.42f to 0.26f, 0.46f to 0.26f, 0.46f to 0.32f, 0.42f to 0.32f), listOf("FN", "CMD_R")),
        keyData("FN", KeyEvent.KEYCODE_FUNCTION, listOf(0.46f to 0.26f, 0.5f to 0.26f, 0.5f to 0.32f, 0.46f to 0.32f), listOf("CTRL_R", "ALT_R")),
        keyData("CTRL_R", KeyEvent.KEYCODE_CTRL_RIGHT, listOf(0.5f to 0.26f, 0.54f to 0.26f, 0.54f to 0.32f, 0.5f to 0.32f), listOf("0_NUMPAD", "FN")),
        keyData("0_NUMPAD", KeyEvent.KEYCODE_NUMPAD_0, listOf(0.695f to 0.26f, 0.765f to 0.26f, 0.765f to 0.32f, 0.695f to 0.32f), listOf("NUM_DOT", "3_NUMPAD")),
        keyData("NUM_DOT", KeyEvent.KEYCODE_NUMPAD_DOT, listOf(0.765f to 0.26f, 0.8f to 0.26f, 0.8f to 0.32f, 0.765f to 0.32f), listOf("NUM_ENTER_NUMPAD", "0_NUMPAD")),
        keyData("NUM_ENTER_NUMPAD", KeyEvent.KEYCODE_NUMPAD_ENTER, listOf(0.8f to 0.26f, 0.87f to 0.26f, 0.87f to 0.32f, 0.8f to 0.32f), listOf("ESC", "NUM_DOT"))
    )

    private fun spaceRow(): Map<String, KeyData> = emptyMap()

    private fun functionRow(): Map<String, KeyData> = mapOf(
        keyData("ESC", KeyEvent.KEYCODE_ESCAPE, listOf(0.0f to 0.325f, 0.035f to 0.325f, 0.035f to 0.38f, 0.0f to 0.38f), listOf("F1", "CTRL_R")),
        keyData("F1", KeyEvent.KEYCODE_F1, listOf(0.04f to 0.325f, 0.075f to 0.325f, 0.075f to 0.38f, 0.04f to 0.38f), listOf("F2", "ESC")),
        keyData("F2", KeyEvent.KEYCODE_F2, listOf(0.075f to 0.325f, 0.11f to 0.325f, 0.11f to 0.38f, 0.075f to 0.38f), listOf("F3", "F1")),
        keyData("F3", KeyEvent.KEYCODE_F3, listOf(0.11f to 0.325f, 0.145f to 0.325f, 0.145f to 0.38f, 0.11f to 0.38f), listOf("F4", "F2")),
        keyData("F4", KeyEvent.KEYCODE_F4, listOf(0.145f to 0.325f, 0.18f to 0.325f, 0.18f to 0.38f, 0.145f to 0.38f), listOf("F5", "F3")),
        keyData("F5", KeyEvent.KEYCODE_F5, listOf(0.19f to 0.325f, 0.225f to 0.325f, 0.225f to 0.38f, 0.19f to 0.38f), listOf("F6", "F4")),
        keyData("F6", KeyEvent.KEYCODE_F6, listOf(0.225f to 0.325f, 0.26f to 0.325f, 0.26f to 0.38f, 0.225f to 0.38f), listOf("F7", "F5")),
        keyData("F7", KeyEvent.KEYCODE_F7, listOf(0.265f to 0.325f, 0.3f to 0.325f, 0.3f to 0.38f, 0.265f to 0.38f), listOf("F8", "F6")),
        keyData("F8", KeyEvent.KEYCODE_F8, listOf(0.3f to 0.325f, 0.335f to 0.325f, 0.335f to 0.38f, 0.3f to 0.38f), listOf("F9", "F7")),
        keyData("F9", KeyEvent.KEYCODE_F9, listOf(0.34f to 0.325f, 0.375f to 0.325f, 0.375f to 0.38f, 0.34f to 0.38f), listOf("F10", "F8")),
        keyData("F10", KeyEvent.KEYCODE_F10, listOf(0.375f to 0.325f, 0.41f to 0.325f, 0.41f to 0.38f, 0.375f to 0.38f), listOf("F11", "F9")),
        keyData("F11", KeyEvent.KEYCODE_F11, listOf(0.415f to 0.325f, 0.45f to 0.325f, 0.45f to 0.38f, 0.415f to 0.38f), listOf("F12", "F10")),
        keyData("F12", KeyEvent.KEYCODE_F12, listOf(0.45f to 0.325f, 0.485f to 0.325f, 0.485f to 0.38f, 0.45f to 0.38f), listOf("PRINT", "F11")),
        keyData("PRINT", KeyEvent.KEYCODE_SYSRQ, listOf(0.495f to 0.325f, 0.53f to 0.325f, 0.53f to 0.38f, 0.495f to 0.38f), listOf("SCROLL_LOCK", "F12")),
        keyData("SCROLL_LOCK", KeyEvent.KEYCODE_SCROLL_LOCK, listOf(0.53f to 0.325f, 0.565f to 0.325f, 0.565f to 0.38f, 0.53f to 0.38f), listOf("PAUSE", "PRINT")),
        keyData("PAUSE", KeyEvent.KEYCODE_BREAK, listOf(0.565f to 0.325f, 0.6f to 0.325f, 0.6f to 0.38f, 0.565f to 0.38f), listOf("9_NUMPAD_TOP", "SCROLL_LOCK")),
        keyData("9_NUMPAD_TOP", KeyEvent.KEYCODE_NUMPAD_9, listOf(0.66f to 0.325f, 0.695f to 0.325f, 0.695f to 0.38f, 0.66f to 0.38f), listOf("MULTIPLY_NUMPAD", "PAUSE")),
        keyData("MULTIPLY_NUMPAD", KeyEvent.KEYCODE_NUMPAD_MULTIPLY, listOf(0.695f to 0.325f, 0.73f to 0.325f, 0.73f to 0.38f, 0.695f to 0.38f), listOf("DIVIDE_NUMPAD", "9_NUMPAD_TOP")),
        keyData("DIVIDE_NUMPAD", KeyEvent.KEYCODE_NUMPAD_DIVIDE, listOf(0.73f to 0.325f, 0.765f to 0.325f, 0.765f to 0.38f, 0.73f to 0.38f), listOf("SUBTRACT_NUMPAD", "MULTIPLY_NUMPAD")),
        keyData("SUBTRACT_NUMPAD", KeyEvent.KEYCODE_NUMPAD_SUBTRACT, listOf(0.765f to 0.325f, 0.8f to 0.325f, 0.8f to 0.38f, 0.765f to 0.38f), listOf("ADD_NUMPAD", "DIVIDE_NUMPAD")),
        keyData("ADD_NUMPAD", KeyEvent.KEYCODE_NUMPAD_ADD, listOf(0.8f to 0.325f, 0.87f to 0.325f, 0.87f to 0.38f, 0.8f to 0.38f), listOf("EQUAL_NUMPAD", "SUBTRACT_NUMPAD")),
        keyData("EQUAL_NUMPAD", KeyEvent.KEYCODE_NUMPAD_EQUALS, listOf(0.87f to 0.325f, 0.97f to 0.325f, 0.97f to 0.38f, 0.87f to 0.38f), listOf("ADD_NUMPAD"))
    )

    private fun modifierKeys(): Map<String, KeyData> = emptyMap()

    private fun keyData(
        label: String,
        keyCode: Int,
        polygonPairs: List<Pair<Float, Float>>,
        neighbors: List<String>
    ): Pair<String, KeyData> {
        val polygon = polygonPairs.map { PointF(it.first, it.second) }
        return label to KeyData(
            label = label,
            keyCode = keyCode,
            relativePolygon = polygon,
            neighbors = neighbors
        )
    }
}
