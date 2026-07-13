package com.visionboard.ui.developer

import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visionboard.common.utils.PerformanceMonitor
import com.visionboard.utils.logging.AppLogger
import com.visionboard.utils.logging.LogEntry
import com.visionboard.utils.logging.LogLevel
import com.visionboard.utils.performance.MemoryGuard
import com.visionboard.utils.performance.MemoryState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeveloperDebugState(
    val fps: Float = 0f,
    val averageLatencyMs: Float = 0f,
    val maxLatencyMs: Float = 0f,
    val currentLatencyMs: Float = 0f,
    val cpuUsagePercent: Float = 0f,
    val memoryUsedMb: Float = 0f,
    val memoryAvailableMb: Float = 0f,
    val frameDropCount: Int = 0,
    val totalFramesRendered: Long = 0,
    val fpsHistory: List<Float> = emptyList(),
    val latencyHistory: List<Float> = emptyList(),
    val detectionConfidence: Float = 0f,
    val cornersDetected: Int = 0,
    val featureMatchCount: Int = 0,
    val homographyDeterminant: Float = 0f,
    val fingerPositions: Map<String, PointF> = emptyMap(),
    val fingerVelocities: Map<String, Float> = emptyMap(),
    val fingerConfidence: Map<String, Float> = emptyMap(),
    val activeFingerCount: Int = 0,
    val lastTouchEvent: String = "",
    val contactProbability: Float = 0f,
    val debounceState: String = "idle",
    val gestureDetected: String = "none",
    val bluetoothConnectionState: String = "Disconnected",
    val bluetoothDeviceName: String = "",
    val bluetoothReportsSent: Int = 0,
    val bluetoothErrors: Int = 0,
    val serverState: String = "Offline",
    val connectedClients: Int = 0,
    val networkMessagesSent: Int = 0,
    val overlayShowCameraFeed: Boolean = true,
    val overlayShowKeyboardOutline: Boolean = true,
    val overlayShowCornerPoints: Boolean = true,
    val overlayShowHomographyGrid: Boolean = false,
    val overlayShowFingerSkeleton: Boolean = true,
    val overlayShowVelocityVectors: Boolean = false,
    val overlayShowTouchPoints: Boolean = true,
    val overlayShowKeyPolygons: Boolean = false,
    val overlayShowFpsCounter: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val memoryState: MemoryState = MemoryState.UNKNOWN,
)

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val performanceMonitor: PerformanceMonitor,
    private val memoryGuard: MemoryGuard,
    private val appLogger: AppLogger,
) : ViewModel() {

    private val _debugState = MutableStateFlow(DeveloperDebugState())
    val debugState: StateFlow<DeveloperDebugState> = _debugState.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentLogs: StateFlow<List<LogEntry>> = _recentLogs.asStateFlow()

    private var refreshJob: Job? = null
    private val fpsHistory = mutableListOf<Float>()
    private val latencyHistory = mutableListOf<Float>()

    init {
        startPeriodicRefresh()
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                refreshMetrics()
                delay(500L)
            }
        }
    }

    fun refreshMetrics() {
        val perfMetrics = performanceMonitor.getMetrics()
        val memState = memoryGuard.getMemoryState()

        fpsHistory.add(perfMetrics.fps)
        if (fpsHistory.size > 120) fpsHistory.removeFirst()

        latencyHistory.add(perfMetrics.currentLatencyMs)
        if (latencyHistory.size > 120) latencyHistory.removeFirst()

        _debugState.update { state ->
            state.copy(
                fps = perfMetrics.fps,
                averageLatencyMs = perfMetrics.averageLatencyMs,
                maxLatencyMs = perfMetrics.maxLatencyMs,
                currentLatencyMs = perfMetrics.currentLatencyMs,
                cpuUsagePercent = perfMetrics.cpuUsagePercent,
                memoryUsedMb = perfMetrics.memoryUsedMB,
                memoryAvailableMb = perfMetrics.memoryAvailableMB,
                frameDropCount = perfMetrics.frameDropCount,
                totalFramesRendered = perfMetrics.totalFramesRendered,
                fpsHistory = fpsHistory.toList(),
                latencyHistory = latencyHistory.toList(),
                memoryState = memState,
                timestamp = System.currentTimeMillis(),
            )
        }
    }

    fun updateDetectionMetrics(
        confidence: Float,
        corners: Int,
        matches: Int,
        determinant: Float,
    ) {
        _debugState.update {
            it.copy(
                detectionConfidence = confidence,
                cornersDetected = corners,
                featureMatchCount = matches,
                homographyDeterminant = determinant,
            )
        }
    }

    fun updateTrackingMetrics(
        positions: Map<String, PointF>,
        velocities: Map<String, Float>,
        confidences: Map<String, Float>,
        activeCount: Int,
    ) {
        _debugState.update {
            it.copy(
                fingerPositions = positions,
                fingerVelocities = velocities,
                fingerConfidence = confidences,
                activeFingerCount = activeCount,
            )
        }
    }

    fun updateTouchMetrics(
        lastEvent: String,
        contactProb: Float,
        debounce: String,
        gesture: String,
    ) {
        _debugState.update {
            it.copy(
                lastTouchEvent = lastEvent,
                contactProbability = contactProb,
                debounceState = debounce,
                gestureDetected = gesture,
            )
        }
    }

    fun updateBluetoothMetrics(
        connectionState: String,
        deviceName: String,
        reportsSent: Int,
        errors: Int,
    ) {
        _debugState.update {
            it.copy(
                bluetoothConnectionState = connectionState,
                bluetoothDeviceName = deviceName,
                bluetoothReportsSent = reportsSent,
                bluetoothErrors = errors,
            )
        }
    }

    fun updateNetworkMetrics(
        serverState: String,
        clients: Int,
        messagesSent: Int,
    ) {
        _debugState.update {
            it.copy(
                serverState = serverState,
                connectedClients = clients,
                networkMessagesSent = messagesSent,
            )
        }
    }

    fun toggleOverlay(overlay: String) {
        _debugState.update { state ->
            when (overlay) {
                "cameraFeed" -> state.copy(overlayShowCameraFeed = !state.overlayShowCameraFeed)
                "keyboardOutline" -> state.copy(overlayShowKeyboardOutline = !state.overlayShowKeyboardOutline)
                "cornerPoints" -> state.copy(overlayShowCornerPoints = !state.overlayShowCornerPoints)
                "homographyGrid" -> state.copy(overlayShowHomographyGrid = !state.overlayShowHomographyGrid)
                "fingerSkeleton" -> state.copy(overlayShowFingerSkeleton = !state.overlayShowFingerSkeleton)
                "velocityVectors" -> state.copy(overlayShowVelocityVectors = !state.overlayShowVelocityVectors)
                "touchPoints" -> state.copy(overlayShowTouchPoints = !state.overlayShowTouchPoints)
                "keyPolygons" -> state.copy(overlayShowKeyPolygons = !state.overlayShowKeyPolygons)
                "fpsCounter" -> state.copy(overlayShowFpsCounter = !state.overlayShowFpsCounter)
                else -> state
            }
        }
    }

    fun exportLogs(): String {
        return appLogger.exportLogs()
    }

    fun clearLogs() {
        appLogger.clearLogs()
        _recentLogs.value = emptyList()
    }

    fun captureScreenshot(): String {
        appLogger.i("DeveloperViewModel", "Screenshot capture requested")
        return "Screenshot captured at ${System.currentTimeMillis()}"
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
