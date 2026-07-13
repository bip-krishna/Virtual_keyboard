package com.visionboard.ui.pairing

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionboard.bluetooth.handler.BluetoothHidHandler
import com.visionboard.bluetooth.handler.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean = false,
)

sealed class PairingUiState {
    data object Idle : PairingUiState()
    data class Scanning(val devices: List<DiscoveredDevice>) : PairingUiState()
    data class Pairing(val device: DiscoveredDevice) : PairingUiState()
    data class Connected(val device: DiscoveredDevice) : PairingUiState()
    data class Error(val message: String) : PairingUiState()
}

data class ConnectionQuality(
    val rssi: Int = -100,
    val signalBars: Int = 0,
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothHidHandler: BluetoothHidHandler,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private val _connectionQuality = MutableStateFlow(ConnectionQuality())
    val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val discoveredDevices = mutableSetOf<DiscoveredDevice>()

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        val name = device.name ?: device.address
                        if (name.isNotBlank()) {
                            discoveredDevices.add(
                                DiscoveredDevice(
                                    name = name,
                                    address = device.address,
                                    isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
                                )
                            )
                            _uiState.update { state ->
                                if (state is PairingUiState.Scanning) {
                                    state.copy(devices = discoveredDevices.toList())
                                } else state
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    val current = _uiState.value
                    if (current is PairingUiState.Scanning) {
                        _uiState.value = current
                    }
                }
            }
        }
    }

    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice? =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            if (device == null) return

            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val bondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR
                    )
                    val prevState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR
                    )

                    when (bondState) {
                        BluetoothDevice.BOND_BONDING -> {
                            _uiState.update {
                                PairingUiState.Pairing(
                                    DiscoveredDevice(
                                        name = device.name ?: device.address,
                                        address = device.address,
                                    )
                                )
                            }
                        }
                        BluetoothDevice.BOND_BONDED -> {
                            val d = DiscoveredDevice(
                                name = device.name ?: device.address,
                                address = device.address,
                                isBonded = true,
                            )
                            _uiState.value = PairingUiState.Connected(d)
                        }
                        BluetoothDevice.BOND_NONE -> {
                            if (prevState == BluetoothDevice.BOND_BONDING) {
                                _uiState.value = PairingUiState.Error("Pairing rejected or failed")
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        observeBluetoothConnection()
    }

    private fun observeBluetoothConnection() {
        viewModelScope.launch {
            bluetoothHidHandler.connectionState.collect { connState ->
                when (connState) {
                    ConnectionState.DISCONNECTED -> {
                        if (_uiState.value !is PairingUiState.Idle &&
                            _uiState.value !is PairingUiState.Error
                        ) {
                            _uiState.value = PairingUiState.Idle
                        }
                    }
                    ConnectionState.ERROR -> {
                        _uiState.value = PairingUiState.Error("Connection lost")
                    }
                    else -> {}
                }
            }
        }
    }

    fun startScanning() {
        if (!ensureBluetoothEnabled()) {
            _uiState.value = PairingUiState.Error("Please enable Bluetooth")
            return
        }

        discoveredDevices.clear()
        _uiState.value = PairingUiState.Scanning(devices = emptyList())

        try {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(discoveryReceiver, filter)

            val pairingFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(pairingReceiver, pairingFilter)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothAdapter?.startDiscovery()
            } else {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.startDiscovery()
            }
        } catch (e: SecurityException) {
            _uiState.value = PairingUiState.Error("Bluetooth permission denied")
        }
    }

    fun stopScanning() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            context.unregisterReceiver(discoveryReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    fun connectToDevice(device: DiscoveredDevice) {
        viewModelScope.launch {
            _uiState.value = PairingUiState.Pairing(device)

            try {
                val bluetoothManager =
                    context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                val adapter = bluetoothManager?.adapter ?: run {
                    _uiState.value = PairingUiState.Error("Bluetooth not available")
                    return@launch
                }

                val remoteDevice = adapter.getRemoteDevice(device.address) ?: run {
                    _uiState.value = PairingUiState.Error("Device not found")
                    return@launch
                }

                if (remoteDevice.bondState != BluetoothDevice.BOND_BONDED) {
                    remoteDevice.createBond()
                    delay(3000L)
                }

                if (remoteDevice.bondState == BluetoothDevice.BOND_BONDED ||
                    remoteDevice.bondState == BluetoothDevice.BOND_BONDING
                ) {
                    bluetoothHidHandler.handleDeviceFound(remoteDevice)
                    bluetoothHidHandler.handlePairingStateChange(
                        remoteDevice,
                        remoteDevice.bondState,
                    )
                }

                if (remoteDevice.bondState == BluetoothDevice.BOND_BONDED) {
                    _uiState.value = PairingUiState.Connected(
                        device.copy(isBonded = true)
                    )
                } else {
                    _uiState.value = PairingUiState.Error("Pairing timed out")
                }
            } catch (e: SecurityException) {
                _uiState.value = PairingUiState.Error("Bluetooth permission required")
            } catch (e: Exception) {
                _uiState.value = PairingUiState.Error(
                    "Connection failed: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun disconnect() {
        try {
            stopScanning()
            context.unregisterReceiver(pairingReceiver)
        } catch (_: IllegalArgumentException) {
        }
        bluetoothHidHandler.disconnect()
        _uiState.value = PairingUiState.Idle
        _connectionQuality.value = ConnectionQuality()
    }

    fun skipPairing() {
        try {
            stopScanning()
        } catch (_: Exception) {}
        bluetoothHidHandler.disconnect()
        _uiState.value = PairingUiState.Idle
    }

    private fun ensureBluetoothEnabled(): Boolean {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        return bluetoothAdapter?.isEnabled == true
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(discoveryReceiver)
            context.unregisterReceiver(pairingReceiver)
        } catch (_: IllegalArgumentException) {
        }
        bluetoothHidHandler.disconnect()
    }
}
