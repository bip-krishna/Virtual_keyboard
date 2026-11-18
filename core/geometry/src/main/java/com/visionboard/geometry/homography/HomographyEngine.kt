package com.visionboard.geometry.homography

import android.graphics.PointF
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

private const val MIN_VALID_INLIER_RATIO = 0.3f

data class HomographyResult(
    val matrix: Mat?,
    val inlierCount: Int,
    val totalPoints: Int,
    val inlierRatio: Float,
    val reprojectionError: Float,
) {
    val isValid: Boolean
        get() = matrix != null && !matrix!!.empty() && inlierRatio >= MIN_VALID_INLIER_RATIO

    fun release() {
        matrix?.release()
    }
}

data class PerspectiveTransformResult(
    val transformedImage: Mat,
    val outputWidth: Int,
    val outputHeight: Int,
) {
    fun release() {
        transformedImage.release()
    }
}

data class FeaturePointSet(
    val keypoints: List<org.opencv.core.KeyPoint>,
    val descriptor: Mat,
    val grayMat: Mat,
) {
    fun release() {
        descriptor.release()
    }
}

@Singleton
class HomographyEngine @Inject constructor() {

    private val orb = ORB.create(MAX_ORB_FEATURES)
    private val bfMatcher = org.opencv.features2d.BFMatcher(
        org.opencv.core.Core.NORM_HAMMING,
        false
    )

    fun computeHomography(
        sourcePoints: List<PointF>,
        destinationPoints: List<PointF>,
    ): HomographyResult {
        if (sourcePoints.size < MIN_POINTS || destinationPoints.size < MIN_POINTS) {
            return HomographyResult(null, 0, 0, 0f, Float.MAX_VALUE)
        }

        val srcMat = MatOfPoint2f()
        val dstMat = MatOfPoint2f()

        srcMat.fromArray(*sourcePoints.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        dstMat.fromArray(*destinationPoints.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())

        var homography: Mat? = null
        val inlierMask = Mat()

        return try {
            val matrix = Calib3d.findHomography(
                srcMat,
                dstMat,
                Calib3d.RANSAC,
                RANSAC_THRESHOLD.toDouble(),
                inlierMask,
                MAX_HOMOGRAPHY_ITERATIONS,
                CONFIDENCE
            )

            homography = matrix
            val inlierCount = if (!inlierMask.empty()) {
                (0 until inlierMask.rows()).count { inlierMask[it, 0][0] != 0.0 }
            } else {
                0
            }

            val totalPoints = sourcePoints.size
            val inlierRatio = if (totalPoints > 0) inlierCount.toFloat() / totalPoints else 0f
            val reprojectionError = computeReprojectionError(
                sourcePoints, destinationPoints,
                matrix.takeUnless { it.empty() }
            )

            HomographyResult(
                matrix = if (!matrix.empty()) matrix else run { matrix.release(); null },
                inlierCount = inlierCount,
                totalPoints = totalPoints,
                inlierRatio = inlierRatio,
                reprojectionError = reprojectionError,
            )
        } catch (e: Exception) {
            homography?.release()
            HomographyResult(null, 0, sourcePoints.size, 0f, Float.MAX_VALUE)
        } finally {
            srcMat.release()
            dstMat.release()
            inlierMask.release()
        }
    }

    fun computeHomographyFromFeatureMatch(
        sourceFeatures: FeaturePointSet,
        destinationFeatures: FeaturePointSet,
    ): HomographyResult {
        if (sourceFeatures.descriptor.empty() || destinationFeatures.descriptor.empty()) {
            return HomographyResult(null, 0, 0, 0f, Float.MAX_VALUE)
        }

        if (sourceFeatures.descriptor.type() != destinationFeatures.descriptor.type()) {
            return HomographyResult(null, 0, 0, 0f, Float.MAX_VALUE)
        }

        val knnMatches = mutableListOf<MatOfDMatch>()
        try {
            bfMatcher.knnMatch(sourceFeatures.descriptor, destinationFeatures.descriptor, knnMatches, 2)
        } catch (e: Exception) {
            return HomographyResult(null, 0, 0, 0f, Float.MAX_VALUE)
        }

        val goodMatches = mutableListOf<DMatch>()
        for (matchPair in knnMatches) {
            val matches = matchPair.toArray()
            if (matches.size >= 2) {
                if (matches[0].distance < LOWE_RATIO * matches[1].distance) {
                    goodMatches.add(matches[0])
                }
            }
        }

        if (goodMatches.size < MIN_MATCH_COUNT) {
            knnMatches.forEach { it.release() }
            return HomographyResult(null, goodMatches.size, 0, 0f, Float.MAX_VALUE)
        }

        val srcPoints = MatOfPoint2f()
        val dstPoints = MatOfPoint2f()

        val srcArr = goodMatches.map { sourceFeatures.keypoints[it.queryIdx].pt }.toTypedArray()
        val dstArr = goodMatches.map { destinationFeatures.keypoints[it.trainIdx].pt }.toTypedArray()

        srcPoints.fromArray(*srcArr)
        dstPoints.fromArray(*dstArr)

        val result = computeHomography(
            srcArr.map { PointF(it.x.toFloat(), it.y.toFloat()) },
            dstArr.map { PointF(it.x.toFloat(), it.y.toFloat()) },
        )

        srcPoints.release()
        dstPoints.release()
        knnMatches.forEach { it.release() }

        return result
    }

    fun extractFeatures(grayMat: Mat): FeaturePointSet {
        val keypoints = MatOfKeyPoint()
        val descriptor = Mat()
        orb.detectAndCompute(grayMat, Mat(), keypoints, descriptor)
        val result = FeaturePointSet(
            keypoints = keypoints.toArray().toList(),
            descriptor = descriptor,
            grayMat = grayMat,
        )
        keypoints.release()
        return result
    }

    fun applyPerspectiveTransform(
        sourceImage: Mat,
        corners: List<PointF>,
        outputWidth: Int,
        outputHeight: Int,
    ): PerspectiveTransformResult? {
        if (corners.size != 4 || sourceImage.empty()) return null

        val srcPoints = MatOfPoint2f(
            Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
            Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
            Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
            Point(corners[3].x.toDouble(), corners[3].y.toDouble()),
        )

        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outputWidth.toDouble(), 0.0),
            Point(outputWidth.toDouble(), outputHeight.toDouble()),
            Point(0.0, outputHeight.toDouble()),
        )

