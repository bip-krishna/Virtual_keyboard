package com.visionboard.common.models

import android.graphics.PointF
import kotlinx.serialization.Serializable

enum class HandType {
    LEFT,
    RIGHT;

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

enum class FingerType {
    THUMB,
    INDEX,
    MIDDLE,
    RING,
    PINKY;

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

    val landmarkIndex: Int
        get() = when (this) {
            THUMB -> 4
            INDEX -> 8
            MIDDLE -> 12
            RING -> 16
            PINKY -> 20
        }
}

enum class GestureType {
    TAP,
    DOUBLE_TAP,
    PINCH,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    TWO_FINGER_TAP,
    THREE_FINGER_SWIPE;

    val label: String get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Serializable
data class FingertipState(
    @Serializable(with = PointFSerializer::class)
    val position: PointF,
    @Serializable(with = PointFSerializer::class)
    val velocity: PointF,
    val acceleration: Float,
    val timestamp: Long,
    val confidence: Float,
    val handType: HandType,
    val fingerType: FingerType,
) {
    val speed: Float
        get() = Math.sqrt((velocity.x * velocity.x + velocity.y * velocity.y).toDouble()).toFloat()

    val isStationary: Boolean
        get() = speed < 10f && Math.abs(acceleration) < 5f

    val isMovingTowardKeyboard: Boolean
        get() = velocity.y > 0f && speed > 20f

    fun withUpdatedPosition(newPosition: PointF, newTimestamp: Long): FingertipState {
        val dt = ((newTimestamp - timestamp) / 1000f).coerceAtLeast(0.001f)
        val newVelocity = PointF(
            (newPosition.x - position.x) / dt,
            (newPosition.y - position.y) / dt
        )
        val oldSpeed = speed
        val newSpeed = Math.sqrt(
            (newVelocity.x * newVelocity.x + newVelocity.y * newVelocity.y).toDouble()
        ).toFloat()
        val newAcceleration = (newSpeed - oldSpeed) / dt

        return copy(
            position = newPosition,
            velocity = newVelocity,
            acceleration = newAcceleration,
            timestamp = newTimestamp,
        )
    }
}

@Serializable
data class TouchEvent(
    val keyLabel: String,
    val keycode: Int,
    val timestamp: Long,
    val confidence: Float,
    val gestureType: GestureType,
) {
    val isHighConfidence: Boolean
        get() = confidence >= 0.8f

    val isTap: Boolean
        get() = gestureType == GestureType.TAP || gestureType == GestureType.DOUBLE_TAP
}

@Serializable
data class FrameResult(
    val timestamp: Long,
    val keyboardDetected: Boolean,
    @Serializable(with = PointFListSerializer::class)
    val corners: List<PointF>?,
    val fingertips: List<FingertipState>,
    val touches: List<TouchEvent>,
    val fps: Float,
    val latencyMs: Float,
) {
    val hasFingertips: Boolean get() = fingertips.isNotEmpty()
    val hasTouches: Boolean get() = touches.isNotEmpty()
    val fingertipCount: Int get() = fingertips.size
    val touchCount: Int get() = touches.size

    val leftHandFingertips: List<FingertipState>
        get() = fingertips.filter { it.handType == HandType.LEFT }

    val rightHandFingertips: List<FingertipState>
        get() = fingertips.filter { it.handType == HandType.RIGHT }

    val isPerformanceAcceptable: Boolean
        get() = fps >= 15f && latencyMs <= 100f

    fun touchesForKey(keyLabel: String): List<TouchEvent> =
        touches.filter { it.keyLabel == keyLabel }

    companion object {
        fun empty(timestamp: Long = System.currentTimeMillis()): FrameResult = FrameResult(
            timestamp = timestamp,
            keyboardDetected = false,
            corners = null,
            fingertips = emptyList(),
            touches = emptyList(),
            fps = 0f,
            latencyMs = 0f,
        )
    }
}
