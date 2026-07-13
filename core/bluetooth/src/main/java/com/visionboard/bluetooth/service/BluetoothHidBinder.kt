package com.visionboard.bluetooth.service

import android.os.Binder
import android.view.KeyEvent
import com.visionboard.bluetooth.handler.BluetoothHidHandler
import com.visionboard.bluetooth.handler.ConnectionState
import com.visionboard.bluetooth.hid.HidKeyTranslator
import com.visionboard.bluetooth.hid.HidKeyEvent
import com.visionboard.bluetooth.hid.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BluetoothHidBinder internal constructor(
    private val service: BluetoothHidService,
    private val hidHandler: BluetoothHidHandler,
    private val keyTranslator: HidKeyTranslator,
) : Binder() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _onDeviceConnected = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val onDeviceConnected: SharedFlow<String> = _onDeviceConnected.asSharedFlow()

    init {
        scope.launch {
            hidHandler.connectionState.collect { state ->
                _connectionState.value = state
            }
        }
        scope.launch {
            hidHandler.isConnected.collect { connected ->
                _isConnected.value = connected
            }
        }
        scope.launch {
            hidHandler.events.collect { event ->
                when (event) {
                    is BluetoothHidHandler.HidEvent.Connected -> {
                        _onDeviceConnected.emit(event.deviceName)
                    }
                    else -> {}
                }
            }
        }
    }

    fun startService() {
        service.startForegroundService()
    }

    fun stopService() {
        service.performStop()
    }

    fun sendKey(keycode: Int) {
        if (hidHandler.isModifierKey(keycode)) {
            handleModifierKey(keycode)
            return
        }
        hidHandler.sendKeyPress(keycode)
        scope.launch {
            delay(KEY_AUTO_RELEASE_MS)
            hidHandler.sendKeyRelease(keycode)
        }
    }

    fun sendKeys(characters: String) {
        scope.launch {
            for (char in characters) {
                if (keyTranslator.isCtrlCombo(char)) {
                    sendCtrlCombo(char)
                    continue
                }
                if (keyTranslator.isAltCombo(char)) {
                    sendAltCombo(char)
                    continue
                }
                val keycode = keyTranslator.translateCharToKeycode(char)
                val modifiers = keyTranslator.getKeyModifiers(char)

                if (modifiers.isNotEmpty()) {
                    for (mod in modifiers) {
                        hidHandler.sendModifierPress(mod)
                    }
                }

                hidHandler.sendKeyPress(keycode)
                delay(KEY_DOWN_UP_DELAY_MS)
                hidHandler.sendKeyRelease(keycode)

                if (modifiers.isNotEmpty()) {
                    for (mod in modifiers) {
                        hidHandler.sendModifierRelease(mod)
                    }
                }

                delay(KEY_INTER_CHAR_DELAY_MS)
            }
        }
    }

    fun sendHidKeyEvent(event: HidKeyEvent) {
        if (event.isDown) {
            for (mod in event.modifiers) {
                hidHandler.sendModifierPress(mod)
            }
            hidHandler.sendKeyPress(event.keycode)
        } else {
            hidHandler.sendKeyRelease(event.keycode)
            for (mod in event.modifiers) {
                hidHandler.sendModifierRelease(mod)
            }
        }
    }

    private fun handleModifierKey(keycode: Int) {
        val modifier = keycodeToModifier(keycode) ?: return
        hidHandler.sendModifierPress(modifier)
        scope.launch {
            delay(KEY_AUTO_RELEASE_MS)
            hidHandler.sendModifierRelease(modifier)
        }
    }

    private suspend fun sendCtrlCombo(char: Char) {
        val keycode = keyTranslator.getCtrlComboKeycode(char)
        hidHandler.sendModifierPress(Modifier.CTRL)
        delay(KEY_MODIFIER_INTERVAL_MS)
        hidHandler.sendKeyPress(keycode)
        delay(KEY_DOWN_UP_DELAY_MS)
        hidHandler.sendKeyRelease(keycode)
        delay(KEY_MODIFIER_INTERVAL_MS)
        hidHandler.sendModifierRelease(Modifier.CTRL)
    }

    private suspend fun sendAltCombo(char: Char) {
        val keycode = keyTranslator.getAltComboKeycode(char)
        hidHandler.sendModifierPress(Modifier.ALT)
        delay(KEY_MODIFIER_INTERVAL_MS)
        hidHandler.sendKeyPress(keycode)
        delay(KEY_DOWN_UP_DELAY_MS)
        hidHandler.sendKeyRelease(keycode)
        delay(KEY_MODIFIER_INTERVAL_MS)
        hidHandler.sendModifierRelease(Modifier.ALT)
    }

    fun connect() {
        service.startConnection()
    }

    fun disconnect() {
        hidHandler.disconnect()
    }

    fun getRemoteDeviceName(): String? = hidHandler.getRemoteDeviceName()

    private fun keycodeToModifier(keycode: Int): Modifier? = when (keycode) {
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> Modifier.SHIFT
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> Modifier.CTRL
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> Modifier.ALT
        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> Modifier.GUI
        else -> null
    }

    companion object {
        private const val KEY_AUTO_RELEASE_MS = 50L
        private const val KEY_DOWN_UP_DELAY_MS = 20L
        private const val KEY_INTER_CHAR_DELAY_MS = 30L
        private const val KEY_MODIFIER_INTERVAL_MS = 10L
    }
}
