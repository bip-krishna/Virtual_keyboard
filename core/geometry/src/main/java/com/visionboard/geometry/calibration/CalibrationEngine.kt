package com.visionboard.geometry.calibration

import android.graphics.PointF
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

data class CalibrationInput(
    val referenceCorners: List<PointF>,
    val capturedCorners: List<PointF>,
    val referenceImage: Mat?,
    val capturedImage: Mat?,
    val featureCount: Int,
) {
    fun isValid(): Boolean {
        return referenceCorners.size == 4 &&
            capturedCorners.size == 4 &&
            featureCount >= MIN_FEATURES &&
            referenceCorners.all { it.x.isFinite() && it.y.isFinite() } &&
            capturedCorners.all { it.x.isFinite() && it.y.isFinite() }
    }
}

data class CalibrationOutput(
    val homographyMatrix: Mat?,
    val transformedCorners: List<PointF>,
    val confidence: Float,
    val reprojectionError: Float,
    val inlierCount: Int,
    val isHighQuality: Boolean,
) {
    val isValid: Boolean
        get() = homographyMatrix != null && !homographyMatrix!!.empty() && confidence >= MIN_CONFIDENCE

    fun release() {
        homographyMatrix?.release()
    }
}

data class CalibrationQualityMetrics(
    val reprojectionError: Float,
    val inlierRatio: Float,
    val cornerDistortion: Float,
    val featureDensity: Float,
    val overallScore: Float,
) {
    val isAcceptable: Boolean
        get() = overallScore >= 0.6f

    val isExcellent: Boolean
        get() = overallScore >= 0.85f
}

@Singleton
class CalibrationEngine @Inject constructor() {

    private val homographyEngine: HomographyEngine = HomographyEngine()

    fun performCalibration(input: CalibrationInput): CalibrationOutput {
        if (!input.isValid()) {
            return CalibrationOutput(
                homographyMatrix = null,
                transformedCorners = input.capturedCorners,
                confidence = 0f,
                reprojectionError = Float.MAX_VALUE,
                inlierCount = 0,
                isHighQuality = false,
            )
        }

        val orderedReference = orderCorners(input.referenceCorners)
        val orderedCaptured = orderCorners(input.capturedCorners)

        val srcMat = MatOfPoint2f(
            *orderedCaptured.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray()
        )
        val dstMat = MatOfPoint2f(
            *orderedReference.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray()
        )

        val homography = Mat()
        val inlierMask = Mat()

        return try {
            Calib3d.findHomography(
                srcMat,
                dstMat,
                Calib3d.RANSAC,
                RANSAC_THRESHOLD.toDouble(),
                inlierMask,
                MAX_ITERATIONS,
                CONFIDENCE_LEVEL
            )

            val inlierCount = if (!inlierMask.empty()) {
                (0 until inlierMask.rows()).count { inlierMask[it, 0][0] != 0.0 }
            } else {
                0
            }

            val transformedCorners = projectCorners(orderedCaptured, homography)
            val reprojectionError = computeReprojectionError(
                orderedCaptured, orderedReference, homography
            )
            val inlierRatio = if (orderedCaptured.isNotEmpty()) {
                inlierCount.toFloat() / orderedCaptured.size
            } else {
                0f
            }

            val qualityMetrics = evaluateQuality(
                reprojectionError = reprojectionError,
                inlierRatio = inlierRatio,
                featureCount = input.featureCount,
                corners = orderedCaptured,
            )

            val confidence = computeConfidence(
                inlierRatio = inlierRatio,
                reprojectionError = reprojectionError,
                featureCount = input.featureCount,
                qualityScore = qualityMetrics.overallScore,
            )

            CalibrationOutput(
                homographyMatrix = if (!homography.empty()) homography else run { homography.release(); null },
                transformedCorners = transformedCorners,
                confidence = confidence,
                reprojectionError = reprojectionError,
                inlierCount = inlierCount,
                isHighQuality = qualityMetrics.isExcellent,
            )
        } catch (e: Exception) {
            homography.release()
            CalibrationOutput(
                homographyMatrix = null,
                transformedCorners = orderedCaptured,
                confidence = 0f,
                reprojectionError = Float.MAX_VALUE,
                inlierCount = 0,
                isHighQuality = false,
            )
        } finally {
            srcMat.release()
            dstMat.release()
            inlierMask.release()
        }
    }

    fun evaluateQuality(
        reprojectionError: Float,
        inlierRatio: Float,
        featureCount: Int,
        corners: List<PointF>,
    ): CalibrationQualityMetrics {
        val errorScore = when {
            reprojectionError < 1.0f -> 1.0f
            reprojectionError < 3.0f -> 0.8f
            reprojectionError < 5.0f -> 0.6f
            reprojectionError < 10.0f -> 0.3f
            else -> 0.1f
        }

        val inlierScore = when {
            inlierRatio > 0.9f -> 1.0f
            inlierRatio > 0.7f -> 0.8f
            inlierRatio > 0.5f -> 0.6f
            inlierRatio > 0.3f -> 0.3f
            else -> 0.1f
        }

        val cornerDistortion = computeCornerDistortion(corners)
        val distortionScore = when {
            cornerDistortion < 0.05f -> 1.0f
            cornerDistortion < 0.1f -> 0.8f
            cornerDistortion < 0.2f -> 0.6f
            else -> 0.3f
        }

        val featureDensityScore = when {
            featureCount >= 50 -> 1.0f
            featureCount >= 20 -> 0.8f
            featureCount >= 10 -> 0.6f
            else -> 0.3f
        }

        val overallScore = errorScore * 0.35f +
            inlierScore * 0.30f +
            distortionScore * 0.20f +
            featureDensityScore * 0.15f

        return CalibrationQualityMetrics(
            reprojectionError = reprojectionError,
            inlierRatio = inlierRatio,
            cornerDistortion = cornerDistortion,
            featureDensity = featureCount.toFloat() / MAX_FEATURES,
            overallScore = overallScore.coerceIn(0f, 1f),
        )
    }

