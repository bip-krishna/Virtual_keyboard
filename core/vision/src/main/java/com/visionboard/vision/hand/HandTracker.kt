package com.visionboard.vision.hand

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.visionboard.common.models.FingerType
import com.visionboard.vision.model.HandLandmarkResult
import com.visionboard.vision.model.HandType
import com.visionboard.vision.model.Landmark
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class HandTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutoCloseable {
    private var landmarker: HandLandmarker? = null
    private var lastTimestampMs = -1L

    @Synchronized
    fun detectHand(bitmap: Bitmap): List<HandLandmarkResult> {
        val detector = landmarker ?: createLandmarker().also { landmarker = it }
        val timestampMs = maxOf(SystemClock.uptimeMillis(), lastTimestampMs + 1)
        lastTimestampMs = timestampMs
        val image = BitmapImageBuilder(bitmap).build()
        val result = try {
            detector.detectForVideo(image, timestampMs)
        } finally {
            image.close()
        }

        return result.landmarks().mapIndexed { index, landmarks ->
            val classification = result.handedness().getOrNull(index)?.firstOrNull()
            val handType = when (classification?.categoryName()?.lowercase()) {
                "left" -> HandType.LEFT
                "right" -> HandType.RIGHT
                else -> HandType.UNKNOWN
            }

            HandLandmarkResult(
                landmarks = FingerType.entries.mapNotNull { finger ->
                    landmarks.getOrNull(finger.landmarkIndex)?.let { point ->
                        Landmark(point.x(), point.y(), point.z(), 1f, finger)
                    }
                },
                handedness = handType,
                confidence = classification?.score() ?: 0f,
            )
        }
    }

    @Synchronized
    override fun close() {
        landmarker?.close()
        landmarker = null
        lastTimestampMs = -1L
    }

    private fun createLandmarker(): HandLandmarker {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.7f)
            .setMinTrackingConfidence(0.6f)
            .build()

        return HandLandmarker.createFromOptions(context, options)
    }
}
