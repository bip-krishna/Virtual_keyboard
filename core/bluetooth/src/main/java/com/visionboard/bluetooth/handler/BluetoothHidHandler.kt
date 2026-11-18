package com.visionboard.bluetooth.handler

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.visionboard.bluetooth.hid.HidKeyTranslator
import com.visionboard.bluetooth.hid.HidReportDescriptor
import com.visionboard.bluetooth.hid.Modifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
@SuppressLint("MissingPermission")
class BluetoothHidHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyTranslator: HidKeyTranslator,
    private val reportDescriptor: HidReportDescriptor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _remoteDeviceName = MutableStateFlow<String?>(null)
    val remoteDeviceName: StateFlow<String?> = _remoteDeviceName.asStateFlow()

    private val _events = MutableSharedFlow<HidEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<HidEvent> = _events.asSharedFlow()

    private var remoteDevice: BluetoothDevice? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var profileRequested = false
    private var modifiers = emptySet<Modifier>()
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (profileRequested) {
                if (registered) {
                    _connectionState.value = ConnectionState.WAITING_FOR_PAIRING
                } else {
                    clearConnection(ConnectionState.ERROR)
                    closeProfile()
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (!profileRequested) return
            when (state) {
                BluetoothProfile.STATE_CONNECTING -> _connectionState.value = ConnectionState.PAIRING
                BluetoothProfile.STATE_CONNECTED -> {
                    val name = device.name ?: device.address
                    remoteDevice = device
                    _remoteDeviceName.value = name
                    _connectionState.value = ConnectionState.CONNECTED
                    _isConnected.value = true
                    _events.tryEmit(HidEvent.Connected(name))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (remoteDevice == device || remoteDevice == null) {
                        clearConnection(ConnectionState.WAITING_FOR_PAIRING)
                        _events.tryEmit(HidEvent.Disconnected("Bluetooth host disconnected"))
                    }
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            scope.launch {
                val device = proxy as? BluetoothHidDevice ?: return@launch
                if (!profileRequested) {
                    adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, device)
                    return@launch
                }
                hidDevice = device
                try {
                    val settings = BluetoothHidDeviceAppSdpSettings(
                        "VisionBoard", "Camera keyboard", "VisionBoard",
                        BluetoothHidDevice.SUBCLASS1_KEYBOARD, reportDescriptor.buildKeyboardDescriptor(),
                    )
                    if (!device.registerApp(settings, null, null, ContextCompat.getMainExecutor(context), callback)) {
                        closeProfile()
                        clearConnection(ConnectionState.ERROR)
                    }
                } catch (error: SecurityException) {
                    Log.w(TAG, "Bluetooth HID registration denied", error)
                    closeProfile()
                    clearConnection(ConnectionState.ERROR)
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            scope.launch {
                hidDevice = null
                profileRequested = false
                clearConnection(ConnectionState.DISCONNECTED)
            }
        }
    }

    fun connect(): Flow<ConnectionState> = flow {
        emit(withContext(Dispatchers.Main.immediate) {
            if (!profileRequested) {
                try {
                    profileRequested = adapter?.isEnabled == true &&
                        adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
                } catch (error: SecurityException) {
                    Log.w(TAG, "Bluetooth permission denied", error)
                }
                _connectionState.value =
                    if (profileRequested) ConnectionState.DISCOVERABLE else ConnectionState.ERROR
            }
            _connectionState.value
        })
    }

    fun disconnect() {
        scope.launch {
            val device = hidDevice
            try {
                remoteDevice?.let { device?.disconnect(it) }
                device?.unregisterApp()
            } catch (error: SecurityException) {
                Log.w(TAG, "Could not unregister Bluetooth HID", error)
            } finally {
                closeProfile()
                clearConnection(ConnectionState.DISCONNECTED)
            }
        }
    }

    fun sendKeyTap(keycode: Int) {
        scope.launch {
            val usage = keyTranslator.translateKeycodeToHidUsage(keycode)
            if (usage == 0) return@launch
            if (sendHidReport(usage)) {
                _events.tryEmit(HidEvent.KeyPressed(keycode, usage))
                if (sendHidReport(0)) _events.tryEmit(HidEvent.KeyReleased(keycode))
            }
        }
    }

    fun sendKeyPress(keycode: Int) {
        scope.launch {
            val usage = keyTranslator.translateKeycodeToHidUsage(keycode)
            if (usage != 0 && sendHidReport(usage)) _events.tryEmit(HidEvent.KeyPressed(keycode, usage))
        }
    }

    fun sendKeyRelease(keycode: Int) {
        scope.launch {
            if (sendHidReport(0)) _events.tryEmit(HidEvent.KeyReleased(keycode))
        }
    }

    fun sendModifierPress(modifier: Modifier) {
        scope.launch {
            modifiers = modifiers + modifier
            if (sendHidReport(0)) _events.tryEmit(HidEvent.ModifierPressed(modifier))
        }
    }

    fun sendModifierRelease(modifier: Modifier) {
        scope.launch {
            modifiers = modifiers - modifier
            if (sendHidReport(0)) _events.tryEmit(HidEvent.ModifierReleased(modifier))
        }
    }

    fun isConnected(): Boolean = _isConnected.value

    fun getConnectionState(): ConnectionState = _connectionState.value

    fun getRemoteDeviceName(): String? = _remoteDeviceName.value

    private fun sendHidReport(usage: Int): Boolean {
        val device = remoteDevice ?: return false
        val bytes = reportDescriptor.buildKeyReport(usage)
        bytes[0] = reportDescriptor.buildModifierByte(modifiers)
        return try {
            hidDevice?.sendReport(device, KEYBOARD_REPORT_ID, bytes) == true
        } catch (error: SecurityException) {
            Log.w(TAG, "Bluetooth report denied", error)
            false
        }
    }

    private fun clearConnection(state: ConnectionState) {
        remoteDevice = null
        modifiers = emptySet()
        _remoteDeviceName.value = null
        _isConnected.value = false
        _connectionState.value = state
    }

    private fun closeProfile() {
        profileRequested = false
        hidDevice?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hidDevice = null
    }

    fun handleDeviceFound(device: BluetoothDevice) {
        _events.tryEmit(HidEvent.DeviceFound(device.name ?: device.address, device.address))
    }

    fun handlePairingStateChange(device: BluetoothDevice, bondState: Int) {
        if (device == remoteDevice && bondState == BluetoothDevice.BOND_NONE) {
            clearConnection(ConnectionState.DISCONNECTED)
        }
    }

    suspend fun attemptReconnect(): Boolean = withContext(Dispatchers.Main.immediate) {
        remoteDevice?.let { hidDevice?.connect(it) } == true
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
        private const val KEYBOARD_REPORT_ID = 0
    }
}
