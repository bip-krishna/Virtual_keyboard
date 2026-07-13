package com.visionboard.geometry.feature

import android.graphics.PointF
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.features2d.ORB
import javax.inject.Inject
import javax.inject.Singleton

data class MatchResult(
    val matchCount: Int,
    val goodMatchCount: Int,
    val homography: Mat?,
    val confidence: Float,
    val sourceKeypoints: List<org.opencv.core.KeyPoint>,
    val destinationKeypoints: List<org.opencv.core.KeyPoint>,
    val goodMatches: List<DMatch>,
) {
    val hasHomography: Boolean
        get() = homography != null && !homography!!.empty()

    val isValid: Boolean
        get() = goodMatchCount >= MIN_VALID_MATCHES && confidence >= MIN_VALID_CONFIDENCE

    fun release() {
        homography?.release()
    }
}

@Singleton
class FeatureMatcher @Inject constructor() {

    private val orb = ORB.create(MAX_FEATURES)
    private val bfMatcher = org.opencv.features2d.BFMatcher(
        org.opencv.features2d.NORM_HAMMING,
        false
    )

    fun detectAndMatch(
        sourceImage: Mat,
        destinationImage: Mat,
    ): MatchResult {
        val sourceFeatures = detectFeatures(sourceImage)
        val destinationFeatures = detectFeatures(destinationImage)

        val result = matchFeatures(
            sourceKeypoints = sourceFeatures.keypoints,
            sourceDescriptor = sourceFeatures.descriptor,
            destinationKeypoints = destinationFeatures.keypoints,
            destinationDescriptor = destinationFeatures.descriptor,
        )

        sourceFeatures.descriptor.release()
        destinationFeatures.descriptor.release()

        return result
    }

    fun matchFeatures(
        sourceKeypoints: List<org.opencv.core.KeyPoint>,
        sourceDescriptor: Mat,
        destinationKeypoints: List<org.opencv.core.KeyPoint>,
        destinationDescriptor: Mat,
    ): MatchResult {
        if (sourceDescriptor.empty() || destinationDescriptor.empty()) {
            return MatchResult(0, 0, null, 0f, sourceKeypoints, destinationKeypoints, emptyList())
        }

        if (sourceDescriptor.type() != destinationDescriptor.type()) {
            return MatchResult(0, 0, null, 0f, sourceKeypoints, destinationKeypoints, emptyList())
        }

        val knnMatches = mutableListOf<MatOfDMatch>()
        try {
            bfMatcher.knnMatch(sourceDescriptor, destinationDescriptor, knnMatches, 2)
        } catch (e: Exception) {
            return MatchResult(0, 0, null, 0f, sourceKeypoints, destinationKeypoints, emptyList())
        }

        val goodMatches = mutableListOf<DMatch>()
        for (matchPair in knnMatches) {
            val matches = matchPair.toArray()
            if (matches.size >= 2) {
                val bestMatch = matches[0]
                val secondBest = matches[1]
                if (bestMatch.distance < LOWE_RATIO * secondBest.distance) {
                    goodMatches.add(bestMatch)
                }
            }
        }

        knnMatches.forEach { it.release() }

        if (goodMatches.size < MIN_MATCH_COUNT) {
            return MatchResult(
                matchCount = goodMatches.size,
                goodMatchCount = goodMatches.size,
                homography = null,
                confidence = computeLowMatchConfidence(goodMatches.size),
                sourceKeypoints = sourceKeypoints,
                destinationKeypoints = destinationKeypoints,
                goodMatches = goodMatches,
            )
        }

        val srcPoints = MatOfPoint2f()
        val dstPoints = MatOfPoint2f()

        val srcArr = goodMatches.map { sourceKeypoints[it.queryIdx].pt }.toTypedArray()
        val dstArr = goodMatches.map { destinationKeypoints[it.trainIdx].pt }.toTypedArray()

        srcPoints.fromArray(*srcArr)
        dstPoints.fromArray(*dstArr)

        val homography = Mat()
        val inlierMask = Mat()

        val homographyFound = try {
            Calib3d.findHomography(
                srcPoints,
                dstPoints,
                Calib3d.RANSAC,
                RANSAC_THRESHOLD,
                inlierMask,
                MAX_HOMOGRAPHY_ITERATIONS,
                HOMOGRAPHY_CONFIDENCE
            )
            homography.rows() > 0 && homography.cols() > 0
        } catch (e: Exception) {
            homography.release()
            false
        }

        val inlierCount = if (homographyFound && !inlierMask.empty()) {
            (0 until inlierMask.rows()).count { inlierMask[it, 0][0] != 0.0 }
        } else {
            0
        }

        val confidence = if (homographyFound && goodMatches.isNotEmpty()) {
            val inlierRatio = inlierCount.toFloat() / goodMatches.size
            val matchRatio = (goodMatches.size.toFloat() / MAX_FEATURES).coerceIn(0f, 1f)
            (inlierRatio * 0.7f + matchRatio * 0.3f).coerceIn(0f, 1f)
        } else {
            computeLowMatchConfidence(goodMatches.size)
        }

        srcPoints.release()
        dstPoints.release()
        inlierMask.release()

        if (!homographyFound) {
            homography.release()
        }

        return MatchResult(
            matchCount = goodMatches.size,
            goodMatchCount = inlierCount,
            homography = if (homographyFound) homography else null,
            confidence = confidence,
            sourceKeypoints = sourceKeypoints,
            destinationKeypoints = destinationKeypoints,
            goodMatches = goodMatches,
        )
    }