    fun mergeCalibrations(
        calibrations: List<CalibrationOutput>,
    ): CalibrationOutput {
        if (calibrations.isEmpty()) {
            return CalibrationOutput(null, emptyList(), 0f, Float.MAX_VALUE, 0, false)
        }

        if (calibrations.size == 1) return calibrations[0]

        val validCalibrations = calibrations.filter { it.isValid }
        if (validCalibrations.isEmpty()) {
            return CalibrationOutput(null, emptyList(), 0f, Float.MAX_VALUE, 0, false)
        }

        val bestCalibration = validCalibrations.maxByOrNull { it.confidence }!!

        val avgConfidence = validCalibrations.map { it.confidence }.average().toFloat()
        val avgReprojectionError = validCalibrations.map { it.reprojectionError }.average().toFloat()
        val totalInliers = validCalibrations.sumOf { it.inlierCount }

        val mergedConfidence = (bestCalibration.confidence * 0.6f + avgConfidence * 0.4f)
            .coerceIn(0f, 1f)

        return CalibrationOutput(
            homographyMatrix = bestCalibration.homographyMatrix,
            transformedCorners = bestCalibration.transformedCorners,
            confidence = mergedConfidence,
            reprojectionError = avgReprojectionError,
            inlierCount = totalInliers,
            isHighQuality = mergedConfidence >= 0.85f,
        )
    }

    private fun orderCorners(points: List<PointF>): List<PointF> {
        if (points.size != 4) return points

        val center = PointF(
            points.sumOf { it.x.toDouble() }.toFloat() / 4f,
            points.sumOf { it.y.toDouble() }.toFloat() / 4f,
        )

        val topLeft = points.filter { it.x <= center.x && it.y <= center.y }
            .minByOrNull { it.x + it.y } ?: points[0]
        val topRight = points.filter { it.x >= center.x && it.y <= center.y }
            .maxByOrNull { it.x - it.y } ?: points[1]
        val bottomRight = points.filter { it.x >= center.x && it.y >= center.y }
            .maxByOrNull { it.x + it.y } ?: points[2]
        val bottomLeft = points.filter { it.x <= center.x && it.y >= center.y }
            .minByOrNull { it.x - it.y } ?: points[3]

        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun projectCorners(corners: List<PointF>, homography: Mat): List<PointF> {
        if (homography.empty()) return corners

        val src = Mat(4, 1, CvType.CV_64FC2)
        for (i in corners.indices) {
            src.put(i, 0, corners[i].x.toDouble(), corners[i].y.toDouble())
        }

        val dst = Mat(4, 1, CvType.CV_64FC2)
        Core.perspectiveTransform(src, dst, homography)

        val result = mutableListOf<PointF>()
        for (i in 0 until dst.rows()) {
            val data = dst[i, 0]
            result.add(PointF(data[0].toFloat(), data[1].toFloat()))
        }

        src.release()
        dst.release()

        return result
    }

    private fun computeReprojectionError(
        sourceCorners: List<PointF>,
        destinationCorners: List<PointF>,
        homography: Mat,
    ): Float {
        val transformed = projectCorners(sourceCorners, homography)
        if (transformed.size != destinationCorners.size) return Float.MAX_VALUE

        var totalError = 0.0
        for (i in transformed.indices) {
            val dx = transformed[i].x - destinationCorners[i].x
            val dy = transformed[i].y - destinationCorners[i].y
            totalError += sqrt((dx * dx + dy * dy).toDouble())
        }

        return (totalError / transformed.size).toFloat()
    }

    private fun computeCornerDistortion(corners: List<PointF>): Float {
        if (corners.size != 4) return 1f

        val ordered = orderCorners(corners)
        val sideLengths = listOf(
            distance(ordered[0], ordered[1]),
            distance(ordered[1], ordered[2]),
            distance(ordered[2], ordered[3]),
            distance(ordered[3], ordered[0]),
        )

        val avgLength = sideLengths.average().toFloat()
        if (avgLength == 0f) return 1f

        val maxDeviation = sideLengths.maxOf { abs(it - avgLength) / avgLength }
        return maxDeviation
    }

    private fun computeConfidence(
        inlierRatio: Float,
        reprojectionError: Float,
        featureCount: Int,
        qualityScore: Float,
    ): Float {
        val inlierComponent = inlierRatio * 0.3f
        val errorComponent = when {
            reprojectionError < 1.0f -> 0.3f
            reprojectionError < 3.0f -> 0.25f
            reprojectionError < 5.0f -> 0.2f
            reprojectionError < 10.0f -> 0.1f
            else -> 0f
        }
        val featureComponent = (featureCount.toFloat() / MAX_FEATURES).coerceIn(0f, 0.2f)
        val qualityComponent = qualityScore * 0.2f

        return (inlierComponent + errorComponent + featureComponent + qualityComponent)
            .coerceIn(0f, 1f)
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    companion object {
        private const val RANSAC_THRESHOLD = 5.0
        private const val MAX_ITERATIONS = 2000
        private const val CONFIDENCE_LEVEL = 0.995
        private const val MIN_FEATURES = 8
        private const val MAX_FEATURES = 100
        private const val MIN_CONFIDENCE = 0.5f
    }
}
