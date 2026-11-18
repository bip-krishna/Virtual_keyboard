package com.visionboard.bluetooth.hid

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HidReportDescriptor @Inject constructor() {

    fun buildKeyboardDescriptor(): ByteArray = byteArrayOf(
        // ── Usage Page (Generic Desktop) ──
        0x05, 0x01,        // USAGE_PAGE (Generic Desktop Ctrls)
        0x09, 0x06,        // USAGE (Keyboard)
        0xA1.toByte(), 0x01,        // COLLECTION (Application)

        // ── Modifier keys (8 bits: LCtrl..RGui) ──
        0x05, 0x07,        //   USAGE_PAGE (Keyboard/Keypad)
        0x19, 0xE0.toByte(),        //   USAGE_MINIMUM (Left Control — 0xE0)
        0x29, 0xE7.toByte(),        //   USAGE_MAXIMUM (Right GUI — 0xE7)
        0x15, 0x00,        //   LOGICAL_MINIMUM (0)
        0x25, 0x01,        //   LOGICAL_MAXIMUM (1)
        0x75, 0x01,        //   REPORT_SIZE (1)
        0x95.toByte(), 0x08,        //   REPORT_COUNT (8)
        0x81.toByte(), 0x02,        //   INPUT (Data,Var,Abs) — 8 modifier bits

        // ── Reserved byte ──
        0x95.toByte(), 0x01,        //   REPORT_COUNT (1)
        0x75, 0x08,        //   REPORT_SIZE (8)
        0x81.toByte(), 0x01,        //   INPUT (Const) — reserved byte

        // ── LED output report (5 LEDs + 3 padding bits) ──
        0x05, 0x08,        //   USAGE_PAGE (LEDs)
        0x19, 0x01,        //   USAGE_MINIMUM (Num Lock — 1)
        0x29, 0x05,        //   USAGE_MAXIMUM (Kana — 5)
        0x75, 0x01,        //   REPORT_SIZE (1)
        0x95.toByte(), 0x05,        //   REPORT_COUNT (5)
        0x91.toByte(), 0x02,        //   OUTPUT (Data,Var,Abs) — 5 LED bits
        0x95.toByte(), 0x01,        //   REPORT_COUNT (1)
        0x75, 0x03,        //   REPORT_SIZE (3)
        0x91.toByte(), 0x01,        //   OUTPUT (Const) — LED padding

        // ── Key array (6 slots, usage 0x00–0x65) ──
        0x05, 0x07,        //   USAGE_PAGE (Keyboard/Keypad)
        0x19, 0x00,        //   USAGE_MINIMUM (0 — no event)
        0x29, 0x65,        //   USAGE_MAXIMUM (101 — keyboard usage maximum)
        0x15, 0x00,        //   LOGICAL_MINIMUM (0)
        0x26, 0x65, 0x00,  //   LOGICAL_MAXIMUM (101)
        0x75, 0x08,        //   REPORT_SIZE (8)
        0x95.toByte(), 0x06,        //   REPORT_COUNT (6)
        0x81.toByte(), 0x00,        //   INPUT (Data,Array) — 6 key slots

        0xC0.toByte()               // END_COLLECTION
    )

    fun buildModifierByte(modifiers: Set<Modifier>): Byte {
        var byte = 0x00.toByte()
        for (modifier in modifiers) {
            byte = (byte.toInt() or (1 shl modifier.bitPosition)).toByte()
        }
        return byte
    }

    fun buildKeyReport(hidUsage: Int): ByteArray {
        return byteArrayOf(
            buildModifierByte(emptySet()),
            0x00,
            (hidUsage and 0xFF).toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00,
        )
    }

    fun buildKeyReport(hidUsage: Int, modifiers: Set<Modifier>): ByteArray {
        return byteArrayOf(
            buildModifierByte(modifiers),
            0x00,
            (hidUsage and 0xFF).toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00,
        )
    }

    fun buildKeyReleaseReport(): ByteArray {
        return byteArrayOf(
            0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
    }

    fun buildModifierReport(modifiers: Set<Modifier>): ByteArray {
        return byteArrayOf(
            buildModifierByte(modifiers),
            0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
    }
}
