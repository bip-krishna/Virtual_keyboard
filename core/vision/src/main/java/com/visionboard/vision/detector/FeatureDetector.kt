package com.visionboard.vision.detector

import com.visionboard.vision.model.FeatureMatchResult
import javax.inject.Inject
import javax.inject.Singleton
import org.opencv.calib3d.Calib3d
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.features2d.ORB

@Singleton
class FeatureDetector @Inject constructor() {

    private val orb: ORB = ORB.create(MAX_FEATURES)

    private val bfMatcher = org.opencv.features2d.BFMatcher(
        org.opencv.features2d.NORM_HAMMING,
        false
    )

    fun extractFeatures(mat: Mat): List<org.opencv.core.KeyPoint> {
        val keypoints = MatOfKeyPoint()
        val descriptor = Mat()
        orb.detectAndCompute(mat, Mat(), keypoints, descriptor)
        val result = keypoints.toArray().toList()
        keypoints.release()
        descriptor.release()
        return result
    }

    fun extractFeatures(
        mat: Mat,
        outKeypoints: MatOfKeyPoint,
        outDescriptor: Mat
    ) {
        orb.detectAndCompute(mat, Mat(), outKeypoints, outDescriptor)
    }

    fun matchFeatures(
        features1: List<org.opencv.core.KeyPoint>,
        descriptors1: Mat,
        features2: List<org.opencv.core.KeyPoint>,
        descriptors2: Mat
    ): FeatureMatchResult {
        if (descriptors1.empty() || descriptors2.empty()) {
            return FeatureMatchResult(
                matchCount = 0,
                goodMatches = emptyList(),
                homography = null,
                confidence = 0f
            )
        }

        if (descriptors1.type() != descriptors2.type()) {
            return FeatureMatchResult(
                matchCount = 0,
                goodMatches = emptyList(),
                homography = null,
                confidence = 0f
            )
        }

        val knnMatches = mutableListOf<MatOfDMatch>()
        try {
            bfMatcher.knnMatch(descriptors1, descriptors2, knnMatches, 2)
        } catch (e: Exception) {
            return FeatureMatchResult(
                matchCount = 0,
                goodMatches = emptyList(),
                homography = null,
                confidence = 0f
            )
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

        if (goodMatches.size < MIN_MATCHES) {
            knnMatches.forEach { it.release() }
            return FeatureMatchResult(
                matchCount = goodMatches.size,
                goodMatches = goodMatches,
                homography = null,
                confidence = 0f
            )
        }

        val srcPoints = MatOfPoint2f()
        val dstPoints = MatOfPoint2f()

        val srcArr = goodMatches.map { features1[it.queryIdx].pt }.toTypedArray()
        val dstArr = goodMatches.map { features2[it.trainIdx].pt }.toTypedArray()

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
                2000,
                0.995
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
            (goodMatches.size.toFloat() / MIN_MATCHES / 2f).coerceIn(0f, 1f)
        }

        srcPoints.release()
        dstPoints.release()
        inlierMask.release()
        knnMatches.forEach { it.release() }

        if (!homographyFound) {
            homography.release()
        }

        return FeatureMatchResult(
            matchCount = goodMatches.size,
            goodMatches = goodMatches,
            homography = if (homographyFound) homography else null,
            confidence = confidence
        )
    }

    fun detectAndMatch(
        mat1: Mat,
        mat2: Mat
    ): FeatureMatchResult {
        val keypoints1 = MatOfKeyPoint()
        val descriptor1 = Mat()
        orb.detectAndCompute(mat1, Mat(), keypoints1, descriptor1)

        val keypoints2 = MatOfKeyPoint()
        val descriptor2 = Mat()
        orb.detectAndCompute(mat2, Mat(), keypoints2, descriptor2)

        val result = matchFeatures(
            keypoints1.toArray().toList(),
            descriptor1,
            keypoints2.toArray().toList(),
            descriptor2
        )

        keypoints1.release()
        descriptor1.release()
        keypoints2.release()
        descriptor2.release()

        return result
    }

    fun release() {
        orb.release()
        bfMatcher.clear()
    }

    companion object {
        private const val MAX_FEATURES = 1000
        private const val LOWE_RATIO = 0.75f
        private const val MIN_MATCHES = 10
        private const val RANSAC_THRESHOLD = 5.0
    }
}
