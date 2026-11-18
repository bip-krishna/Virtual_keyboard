package com.visionboard.domain.usecase.tracking

import com.visionboard.common.models.TouchEvent
import org.opencv.core.Mat
import javax.inject.Inject

data class ProcessingResult(
    val keyEvents: List<TouchEvent>,
    val updatedHomography: Mat?,
    val detectedHands: Int,
    val latencyMs: Float,
    val fps: Float,
)

class ProcessFrameUseCase @Inject constructor(
    private val generateKeyEventUseCase: GenerateKeyEventUseCase,
) {
    suspend operator fun invoke(frame: Mat, currentHomography: Mat?): ProcessingResult {
        val startTime = System.nanoTime()

        if (frame.empty()) {
            return ProcessingResult(
                keyEvents = emptyList(),
                updatedHomography = currentHomography,
                detectedHands = 0,
                latencyMs = 0f,
                fps = 0f,
            )
        }

        val processedFrame = preprocessFrame(frame)
        val detectedCorners = detectKeyboard(processedFrame)
        val updatedHomography = if (detectedCorners != null && currentHomography == null) {
            computeHomography(detectedCorners, processedFrame)
        } else {
            currentHomography
        }

        val handLandmarks = detectHands(processedFrame)
        val detectedHands = handLandmarks.size

        val touches = if (updatedHomography != null) {
            detectTouches(handLandmarks, updatedHomography, processedFrame)
        } else {
            emptyList()
        }

        val keyEvents = touches.map { touch ->
            generateKeyEventUseCase(touch).toTouchEvent()
        }

        val endTime = System.nanoTime()
        val latencyMs = ((endTime - startTime) / 1_000_000f)
        val fps = if (latencyMs > 0f) 1000f / latencyMs else 0f

        processedFrame.release()

        return ProcessingResult(
            keyEvents = keyEvents,
            updatedHomography = updatedHomography,
            detectedHands = detectedHands,
            latencyMs = latencyMs,
            fps = fps,
        )
    }

    private fun preprocessFrame(frame: Mat): Mat {
        val processed = Mat()
        frame.copyTo(processed)
        return processed
    }

    private fun detectKeyboard(frame: Mat): List<android.graphics.PointF>? {
        return null
    }

    private fun computeHomography(corners: List<android.graphics.PointF>, frame: Mat): Mat? {
        if (corners.size != 4) return null
        val srcPoints = org.opencv.core.MatOfPoint2f(
            org.opencv.core.Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
            org.opencv.core.Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
            org.opencv.core.Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
            org.opencv.core.Point(corners[3].x.toDouble(), corners[3].y.toDouble()),
        )
        val dstPoints = org.opencv.core.MatOfPoint2f(
            org.opencv.core.Point(0.0, 0.0),
            org.opencv.core.Point(frame.cols().toDouble(), 0.0),
            org.opencv.core.Point(frame.cols().toDouble(), frame.rows().toDouble()),
            org.opencv.core.Point(0.0, frame.rows().toDouble()),
        )
        val homography = org.opencv.calib3d.Calib3d.findHomography(srcPoints, dstPoints)
        srcPoints.release()
        dstPoints.release()
        return homography
    }

    private data class HandLandmark(
        val x: Float,
        val y: Float,
        val confidence: Float,
    )

    private fun detectHands(frame: Mat): List<List<HandLandmark>> {
        return emptyList()
    }

    private fun detectTouches(
        hands: List<List<HandLandmark>>,
        homography: Mat,
        frame: Mat,
    ): List<TouchEvent> {
        return emptyList()
    }

    private fun HidKeyEventResult.toTouchEvent(): TouchEvent {
        return TouchEvent(
            keyLabel = "",
            keycode = keycode,
            timestamp = System.currentTimeMillis(),
            confidence = 1.0f,
            gestureType = com.visionboard.common.models.GestureType.TAP,
        )
    }
}
