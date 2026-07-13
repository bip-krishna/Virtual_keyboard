package com.visionboard.vision.hand

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.visionboard.vision.model.HandLandmarkResult
import com.visionboard.vision.model.HandType
import com.visionboard.vision.model.Landmark
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat

@Singleton
class HandTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Volatile
    private var handLandmarker: HandLandmarker? = null

    private val landmarkIndicesToExtract = setOf(THUMB_TIP, INDEX_TIP, MIDDLE_TIP)

    init {
        initialize()
    }

    fun initialize() {
        synchronized(this) {
            if (handLandmarker != null) return

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxNumHands(MAX_NUM_HANDS)
                .setMinDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setModelComplexity(MODEL_COMPLEXITY)
                .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
        }
    }

    fun detectHand(image: Mat): List<HandLandmarkResult> {
        val bmp = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888)
        try {
            Utils.matToBitmap(image, bmp)
            return detectHand(bmp)
        } finally {
            bmp.recycle()
        }
    }

    fun detectHand(bitmap: Bitmap): List<HandLandmarkResult> {
        val landmarker = handLandmarker ?: run {
            initialize()
            handLandmarker ?: return emptyList()
        }

        val mpImage = BitmapImageBuilder(bitmap).build()

        val result: HandLandmarkerResult = try {
            landmarker.detect(mpImage)
        } catch (e: Exception) {
            reinitialize()
            return emptyList()
        }

        val handLandmarkResults = mutableListOf<HandLandmarkResult>()

        for (i in result.landmarks().indices) {
            val allLandmarks = result.landmarks()[i]
            val handedness = result.handedness().getOrElse(i) {
                listOf(com.google.mediapipe.tasks.components.containers.Category("Unknown", 0f))
            }

            val handType = when {
                handedness.isNotEmpty() -> {
                    val label = handedness[0].categoryName()
                    when {
                        label.contains("Left", ignoreCase = true) -> HandType.LEFT
                        label.contains("Right", ignoreCase = true) -> HandType.RIGHT
                        else -> HandType.UNKNOWN
                    }
                }
                else -> HandType.UNKNOWN
            }

            val confidence = if (handedness.isNotEmpty()) handedness[0].score() else 0f

            val extractedLandmarks = extractFingertipLandmarks(allLandmarks)

            handLandmarkResults.add(
                HandLandmarkResult(
                    landmarks = extractedLandmarks,
                    handedness = handType,
                    confidence = confidence
                )
            )
        }

        return handLandmarkResults
    }

    private fun extractFingertipLandmarks(
        allLandmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ): List<Landmark> {
        val result = mutableListOf<Landmark>()

        for (index in landmarkIndicesToExtract) {
            if (index < allLandmarks.size) {
                val lm = allLandmarks[index]
                result.add(
                    Landmark(
                        x = lm.x(),
                        y = lm.y(),
                        z = lm.z(),
                        visibility = 1.0f
                    )
                )
            }
        }

        return result
    }

    fun normalizedToPixel(
        landmark: Landmark,
        imageWidth: Int,
        imageHeight: Int
    ): android.graphics.PointF {
        val pixelX = landmark.x * imageWidth
        val pixelY = landmark.y * imageHeight
        return android.graphics.PointF(pixelX, pixelY)
    }

    fun reinitialize() {
        close()
        initialize()
    }

    fun close() {
        synchronized(this) {
            handLandmarker?.close()
            handLandmarker = null
        }
    }

    fun isInitialized(): Boolean = handLandmarker != null

    companion object {
        private const val MODEL_PATH = "hand_landmarker.task"
        private const val MAX_NUM_HANDS = 2
        private const val MIN_DETECTION_CONFIDENCE = 0.7f
        private const val MIN_TRACKING_CONFIDENCE = 0.6f
        private const val MODEL_COMPLEXITY = 1

        private const val THUMB_TIP = 4
        private const val INDEX_TIP = 8
        private const val MIDDLE_TIP = 12
    }
}
