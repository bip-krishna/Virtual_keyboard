package com.visionboard.ui.typing

import android.graphics.PointF
import com.visionboard.common.models.FingertipState
import com.visionboard.common.models.FrameResult
import com.visionboard.common.models.TouchEvent

data class TypingUiState(
    val isTypingActive: Boolean = false,
    val lastKeyPressed: String = "",
    val pressedKeyHistory: List<String> = emptyList(),
    val fps: Float = 0f,
    val latencyMs: Float = 0f,
    val bluetoothConnected: Boolean = false,
    val wpm: Float = 0f,
    val accuracy: Float = 100f,
    val totalKeyPresses: Int = 0,
    val currentHomography: FloatArray? = null,
    val detectedCorners: List<PointF> = emptyList(),
    val detectedFingertips: List<FingertipState> = emptyList(),
    val developerModeEnabled: Boolean = false,
    val showOverlay: Boolean = true,
    val cameraActive: Boolean = true,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val frameAspectRatio: Float = 0f,
    val statusMessage: String = "",
    val showConnectionBanner: Boolean = true,
    val showStatsPanel: Boolean = false,
)

data class TypingStats(
    val wpm: Float,
    val accuracy: Float,
    val totalKeyPresses: Int,
    val correctPresses: Int,
    val incorrectPresses: Int,
    val sessionDurationSec: Long,
    val peakWpm: Float,
    val avgLatencyMs: Float,
) {
    val totalAttempts: Int get() = correctPresses + incorrectPresses

    companion object {
        val EMPTY = TypingStats(0f, 100f, 0, 0, 0, 0L, 0f, 0f)
    }
}
