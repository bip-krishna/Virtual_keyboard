package com.visionboard.domain.model

import android.graphics.PointF

data class CalibrationDomain(
    val id: Long = 0,
    val name: String,
    val layout: String,
    val corners: List<PointF>,
    val featureCount: Int,
    val confidence: Float,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false,
) {
    fun isValid(): Boolean =
        name.isNotBlank() &&
            layout.isNotBlank() &&
            corners.size == 4 &&
            featureCount >= 4 &&
            confidence >= 0.5f

    fun validate(): Result<CalibrationDomain> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Calibration name cannot be blank"))
        if (layout.isBlank()) return Result.failure(IllegalArgumentException("Layout cannot be blank"))
        if (corners.size != 4) return Result.failure(IllegalArgumentException("Exactly 4 corners are required"))
        if (featureCount < 4) return Result.failure(IllegalArgumentException("At least 4 features are required"))
        if (confidence < 0.5f) return Result.failure(IllegalArgumentException("Confidence must be at least 0.5"))
        return Result.success(this)
    }
}

data class TypingSessionDomain(
    val date: Long = System.currentTimeMillis(),
    val totalKeyPresses: Int,
    val uniqueKeys: Int,
    val avgLatency: Float,
    val avgConfidence: Float,
    val wpm: Float,
    val accuracy: Float,
    val duration: Long,
) {
    fun isValid(): Boolean =
        totalKeyPresses > 0 &&
            uniqueKeys > 0 &&
            avgLatency >= 0f &&
            avgConfidence in 0f..1f &&
            wpm >= 0f &&
            accuracy in 0f..1f &&
            duration > 0L
}

data class HeatmapData(
    val keyLabel: String,
    val pressCount: Int,
    val avgConfidence: Float,
    val avgLatency: Float,
)

data class AppSettingsDomain(
    val sensitivity: Float = 0.7f,
    val debounceMs: Long = 50L,
    val cameraFps: Int = 30,
    val resolution: String = "MEDIUM",
    val darkMode: Boolean = true,
    val developerMode: Boolean = false,
    val gestureEnabled: Boolean = true,
    val keyboardLayout: String = "qwerty_us",
    val autoReconnect: Boolean = true,
    val wifiMode: Boolean = false,
) {
    fun validate(): AppSettingsDomain = copy(
        sensitivity = sensitivity.coerceIn(0.1f, 1.0f),
        debounceMs = debounceMs.coerceIn(10L, 500L),
        cameraFps = cameraFps.coerceIn(15, 60),
    )
}
