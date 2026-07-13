package com.visionboard.common.utils

import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class PerformanceMetrics(
    val fps: Float,
    val averageLatencyMs: Float,
    val maxLatencyMs: Float,
    val currentLatencyMs: Float,
    val cpuUsagePercent: Float,
    val memoryUsedMB: Float,
    val memoryAvailableMB: Float,
    val nativeMemoryMB: Float,
    val gpuUsagePercent: Float,
    val batteryLevel: Int,
    val isBatteryCharging: Boolean,
    val frameDropCount: Int,
    val totalFramesRendered: Long,
)

class PerformanceMonitor private constructor() {

    private val isRunning = AtomicBoolean(false)

    private val frameTimestamps = LongArray(60)
    private var frameIndex = 0
    private var frameCount = 0

    private var lastFrameStartNanos = 0L
    private var totalFramesRendered = 0L
    private var frameDropCount = 0

    private val cpuPreviousIdle = AtomicLong(0)
    private val cpuPreviousTotal = AtomicLong(0)

    @Volatile
    private var cachedMetrics = PerformanceMetrics(
        fps = 0f,
        averageLatencyMs = 0f,
        maxLatencyMs = 0f,
        currentLatencyMs = 0f,
        cpuUsagePercent = 0f,
        memoryUsedMB = 0f,
        memoryAvailableMB = 0f,
        nativeMemoryMB = 0f,
        gpuUsagePercent = 0f,
        batteryLevel = -1,
        isBatteryCharging = false,
        frameDropCount = 0,
        totalFramesRendered = 0,
    )

    private var context: Context? = null

    fun initialize(appContext: Context) {
        context = appContext.applicationContext
        frameTimestamps.fill(0L)
    }

    fun startFrame() {
        if (!isRunning.get()) {
            isRunning.set(true)
            frameTimestamps.fill(0L)
            frameIndex = 0
            frameCount = 0
            totalFramesRendered = 0
            frameDropCount = 0
        }
        lastFrameStartNanos = System.nanoTime()
    }

    fun endFrame() {
        if (lastFrameStartNanos == 0L) return

        val now = System.nanoTime()
        val latencyMs = ((now - lastFrameStartNanos) / 1_000_000.0).toFloat()
        lastFrameStartNanos = 0L
        totalFramesRendered++

        frameTimestamps[frameIndex % 60] = now
        frameIndex++
        if (frameCount < 60) frameCount++

        if (latencyMs > 100f) {
            frameDropCount++
        }

        cachedMetrics = computeMetrics(latencyMs)
    }

    fun getMetrics(): PerformanceMetrics = cachedMetrics

    fun reset() {
        isRunning.set(false)
        frameTimestamps.fill(0L)
        frameIndex = 0
        frameCount = 0
        totalFramesRendered = 0
        frameDropCount = 0
        lastFrameStartNanos = 0L
    }

    private fun computeMetrics(currentLatencyMs: Float): PerformanceMetrics {
        val fps = computeFps()
        val (avgLatency, maxLatency) = computeLatencyStats()
        val cpuUsage = readCpuUsage()
        val (memUsed, memAvail, nativeMem) = readMemoryUsage()
        val gpuUsage = estimateGpuUsage()
        val (batteryLevel, isCharging) = readBatteryInfo()

        return PerformanceMetrics(
            fps = fps,
            averageLatencyMs = avgLatency,
            maxLatencyMs = maxLatency,
            currentLatencyMs = currentLatencyMs,
            cpuUsagePercent = cpuUsage,
            memoryUsedMB = memUsed,
            memoryAvailableMB = memAvail,
            nativeMemoryMB = nativeMem,
            gpuUsagePercent = gpuUsage,
            batteryLevel = batteryLevel,
            isBatteryCharging = isCharging,
            frameDropCount = frameDropCount,
            totalFramesRendered = totalFramesRendered,
        )
    }