        val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        val transformed = Mat()
        Imgproc.warpPerspective(sourceImage, transformed, perspectiveMatrix, Size(outputWidth.toDouble(), outputHeight.toDouble()))

        srcPoints.release()
        dstPoints.release()
        perspectiveMatrix.release()

        return PerspectiveTransformResult(
            transformedImage = transformed,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
        )
    }

    fun invertHomography(homography: Mat): Mat? {
        if (homography.empty()) return null
        return try {
            val inverted = Mat()
            Core.invert(homography, inverted, Core.DECOMP_LU)
            if (inverted.empty()) {
                inverted.release()
                null
            } else {
                inverted
            }
        } catch (e: Exception) {
            null
        }
    }

    fun composeHomographies(h1: Mat, h2: Mat): Mat? {
        if (h1.empty() || h2.empty()) return null
        return try {
            val result = Mat()
            Core.gemm(h2, h1, 1.0, Mat(), 0.0, result)
            if (result.empty()) {
                result.release()
                null
            } else {
                result
            }
        } catch (e: Exception) {
            null
        }
    }

    fun transformPoint(point: PointF, homography: Mat): PointF {
        if (homography.empty()) return point

        val src = MatOfPoint2f(Point(point.x.toDouble(), point.y.toDouble()))
        val dst = MatOfPoint2f()
        Core.perspectiveTransform(src, dst, homography)

        val result = dst.toArray()[0]
        dst.release()
        src.release()

        return PointF(result.x.toFloat(), result.y.toFloat())
    }

    fun transformPoints(points: List<PointF>, homography: Mat): List<PointF> {
        return points.map { transformPoint(it, homography) }
    }

    private fun computeReprojectionError(
        sourcePoints: List<PointF>,
        destinationPoints: List<PointF>,
        homography: Mat?,
    ): Float {
        if (homography == null || homography.empty() || sourcePoints.size != destinationPoints.size) {
            return Float.MAX_VALUE
        }

        var totalError = 0.0
        for (i in sourcePoints.indices) {
            val transformed = transformPoint(sourcePoints[i], homography)
            val dx = transformed.x - destinationPoints[i].x
            val dy = transformed.y - destinationPoints[i].y
            totalError += sqrt((dx * dx + dy * dy).toDouble())
        }

        return (totalError / sourcePoints.size).toFloat()
    }

    fun release() {
        orb.clear()
        bfMatcher.clear()
    }

    companion object {
        private const val MAX_ORB_FEATURES = 1000
        private const val LOWE_RATIO = 0.75f
        private const val MIN_MATCH_COUNT = 10
        private const val RANSAC_THRESHOLD = 5.0f
        private const val MAX_HOMOGRAPHY_ITERATIONS = 2000
        private const val CONFIDENCE = 0.995
        private const val MIN_POINTS = 4
    }
}
