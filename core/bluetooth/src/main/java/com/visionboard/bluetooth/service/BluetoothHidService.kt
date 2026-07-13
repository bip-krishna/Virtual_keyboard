package com.visionboard.bluetooth.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import com.visionboard.bluetooth.hid.HidReportDescriptor
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
    @Inject lateinit var reportDescriptor: HidReportDescriptor
    @Inject lateinit var keyTranslator: com.visionboard.bluetooth.hid.HidKeyTranslator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var isServiceRunning = false

    private val binder by lazy {
        BluetoothHidBinder(this, hidHandler, keyTranslator)
    }

    private val bluetoothManager: BluetoothManager? by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private var hidDevice: Any? = null
    private var hidHostProxy: BluetoothProfile? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val hidServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "Bluetooth HID Host profile connected")
            hidHostProxy = proxy
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "Bluetooth HID Host profile disconnected")
            hidHostProxy = null
        }
    }

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
                            initializeHidDevice()
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
        connectHidHostProfile()
        initializeHidDevice()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                performStop()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                startConnection()
            }
        }

        if (!isServiceRunning) {
            startForeground(NOTIFICATION_ID, buildNotification("Bluetooth HID active"))
            isServiceRunning = true
            _isRunning.value = true
        }

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
            unregisterSdpRecord()
            unregisterBluetoothReceiver()
            disconnectHidHostProfile()
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
                updateNotification(state)
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

    private fun initializeHidDevice() {
        try {
            val adapter = bluetoothAdapter ?: run {
                Log.e(TAG, "BluetoothAdapter not available")
                return
            }

            val getHidDeviceMethod = try {
                adapter.javaClass.getMethod("getHidDevice")
            } catch (e: NoSuchMethodException) {
                Log.e(TAG, "getHidDevice not found — hidden API unavailable", e)
                trySystemServiceHidDevice()
                return
            }

            hidDevice = getHidDeviceMethod.invoke(adapter)
            if (hidDevice == null) {
                Log.w(TAG, "BluetoothHidDevice returned null, trying system service")
                trySystemServiceHidDevice()
                return
            }

            registerSdpRecordViaReflection()
            Log.d(TAG, "BluetoothHidDevice initialized and SDP record registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HID device", e)
            trySystemServiceHidDevice()
        }
    }

    private fun trySystemServiceHidDevice() {
        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "bluetooth_manager") as? IBinder
                ?: return Log.w(TAG, "bluetooth_manager binder not found")

            val stubClass = Class.forName("android.bluetooth.IBluetoothManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val btManager = asInterface.invoke(null, binder)

            val method = btManager.javaClass.getMethod("getHidDevice")
            hidDevice = method.invoke(btManager)
            Log.d(TAG, "Obtained HID device via system service: ${hidDevice != null}")

            if (hidDevice != null) {
                registerSdpRecordViaReflection()
            }
        } catch (e: Exception) {
            Log.e(TAG, "System service HID fallback failed", e)
        }
    }

    private fun registerSdpRecordViaReflection() {
        val device = hidDevice ?: return
        val descriptor = reportDescriptor.buildKeyboardDescriptor()

        try {
            val registerMethod = device.javaClass.getMethod(
                "registerApp",
                String::class.java,
                String::class.java,
                String::class.java,
                ByteArray::class.java,
                BluetoothProfile.ServiceListener::class.java,
            )
            registerMethod.invoke(
                device,
                SDP_DEVICE_NAME,
                SDP_DESCRIPTION,
                SDP_PROVIDER,
                descriptor,
                hidServiceListener,
            )
            Log.d(TAG, "HID app registered via registerApp")
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "registerApp not found, trying alternative registration")
            registerSdpAlternative()
        } catch (e: Exception) {
            Log.e(TAG, "SDP registration failed", e)
            registerSdpAlternative()
        }
    }

    private fun registerSdpAlternative() {
        val device = hidDevice ?: return
        val descriptor = reportDescriptor.buildKeyboardDescriptor()

        try {
            val connectInputHost = device.javaClass.getMethod(
                "connectInputHost",
                BluetoothProfile.ServiceListener::class.java,
            )
            connectInputHost.invoke(device, hidServiceListener)
            Log.d(TAG, "Registered via connectInputHost")
        } catch (e: Exception) {
            Log.w(TAG, "connectInputHost failed: ${e.message}")
        }

        try {
            val setDescriptor = device.javaClass.getMethod("setReportDescriptor", ByteArray::class.java)
            setDescriptor.invoke(device, descriptor)
            Log.d(TAG, "Set report descriptor via alternative method")
        } catch (e: Exception) {
            Log.w(TAG, "setReportDescriptor failed: ${e.message}")
        }
    }

    private fun unregisterSdpRecord() {
        val device = hidDevice ?: return
        try {
            val method = device.javaClass.getMethod("unregisterApp")
            method.invoke(device)
            Log.d(TAG, "HID app unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister HID app", e)
        }
    }

    private fun connectHidHostProfile() {
        try {
            bluetoothAdapter?.getProfileProxy(this, hidServiceListener, BluetoothProfile.HID_HOST)
            Log.d(TAG, "Requesting HID Host profile proxy")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect HID Host profile", e)
        }
    }

    private fun disconnectHidHostProfile() {
        try {
            hidHostProxy?.let { proxy ->
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_HOST, proxy)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close HID Host profile", e)
        }
        hidHostProxy = null
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
        private const val SDP_DEVICE_NAME = "VisionBoard Keyboard"
        private const val SDP_DESCRIPTION = "VisionBoard virtual keyboard HID device"
        private const val SDP_PROVIDER = "com.visionboard"
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
    }
}
