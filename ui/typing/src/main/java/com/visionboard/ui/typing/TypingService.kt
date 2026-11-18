package com.visionboard.ui.typing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.visionboard.bluetooth.handler.BluetoothHidHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TypingServiceState(
    val isRunning: Boolean = false,
    val wpm: Float = 0f,
    val accuracy: Float = 100f,
    val totalKeys: Int = 0,
    val bluetoothConnected: Boolean = false,
)

@AndroidEntryPoint
class TypingService : Service() {

    companion object {
        private const val TAG = "TypingService"
        private const val NOTIFICATION_CHANNEL_ID = "visionboard_typing_channel"
        private const val NOTIFICATION_ID = 2002
        const val ACTION_START = "com.visionboard.ui.typing.ACTION_START"
        const val ACTION_STOP = "com.visionboard.ui.typing.ACTION_STOP"
        const val ACTION_UPDATE_STATS = "com.visionboard.ui.typing.ACTION_UPDATE_STATS"
    }

    @Inject
    lateinit var bluetoothHidHandler: BluetoothHidHandler

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _serviceState = MutableStateFlow(TypingServiceState())
    val serviceState: StateFlow<TypingServiceState> = _serviceState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "TypingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startTypingService()
            }
            ACTION_STOP -> {
                stopTypingService()
            }
            ACTION_UPDATE_STATS -> {
                val wpm = intent.getFloatExtra("wpm", 0f)
                val accuracy = intent.getFloatExtra("accuracy", 100f)
                val totalKeys = intent.getIntExtra("totalKeys", 0)
                updateStats(wpm, accuracy, totalKeys)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopTypingService()
        serviceScope.cancel()
        Log.i(TAG, "TypingService destroyed")
    }

    private fun startTypingService() {
        _serviceState.update { it.copy(isRunning = true) }

        val notification = buildNotification(
            title = "VisionBoard Typing",
            content = "Typing active — tap to open",
        )
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            bluetoothHidHandler.isConnected.collectLatest { connected ->
                _serviceState.update { it.copy(bluetoothConnected = connected) }
                updateNotification()
            }
        }

        Log.i(TAG, "Typing service started")
    }

    private fun stopTypingService() {
        _serviceState.update {
            it.copy(
                isRunning = false,
                wpm = 0f,
                totalKeys = 0,
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Typing service stopped")
    }

    private fun updateStats(wpm: Float, accuracy: Float, totalKeys: Int) {
        _serviceState.update {
            it.copy(wpm = wpm, accuracy = accuracy, totalKeys = totalKeys)
        }
        updateNotification()
    }

    private fun updateNotification() {
        val state = _serviceState.value
        val notification = buildNotification(
            title = "VisionBoard Typing Active",
            content = "WPM: ${String.format("%.1f", state.wpm)} | " +
                "Accuracy: ${String.format("%.0f%%", state.accuracy)} | " +
                "Keys: ${state.totalKeys}" +
                if (state.bluetoothConnected) " | Bluetooth" else "",
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.let { intent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TypingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Typing Service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "VisionBoard background typing pipeline"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
