package com.visionboard.vision.model

import com.visionboard.common.models.FingerType

enum class HandType {
    LEFT,
    RIGHT,
    UNKNOWN
}

data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val finger: FingerType,
)

data class HandLandmarkResult(
    val landmarks: List<Landmark>,
    val handedness: HandType,
    val confidence: Float
)
