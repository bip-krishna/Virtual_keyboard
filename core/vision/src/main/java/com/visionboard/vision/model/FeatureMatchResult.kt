package com.visionboard.vision.model

import org.opencv.core.Mat

data class FeatureMatchResult(
    val matchCount: Int,
    val goodMatches: List<org.opencv.core.DMatch>,
    val homography: Mat?,
    val confidence: Float
) {
    fun release() {
        homography?.release()
    }
}
