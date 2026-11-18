package com.visionboard.utils.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class MemoryState(
    val usedMb: Int,
    val maxMb: Int,
    val usagePercent: Float,
    val isLow: Boolean,
) {
    companion object {
        val UNKNOWN = MemoryState(usedMb = 0, maxMb = 0, usagePercent = 0f, isLow = false)
    }
}

@Singleton
class MemoryGuard @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isMonitoring = AtomicBoolean(false)
    private val pressureCallbacks = ConcurrentHashMap<Float, MutableList<() -> Unit>>()

    private val _memoryState = MutableStateFlow(MemoryState.UNKNOWN)
    val memoryState: StateFlow<MemoryState> = _memoryState.asStateFlow()

    fun startMonitoring(intervalMs: Long = 5000L): Flow<MemoryState> = flow {
        isMonitoring.set(true)

        while (isMonitoring.get() && currentCoroutineContext().isActive) {
            val state = getMemoryState()
            _memoryState.value = state

            if (state.isLow) {
                triggerPressureCallbacks(state.usagePercent)
            }

            emit(state)
            delay(intervalMs)
        }

        isMonitoring.set(false)
    }.flowOn(Dispatchers.IO)

    fun getMemoryState(): MemoryState {
        val runtime = Runtime.getRuntime()
        val maxMemoryBytes = runtime.maxMemory()
        val totalMemoryBytes = runtime.totalMemory()
        val freeMemoryBytes = runtime.freeMemory()
        val usedMemoryBytes = totalMemoryBytes - freeMemoryBytes

        val maxMemoryMb = (maxMemoryBytes / (1024 * 1024)).toInt()
        val usedMemoryMb = (usedMemoryBytes / (1024 * 1024)).toInt()
        val usagePercent = if (maxMemoryBytes > 0) {
            (usedMemoryBytes.toFloat() / maxMemoryBytes * 100f)
        } else {
            0f
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val isLow = if (memoryInfo.lowMemory) {
            true
        } else {
            val thresholdPercent = 85f
            usagePercent >= thresholdPercent
        }

        val nativeHeapMb = (Debug.getNativeHeapAllocatedSize() / (1024 * 1024)).toInt()

        return MemoryState(
            usedMb = usedMemoryMb + nativeHeapMb,
            maxMb = maxMemoryMb,
            usagePercent = usagePercent.coerceIn(0f, 100f),
            isLow = isLow,
        )
    }

    fun forceGc() {
        val beforeState = getMemoryState()

        System.gc()
        System.runFinalization()

        val afterState = getMemoryState()

        val freedMb = beforeState.usedMb - afterState.usedMb
        if (freedMb > 0) {
            Log.d(TAG, "GC freed ~${freedMb}MB (${beforeState.usedMb}MB -> ${afterState.usedMb}MB)")
        }

        _memoryState.value = afterState
    }

    fun registerPressureCallback(threshold: Float, callback: () -> Unit) {
        val clampedThreshold = threshold.coerceIn(0f, 100f)
        pressureCallbacks.getOrPut(clampedThreshold) { mutableListOf() }.add(callback)
    }

    fun unregisterPressureCallbacks(threshold: Float) {
        pressureCallbacks.remove(threshold)
    }

    fun clearAllCallbacks() {
        pressureCallbacks.clear()
    }

    fun getDetailedMemoryInfo(): DetailedMemoryInfo {
        val runtime = Runtime.getRuntime()
        val maxMemoryBytes = runtime.maxMemory()
        val totalMemoryBytes = runtime.totalMemory()
        val freeMemoryBytes = runtime.freeMemory()

        val dalvikMax = maxMemoryBytes / (1024 * 1024)
        val dalvikAllocated = totalMemoryBytes / (1024 * 1024)
        val dalvikFree = freeMemoryBytes / (1024 * 1024)

        val nativeHeapSize = Debug.getNativeHeapSize() / (1024 * 1024)
        val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        val nativeHeapFree = Debug.getNativeHeapFreeSize() / (1024 * 1024)

        val nativeHeap = Debug.getNativeHeapSize()
        val nativeAllocated = Debug.getNativeHeapAllocatedSize()
        val nativeFree = Debug.getNativeHeapFreeSize()

        val pssInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(pssInfo)

        return DetailedMemoryInfo(
            dalvikMaxMb = dalvikMax.toInt(),
            dalvikAllocatedMb = dalvikAllocated.toInt(),
            dalvikFreeMb = dalvikFree.toInt(),
            nativeHeapSizeMb = nativeHeapSize.toInt(),
            nativeHeapAllocatedMb = nativeHeapAllocated.toInt(),
            nativeHeapFreeMb = nativeHeapFree.toInt(),
            totalPssKb = pssInfo.totalPss,
            totalPrivateDirtyKb = pssInfo.totalPrivateDirty,
            totalSharedDirtyKb = pssInfo.totalSharedDirty,
        )
    }

    fun getMemoryPressureLevel(): MemoryPressureLevel {
        val state = getMemoryState()
        return when {
            state.usagePercent >= 90f -> MemoryPressureLevel.CRITICAL
            state.usagePercent >= 80f -> MemoryPressureLevel.HIGH
            state.usagePercent >= 65f -> MemoryPressureLevel.MODERATE
            else -> MemoryPressureLevel.NORMAL
        }
    }

    private fun triggerPressureCallbacks(currentUsage: Float) {
        for ((threshold, callbacks) in pressureCallbacks) {
            if (currentUsage >= threshold) {
                callbacks.forEach { callback ->
                    try {
                        callback()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error in memory pressure callback", e)
                    }
                }
            }
        }
    }

    fun stopMonitoring() {
        isMonitoring.set(false)
    }

    fun destroy() {
        stopMonitoring()
        clearAllCallbacks()
        scope.cancel()
    }

    companion object {
        private const val TAG = "MemoryGuard"
    }
}

data class DetailedMemoryInfo(
    val dalvikMaxMb: Int,
    val dalvikAllocatedMb: Int,
    val dalvikFreeMb: Int,
    val nativeHeapSizeMb: Int,
    val nativeHeapAllocatedMb: Int,
    val nativeHeapFreeMb: Int,
    val totalPssKb: Int,
    val totalPrivateDirtyKb: Int,
    val totalSharedDirtyKb: Int,
) {
    val totalAllocatedMb: Int
        get() = dalvikAllocatedMb + nativeHeapAllocatedMb

    val totalMaxMb: Int
        get() = dalvikMaxMb + nativeHeapSizeMb

    fun toSummary(): String = buildString {
        appendLine("Memory Summary:")
        appendLine("  Dalvik: ${dalvikAllocatedMb}MB / ${dalvikMaxMb}MB (${dalvikFreeMb}MB free)")
        appendLine("  Native: ${nativeHeapAllocatedMb}MB / ${nativeHeapSizeMb}MB (${nativeHeapFreeMb}MB free)")
        appendLine("  Total PSS: ${totalPssKb}KB")
        appendLine("  Total Private Dirty: ${totalPrivateDirtyKb}KB")
        appendLine("  Total Shared Dirty: ${totalSharedDirtyKb}KB")
    }
}

enum class MemoryPressureLevel {
    NORMAL,
    MODERATE,
    HIGH,
    CRITICAL
}
