package com.visionboard.vision.model

import android.graphics.PointF

data class FingertipState(
    val position: PointF,
    val velocity: PointF,
    val type: FingertipType,
    val isDown: Boolean,
    val downTimestamp: Long
)

enum class FingertipType {
    THUMB,
    INDEX,
    MIDDLE
}
