package com.visionboard.bluetooth.handler

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.visionboard.bluetooth.hid.HidKeyTranslator
import com.visionboard.bluetooth.hid.Modifier
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState {
    DISCOVERABLE,
    WAITING_FOR_PAIRING,
    PAIRING,
    CONNECTED,
    DISCONNECTED,
    ERROR,
}

@Singleton
class BluetoothHidHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyTranslator: HidKeyTranslator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _remoteDeviceName = MutableStateFlow<String?>(null)
    val remoteDeviceName: StateFlow<String?> = _remoteDeviceName.asStateFlow()

    private val _events = MutableSharedFlow<HidEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<HidEvent> = _events.asSharedFlow()

    private var remoteDevice: BluetoothDevice? = null
    private var hidDevice: Any? = null
    private var reconnectAttempt = 0

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    init {
        obtainHidDeviceProxy()
    }

    fun connect(): Flow<ConnectionState> = flow {
        emit(ConnectionState.DISCOVERABLE)
        _connectionState.value = ConnectionState.DISCOVERABLE

        if (!ensureBluetoothEnabled()) {
            emit(ConnectionState.ERROR)
            _connectionState.value = ConnectionState.ERROR
            return@flow
        }

        startDiscovery()

        emit(ConnectionState.WAITING_FOR_PAIRING)
        _connectionState.value = ConnectionState.WAITING_FOR_PAIRING
    }

    fun disconnect() {
        scope.launch {
            reconnectAttempt = 0
            _connectionState.value = ConnectionState.DISCONNECTED
            _isConnected.value = false
            _remoteDeviceName.value = null
            remoteDevice = null
            disconnectHidDevice()
        }
    }

    fun sendKeyPress(keycode: Int) {
        scope.launch {
            if (_connectionState.value != ConnectionState.CONNECTED) {
                Log.w(TAG, "Cannot send key press — not connected")
                return@launch
            }
            val hidUsage = keyTranslator.translateKeycodeToHidUsage(keycode)
            sendHidReport(hidUsage, emptySet())
            _events.emit(HidEvent.KeyPressed(keycode, hidUsage))
        }
    }

    fun sendKeyRelease(keycode: Int) {
        scope.launch {
            if (_connectionState.value != ConnectionState.CONNECTED) return@launch
            sendHidReport(0, emptySet())
            _events.emit(HidEvent.KeyReleased(keycode))
        }
    }

    fun sendModifierPress(modifier: Modifier) {
        scope.launch {
            if (_connectionState.value != ConnectionState.CONNECTED) return@launch
            sendModifierHidReport(setOf(modifier))
            _events.emit(HidEvent.ModifierPressed(modifier))
        }
    }

    fun sendModifierRelease(modifier: Modifier) {
        scope.launch {
            if (_connectionState.value != ConnectionState.CONNECTED) return@launch
            sendModifierHidReport(emptySet())
            _events.emit(HidEvent.ModifierReleased(modifier))
        }
    }

    fun isConnected(): Boolean = _isConnected.value

    fun getConnectionState(): ConnectionState = _connectionState.value

    fun getRemoteDeviceName(): String? = _remoteDeviceName.value

    private fun obtainHidDeviceProxy() {
        try {
            val getHidDevice = bluetoothAdapter?.javaClass?.getMethod("getHidDevice")
            hidDevice = getHidDevice?.invoke(bluetoothAdapter)
            Log.d(TAG, "BluetoothHidDevice proxy obtained: ${hidDevice != null}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to obtain BluetoothHidDevice proxy via reflection", e)
            hidDevice = null
        }
    }

    private suspend fun ensureBluetoothEnabled(): Boolean {
        if (bluetoothAdapter?.isEnabled == true) return true
        return try {
            bluetoothAdapter?.enable()
            delay(1500)
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_ADMIN permission to enable adapter", e)
            false
        }
    }

    private fun startDiscovery() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothAdapter?.startDiscovery()
            } else {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.startDiscovery()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot start discovery", e)
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        remoteDevice = device
        _remoteDeviceName.value = device.name ?: device.address

        scope.launch {
            _connectionState.value = ConnectionState.PAIRING
            try {
                val pairResult = device.createBond()
                if (!pairResult) {
                    _connectionState.value = ConnectionState.ERROR
                    return@launch
                }
                _connectionState.value = ConnectionState.CONNECTED
                _isConnected.value = true
                reconnectAttempt = 0
                _events.emit(HidEvent.Connected(device.name ?: device.address))
            } catch (e: SecurityException) {
                Log.e(TAG, "Pairing failed due to missing permission", e)
                _connectionState.value = ConnectionState.ERROR
                _events.emit(HidEvent.Error("Pairing permission denied"))
            }
        }
    }

    private fun sendHidReport(hidUsage: Int, modifiers: Set<Modifier>) {
        try {
            if (hidDevice == null) {
                Log.w(TAG, "HID device proxy not available, simulating report")
                return
            }
            val sendReportMethod = hidDevice!!.javaClass.getMethod(
                "sendReport",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType,
                ByteArray::class.java,
            )
            val report = byteArrayOf(
                0x00, // modifier byte
                0x00, // reserved
                (hidUsage and 0xFF).toByte(),
                0x00, 0x00, 0x00, 0x00, 0x00,
            )
            sendReportMethod.invoke(hidDevice, remoteDevice, REPORT_ID_KEYBOARD, report)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send HID report", e)
        }
    }

    private fun sendModifierHidReport(modifiers: Set<Modifier>) {
        try {
            if (hidDevice == null) return
            var modifierByte = 0x00
            for (mod in modifiers) {
                modifierByte = modifierByte or (1 shl mod.bitPosition)
            }
            val report = byteArrayOf(
                modifierByte.toByte(),
                0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )
            val sendReportMethod = hidDevice!!.javaClass.getMethod(
                "sendReport",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType,
                ByteArray::class.java,
            )
            sendReportMethod.invoke(hidDevice, remoteDevice, REPORT_ID_KEYBOARD, report)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send modifier HID report", e)
        }
    }

    private fun disconnectHidDevice() {
        try {
            if (hidDevice == null) return
            val disconnectMethod = hidDevice!!.javaClass.getMethod("disconnect")
            disconnectMethod.invoke(hidDevice)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect HID device", e)
        }
    }

    fun handleDeviceFound(device: BluetoothDevice) {
        scope.launch {
            _events.emit(HidEvent.DeviceFound(device.name ?: device.address, device.address))
        }
    }

    fun handlePairingStateChange(device: BluetoothDevice, bondState: Int) {
        when (bondState) {
            BluetoothDevice.BOND_NONE -> {
                _connectionState.value = ConnectionState.DISCONNECTED
                _isConnected.value = false
                scope.launch { _events.emit(HidEvent.Disconnected("Pairing removed")) }
            }
            BluetoothDevice.BOND_BONDING -> {
                _connectionState.value = ConnectionState.PAIRING
            }
            BluetoothDevice.BOND_BONDED -> {
                _connectionState.value = ConnectionState.CONNECTED
                _isConnected.value = true
                _remoteDeviceName.value = device.name ?: device.address
                reconnectAttempt = 0
                scope.launch { _events.emit(HidEvent.Connected(device.name ?: device.address)) }
            }
        }
    }

    suspend fun attemptReconnect(): Boolean {
        val maxAttempts = 10
        while (reconnectAttempt < maxAttempts) {
            reconnectAttempt++
            val backoffMs = (RECONNECT_BASE_DELAY_MS * (1L shl (reconnectAttempt - 1)))
                .coerceAtMost(RECONNECT_MAX_DELAY_MS)
            Log.d(TAG, "Reconnect attempt $reconnectAttempt/$maxAttempts in ${backoffMs}ms")
            delay(backoffMs)

            val device = remoteDevice ?: return false
            try {
                connectToDevice(device)
                if (_isConnected.value) return true
            } catch (e: Exception) {
                Log.w(TAG, "Reconnect attempt $reconnectAttempt failed", e)
            }
        }
        reconnectAttempt = 0
        return false
    }

    sealed class HidEvent {
        data class Connected(val deviceName: String) : HidEvent()
        data class Disconnected(val reason: String) : HidEvent()
        data class Error(val message: String) : HidEvent()
        data class DeviceFound(val name: String, val address: String) : HidEvent()
        data class KeyPressed(val keycode: Int, val hidUsage: Int) : HidEvent()
        data class KeyReleased(val keycode: Int) : HidEvent()
        data class ModifierPressed(val modifier: Modifier) : HidEvent()
        data class ModifierReleased(val modifier: Modifier) : HidEvent()
    }

    companion object {
        private const val TAG = "BluetoothHidHandler"
        private const val REPORT_ID_KEYBOARD = 1
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
    }
}
