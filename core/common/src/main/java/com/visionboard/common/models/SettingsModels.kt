package com.visionboard.common.models

import kotlinx.serialization.Serializable

enum class CameraResolution {
    LOW,
    MEDIUM,
    HIGH,
    MAX;

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

    val width: Int
        get() = when (this) {
            LOW -> 640
            MEDIUM -> 1280
            HIGH -> 1920
            MAX -> 2560
        }

    val height: Int
        get() = when (this) {
            LOW -> 480
            MEDIUM -> 720
            HIGH -> 1080
            MAX -> 1440
        }

    val megapixels: Float
        get() = (width * height) / 1_000_000f
}

@Serializable
data class AppSettings(
    val sensitivity: Float = 0.7f,
    val debounceMs: Long = 50L,
    val cameraFps: Int = 30,
    val cameraResolution: CameraResolution = CameraResolution.MEDIUM,
    val darkMode: Boolean = true,
    val developerMode: Boolean = false,
    val gestureEnabled: Boolean = true,
    val keyboardLayout: String = "qwerty_us",
    val bluetoothAutoReconnect: Boolean = true,
    val wifiModeEnabled: Boolean = false,
    val calibrationBackupEnabled: Boolean = true,
) {
    val isHighPerformance: Boolean
        get() = cameraResolution == CameraResolution.HIGH || cameraResolution == CameraResolution.MAX

    val effectiveFps: Int
        get() = if (isHighPerformance) 30 else cameraFps.coerceAtMost(30)

    fun validate(): AppSettings = copy(
        sensitivity = sensitivity.coerceIn(0.1f, 1.0f),
        debounceMs = debounceMs.coerceIn(10L, 500L),
        cameraFps = cameraFps.coerceIn(15, 60),
    )
}

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class NavigateTo(val route: String) : UiEvent()
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val action: (() -> Unit)? = null,
    ) : UiEvent()

    data class RequestPermission(val permission: String) : UiEvent()
    data class UpdateOverlay(val visible: Boolean) : UiEvent()
    data class CalibrationStarted(val imageCount: Int) : UiEvent()
    data object CalibrationCompleted : UiEvent()
    data class CalibrationFailed(val reason: String) : UiEvent()
    data object KeyboardConnected : UiEvent()
    data object KeyboardDisconnected : UiEvent()
    data class KeyboardConnectionError(val error: String) : UiEvent()
    data class SettingsChanged(val key: String, val value: Any) : UiEvent()
    data class PerformanceWarning(val metric: String, val value: Float) : UiEvent()
    data object FeatureDetectionStarted : UiEvent()
    data object FeatureDetectionCompleted : UiEvent()
    data class TrackingStarted(val handType: com.visionboard.common.models.HandType) : UiEvent()
    data object TrackingLost : UiEvent()
    data class DebugInfo(val info: Map<String, Any>) : UiEvent()
}
