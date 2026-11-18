package com.visionboard.bluetooth.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.visionboard.bluetooth.handler.BluetoothHidHandler
import com.visionboard.bluetooth.handler.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothHidService : Service() {

    @Inject lateinit var hidHandler: BluetoothHidHandler
    @Inject lateinit var keyTranslator: com.visionboard.bluetooth.hid.HidKeyTranslator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var isServiceRunning = false

    private val binder by lazy {
        BluetoothHidBinder(this, hidHandler, keyTranslator)
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { hidHandler.handleDeviceFound(it) }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE,
                    )
                    device?.let { hidHandler.handlePairingStateChange(it, bondState) }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.STATE_OFF,
                    )
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            hidHandler.disconnect()
                            _isRunning.value = false
                        }
                        BluetoothAdapter.STATE_ON -> {
                            startConnection()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerBluetoothReceiver()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            performStop()
            return START_NOT_STICKY
        }

        if (!isServiceRunning) {
            startForeground(NOTIFICATION_ID, buildNotification("Bluetooth HID active"))
            isServiceRunning = true
            _isRunning.value = true
        }
        if (intent?.action == ACTION_CONNECT) startConnection()
        return START_STICKY
    }

    override fun onDestroy() {
        performStop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scope.launch {
            hidHandler.disconnect()
        }
        super.onTaskRemoved(rootIntent)
    }

    fun startForegroundService() {
        val intent = Intent(this, BluetoothHidService::class.java).apply {
            action = ACTION_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun performStop() {
        reconnectJob?.cancel()
        scope.launch {
            hidHandler.disconnect()
            unregisterBluetoothReceiver()
        }
        isServiceRunning = false
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun startConnection() {
        if (!isServiceRunning) {
            startForegroundService()
        }
        scope.launch {
            hidHandler.connect().collect { state ->
                updateNotification(state.name.lowercase().replace('_', ' '))
                when (state) {
                    ConnectionState.CONNECTED -> {
                        reconnectJob?.cancel()
                    }
                    ConnectionState.ERROR, ConnectionState.DISCONNECTED -> {
                        startReconnect()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.d(TAG, "Starting reconnect loop")
            var attempt = 0
            while (attempt < MAX_RECONNECT_ATTEMPTS && _isRunning.value) {
                attempt++
                val backoffMs = (RECONNECT_BASE_DELAY_MS * (1L shl (attempt - 1)))
                    .coerceAtMost(RECONNECT_MAX_DELAY_MS)
                Log.d(TAG, "Reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS in ${backoffMs}ms")
                updateNotification("Reconnecting… ($attempt/$MAX_RECONNECT_ATTEMPTS)")
                delay(backoffMs)

                if (hidHandler.attemptReconnect()) {
                    Log.d(TAG, "Reconnected successfully")
                    updateNotification("Connected")
                    return@launch
                }
            }
            Log.w(TAG, "Failed to reconnect after $MAX_RECONNECT_ATTEMPTS attempts")
            updateNotification("Connection lost")
        }
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
    }

    private fun unregisterBluetoothReceiver() {
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: IllegalArgumentException) {
            // already unregistered
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Bluetooth HID Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Manages Bluetooth HID keyboard connection"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val stopIntent = Intent(this, BluetoothHidService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("VisionBoard Keyboard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "BluetoothHidService"
        private const val NOTIFICATION_CHANNEL_ID = "visionboard_bt_hid"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "com.visionboard.bluetooth.ACTION_STOP"
        private const val ACTION_CONNECT = "com.visionboard.bluetooth.ACTION_CONNECT"
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
    }
}