    fun matchWithKnownHomography(
        sourceImage: Mat,
        destinationImage: Mat,
        previousHomography: Mat,
    ): MatchResult {
        val baseResult = detectAndMatch(sourceImage, destinationImage)

        if (baseResult.hasHomography || previousHomography.empty()) {
            return baseResult
        }

        val refinedSrcPoints = mutableListOf<Point>()
        val refinedDstPoints = mutableListOf<Point>()

        for (match in baseResult.goodMatches) {
            val srcPt = baseResult.sourceKeypoints[match.queryIdx].pt
            val dstPt = baseResult.destinationKeypoints[match.trainIdx].pt

            val transformedSrc = transformPoint(
                PointF(srcPt.x, srcPt.y),
                previousHomography
            )

            refinedSrcPoints.add(Point(srcPt.x.toDouble(), srcPt.y.toDouble()))
            refinedDstPoints.add(Point(dstPt.x.toDouble(), dstPt.y.toDouble()))
        }

        if (refinedSrcPoints.size < MIN_MATCH_COUNT) {
            return baseResult
        }

        val srcMat = MatOfPoint2f(*refinedSrcPoints.toTypedArray())
        val dstMat = MatOfPoint2f(*refinedDstPoints.toTypedArray())

        val refinedHomography = Mat()
        val inlierMask = Mat()

        return try {
            Calib3d.findHomography(
                srcMat,
                dstMat,
                Calib3d.RANSAC,
                RANSAC_THRESHOLD,
                inlierMask,
                MAX_HOMOGRAPHY_ITERATIONS,
                HOMOGRAPHY_CONFIDENCE
            )

            val inlierCount = if (!inlierMask.empty()) {
                (0 until inlierMask.rows()).count { inlierMask[it, 0][0] != 0.0 }
            } else {
                0
            }

            val confidence = if (refinedHomography.rows() > 0 && refinedHomography.cols() > 0) {
                val inlierRatio = inlierCount.toFloat() / refinedSrcPoints.size
                val matchRatio = (baseResult.goodMatches.size.toFloat() / MAX_FEATURES).coerceIn(0f, 1f)
                (inlierRatio * 0.7f + matchRatio * 0.3f).coerceIn(0f, 1f)
            } else {
                baseResult.confidence
            }

            MatchResult(
                matchCount = baseResult.matchCount,
                goodMatchCount = inlierCount,
                homography = if (refinedHomography.rows() > 0 && refinedHomography.cols() > 0) refinedHomography else run { refinedHomography.release(); null },
                confidence = confidence,
                sourceKeypoints = baseResult.sourceKeypoints,
                destinationKeypoints = baseResult.destinationKeypoints,
                goodMatches = baseResult.goodMatches,
            )
        } catch (e: Exception) {
            refinedHomography.release()
            baseResult
        } finally {
            srcMat.release()
            dstMat.release()
            inlierMask.release()
        }
    }

    private fun detectFeatures(image: Mat): DetectedFeatures {
        val keypoints = MatOfKeyPoint()
        val descriptor = Mat()
        orb.detectAndCompute(image, Mat(), keypoints, descriptor)
        val result = DetectedFeatures(
            keypoints = keypoints.toArray().toList(),
            descriptor = descriptor,
        )
        keypoints.release()
        return result
    }

    private fun transformPoint(point: PointF, matrix: Mat): PointF {
        if (matrix.empty()) return point

        val src = org.opencv.core.MatOfPoint2f(
            Point(point.x.toDouble(), point.y.toDouble())
        )
        val dst = org.opencv.core.MatOfPoint2f()
        org.opencv.core.Core.perspectiveTransform(src, dst, matrix)

        val result = dst.toArray()[0]
        dst.release()
        src.release()

        return PointF(result.x.toFloat(), result.y.toFloat())
    }

    private fun computeLowMatchConfidence(matchCount: Int): Float {
        return (matchCount.toFloat() / MIN_MATCH_COUNT / 2f).coerceIn(0f, 1f)
    }

    fun release() {
        orb.release()
        bfMatcher.clear()
    }

    companion object {
        private const val MAX_FEATURES = 1000
        private const val LOWE_RATIO = 0.75f
        private const val MIN_MATCH_COUNT = 10
        private const val MIN_VALID_MATCHES = 4
        private const val MIN_VALID_CONFIDENCE = 0.3f
        private const val RANSAC_THRESHOLD = 5.0
        private const val MAX_HOMOGRAPHY_ITERATIONS = 2000
        private const val HOMOGRAPHY_CONFIDENCE = 0.995
    }

    private data class DetectedFeatures(
        val keypoints: List<org.opencv.core.KeyPoint>,
        val descriptor: Mat,
    )
}
