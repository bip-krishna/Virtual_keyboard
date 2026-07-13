package com.visionboard.utils.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

enum class LogLevel(val priority: Int, val tag: String) {
    VERBOSE(2, "V"),
    DEBUG(3, "D"),
    INFO(4, "I"),
    WARN(5, "W"),
    ERROR(6, "E"),
    OFF(100, "OFF");
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
) {
    fun format(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val timeStr = sdf.format(Date(timestamp))
        val throwableStr = if (throwable != null) {
            "\n${Log.getStackTraceString(throwable)}"
        } else {
            ""
        }
        return "[$timeStr] ${level.tag}/$tag: $message$throwableStr"
    }
}

@Singleton
class AppLogger @Inject constructor() {

    private val currentLogLevel = AtomicReference(LogLevel.DEBUG)

    private val logBuffer = ArrayDeque<LogEntry>(MAX_LOG_ENTRIES + 1)

    private val logLock = Any()

    fun v(tag: String, message: String) {
        log(LogLevel.VERBOSE, tag, message, null)
    }

    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message, null)
    }

    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message, null)
    }

    fun w(tag: String, message: String) {
        log(LogLevel.WARN, tag, message, null)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    fun setLogLevel(level: LogLevel) {
        currentLogLevel.set(level)
    }

    fun getLogLevel(): LogLevel = currentLogLevel.get()

    fun exportLogs(): String {
        return synchronized(logLock) {
            val sb = StringBuilder()
            sb.appendLine("=== VisionBoard Logs ===")
            sb.appendLine("Exported at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            sb.appendLine("Total entries: ${logBuffer.size}")
            sb.appendLine("Log level: ${currentLogLevel.get().name}")
            sb.appendLine("========================")
            sb.appendLine()

            for (entry in logBuffer) {
                sb.appendLine(entry.format())
            }

            sb.toString()
        }
    }

    fun exportLogs(level: LogLevel): String {
        return synchronized(logLock) {
            val sb = StringBuilder()
            val filteredEntries = logBuffer.filter { it.level.priority >= level.priority }

            sb.appendLine("=== VisionBoard Logs (>= ${level.name}) ===")
            sb.appendLine("Exported at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            sb.appendLine("Filtered entries: ${filteredEntries.size} / ${logBuffer.size}")
            sb.appendLine("=========================================")
            sb.appendLine()

            for (entry in filteredEntries) {
                sb.appendLine(entry.format())
            }

            sb.toString()
        }
    }

    fun exportLogs(tag: String): String {
        return synchronized(logLock) {
            val sb = StringBuilder()
            val filteredEntries = logBuffer.filter { it.tag == tag }

            sb.appendLine("=== VisionBoard Logs (tag=$tag) ===")
            sb.appendLine("Exported at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            sb.appendLine("Filtered entries: ${filteredEntries.size} / ${logBuffer.size}")
            sb.appendLine("====================================")
            sb.appendLine()

            for (entry in filteredEntries) {
                sb.appendLine(entry.format())
            }

            sb.toString()
        }
    }

    fun exportLogs(fromTimestamp: Long, toTimestamp: Long): String {
        return synchronized(logLock) {
            val sb = StringBuilder()
            val filteredEntries = logBuffer.filter {
                it.timestamp in fromTimestamp..toTimestamp
            }

            sb.appendLine("=== VisionBoard Logs (time range) ===")
            sb.appendLine("Exported at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            sb.appendLine("From: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(fromTimestamp))}")
            sb.appendLine("To: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(toTimestamp))}")
            sb.appendLine("Filtered entries: ${filteredEntries.size} / ${logBuffer.size}")
            sb.appendLine("=====================================")
            sb.appendLine()

            for (entry in filteredEntries) {
                sb.appendLine(entry.format())
            }

            sb.toString()
        }
    }

    fun getRecentLogs(count: Int = 50): List<LogEntry> {
        return synchronized(logLock) {
            logBuffer.toList().takeLast(count)
        }
    }

    fun getLogsByLevel(level: LogLevel): List<LogEntry> {
        return synchronized(logLock) {
            logBuffer.filter { it.level == level }
        }
    }

    fun getLogsByTag(tag: String): List<LogEntry> {
        return synchronized(logLock) {
            logBuffer.filter { it.tag == tag }
        }
    }

    fun clearLogs() {
        synchronized(logLock) {
            logBuffer.clear()
        }
    }

    fun getLogCount(): Int = logBuffer.size

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level.priority < currentLogLevel.get().priority) return

        if (!isReleaseBuild()) {
            when (level) {
                LogLevel.VERBOSE -> Log.v(tag, message)
                LogLevel.DEBUG -> Log.d(tag, message)
                LogLevel.INFO -> Log.i(tag, message)
                LogLevel.WARN -> Log.w(tag, message)
                LogLevel.ERROR -> {
                    if (throwable != null) {
                        Log.e(tag, message, throwable)
                    } else {
                        Log.e(tag, message)
                    }
                }
                LogLevel.OFF -> {}
            }
        }

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable,
        )

        synchronized(logLock) {
            if (logBuffer.size >= MAX_LOG_ENTRIES) {
                logBuffer.removeFirst()
            }
            logBuffer.addLast(entry)
        }
    }

    private fun isReleaseBuild(): Boolean {
        return try {
            val context = getAppContext()
            val appInfo = context?.applicationInfo
            appInfo != null && (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun getAppContext(): android.content.Context? {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
            currentApplicationMethod.invoke(null) as? android.content.Context
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 1000

        @Volatile
        private var instance: AppLogger? = null

        fun getInstance(): AppLogger {
            return instance ?: synchronized(this) {
                instance ?: AppLogger().also { instance = it }
            }
        }
    }
}
