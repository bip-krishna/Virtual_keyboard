package com.visionboard.camera.processor

import androidx.camera.core.ImageProxy
import org.opencv.core.Mat

data class FrameProcessingResult(
    val rgbMat: Mat,
    val grayMat: Mat,
    val timestamp: Long,
    val frameNumber: Long
) {
    fun release() {
        rgbMat.release()
        grayMat.release()
    }
}

interface FrameProcessor {
    suspend fun processFrame(image: ImageProxy): FrameProcessingResult
}