    private fun computeFps(): Float {
        if (frameCount < 2) return 0f

        val now = System.nanoTime()
        val windowSize = frameCount.coerceAtMost(60)
        var oldestTimestamp = now
        var count = 0

        for (i in 0 until windowSize) {
            val idx = (frameIndex - 1 - i).coerceAtLeast(0) % 60
            val ts = frameTimestamps[idx]
            if (ts > 0L) {
                if (ts < oldestTimestamp) oldestTimestamp = ts
                count++
            }
        }

        if (count < 2 || oldestTimestamp == now) return 0f

        val elapsedSeconds = (now - oldestTimestamp) / 1_000_000_000.0f
        return if (elapsedSeconds > 0f) (count - 1) / elapsedSeconds else 0f
    }

    private fun computeLatencyStats(): Pair<Float, Float> {
        if (frameCount == 0) return 0f to 0f

        val windowSize = frameCount.coerceAtMost(60)
        var sum = 0.0
        var maxLatency = 0f
        var validCount = 0

        for (i in 1 until windowSize) {
            val idx1 = (frameIndex - 1 - i).coerceAtLeast(0) % 60
            val idx2 = (frameIndex - i).coerceAtLeast(0) % 60
            val ts1 = frameTimestamps[idx1]
            val ts2 = frameTimestamps[idx2]
            if (ts1 > 0L && ts2 > 0L && ts2 > ts1) {
                val diff = ((ts2 - ts1) / 1_000_000.0).toFloat()
                sum += diff
                if (diff > maxLatency) maxLatency = diff
                validCount++
            }
        }

        val avgLatency = if (validCount > 0) (sum / validCount).toFloat() else 0f
        return avgLatency to maxLatency
    }

    private fun readCpuUsage(): Float {
        return try {
            val file = File("/proc/stat")
            if (!file.exists()) return 0f

            val reader = RandomAccessFile(file, "r")
            val line = reader.readLine()
            reader.close()

            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 5) return 0f

            val idle = parts[4].toLong()
            val total = parts.drop(1).take(7).sumOf { it.toLong() }

            val prevIdle = cpuPreviousIdle.get()
            val prevTotal = cpuPreviousTotal.get()

            cpuPreviousIdle.set(idle)
            cpuPreviousTotal.set(total)

            if (prevTotal == 0L) return 0f

            val totalDiff = total - prevTotal
            val idleDiff = idle - prevIdle

            if (totalDiff == 0L) return 0f
            ((totalDiff - idleDiff).toFloat() / totalDiff * 100f).coerceIn(0f, 100f)
        } catch (_: Exception) {
            0f
        }
    }

    private fun readMemoryUsage(): Triple<Float, Float, Float> {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024).toFloat()
        val totalMemory = runtime.totalMemory() / (1024 * 1024).toFloat()
        val freeMemory = runtime.freeMemory() / (1024 * 1024).toFloat()
        val usedMemory = totalMemory - freeMemory

        val nativeHeapSize = Debug.getNativeHeapSize() / (1024 * 1024).toFloat()

        return Triple(usedMemory, maxMemory - usedMemory, nativeHeapSize)
    }

    private fun estimateGpuUsage(): Float {
        return try {
            val glInfo = StringBuilder()
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "gpu"))
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    glInfo.appendLine(line)
                }
            }
            process.waitFor()

            val output = glInfo.toString()
            val gpuMatch = Regex("GPU\\s+Usage:\\s+(\\d+)%").find(output)
            gpuMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        } catch (_: Exception) {
            0f
        }
    }

    private fun readBatteryInfo(): Pair<Int, Boolean> {
        val ctx = context ?: return -1 to false
        return try {
            val batteryManager = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = batteryManager?.isCharging ?: false
            level to isCharging
        } catch (_: Exception) {
            -1 to false
        }
    }

    companion object {
        val instance: PerformanceMonitor by lazy { PerformanceMonitor() }

        fun getInstance(): PerformanceMonitor = instance
    }
}
