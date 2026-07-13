package com.visionboard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VisionBoardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeOpenCV()
        createNotificationChannels()
        initializeLogging()
    }

    private fun initializeOpenCV() {
        try {
            System.loadLibrary("opencv_java4")
            Log.i(TAG, "OpenCV initialized successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load OpenCV native library", e)
        }
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val keyboardChannel = NotificationChannel(
            KEYBOARD_SERVICE_CHANNEL_ID,
            "Keyboard Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when VisionBoard keyboard is active"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }

        val typingChannel = NotificationChannel(
            TYPING_SERVICE_CHANNEL_ID,
            "Typing Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when VisionBoard typing service is running"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }

        val cameraChannel = NotificationChannel(
            CAMERA_SERVICE_CHANNEL_ID,
            "Camera Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when VisionBoard camera processing is active"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }

        notificationManager.createNotificationChannels(
            listOf(keyboardChannel, typingChannel, cameraChannel)
        )

        Log.i(TAG, "Notification channels created")
    }

    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "VisionBoard v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) - Debug build")
        } else {
            Log.i(TAG, "VisionBoard v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) - Release build")
        }
    }

    companion object {
        private const val TAG = "VisionBoardApp"

        const val KEYBOARD_SERVICE_CHANNEL_ID = "keyboard_service"
        const val TYPING_SERVICE_CHANNEL_ID = "typing_service"
        const val CAMERA_SERVICE_CHANNEL_ID = "camera_service"
    }
}
