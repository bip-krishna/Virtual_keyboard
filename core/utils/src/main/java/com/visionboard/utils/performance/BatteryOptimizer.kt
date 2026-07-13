package com.visionboard.utils.performance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BatteryOptimizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var highPerformanceModeActive = false
    private var batteryReceiver: BroadcastReceiver? = null

    fun requestHighPerformance() {
        if (highPerformanceModeActive) return

        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                if (!isIgnoringBattery) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }

        highPerformanceModeActive = true
    }

    fun releasePerformanceLock() {
        highPerformanceModeActive = false
    }

    fun isBatterySaverEnabled(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager.isPowerSaveMode
        } else {
            false
        }
    }

    fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    fun isBatteryCharging(): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return batteryManager?.isCharging ?: false
    }

    fun getBatteryTemperature(): Float {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return -1f
        val temperature = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return temperature / 10f
    }

    fun getChargingSource(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return "unknown"
        val plugged = batteryManager?.getIntProperty(4) ?: return "unknown"
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "unknown"
        }
    }

    fun registerBatteryReceiver(callback: (Int) -> Unit) {
        unregisterBatteryReceiver()

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = if (level >= 0 && scale > 0) {
                        (level * 100 / scale.toFloat()).toInt()
                    } else {
                        getBatteryLevel()
                    }
                    callback(batteryPct)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
    }

    fun unregisterBatteryReceiver() {
        batteryReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
        batteryReceiver = null
    }
}
