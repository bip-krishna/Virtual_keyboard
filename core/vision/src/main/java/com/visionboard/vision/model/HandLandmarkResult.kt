package com.visionboard.vision.model

enum class HandType {
    LEFT,
    RIGHT,
    UNKNOWN
}

data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
)

data class HandLandmarkResult(
    val landmarks: List<Landmark>,
    val handedness: HandType,
    val confidence: Float
)
