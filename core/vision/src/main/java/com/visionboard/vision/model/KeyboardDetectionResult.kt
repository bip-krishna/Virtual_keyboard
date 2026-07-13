package com.visionboard.vision.model

import android.graphics.PointF

data class KeyboardDetectionResult(
    val detected: Boolean,
    val corners: List<PointF>,
    val confidence: Float,
    val contourArea: Float
)
