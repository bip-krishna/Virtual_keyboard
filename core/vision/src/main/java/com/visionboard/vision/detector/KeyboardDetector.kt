package com.visionboard.vision.detector

import android.graphics.PointF
import com.visionboard.vision.model.FeatureMatchResult
import com.visionboard.vision.model.KeyboardDetectionResult
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

@Singleton
class KeyboardDetector @Inject constructor(
    private val featureDetector: Lazy<FeatureDetector>
) {

    private var previousCorners: List<PointF>? = null
    private var referenceDescriptor: Mat? = null
    private var referenceKeypoints: List<org.opencv.core.KeyPoint>? = null

    fun detectKeyboard(
        grayMat: Mat,
        rgbMat: Mat
    ): KeyboardDetectionResult {
        val blurred = Mat()
        val thresh = Mat()
        val edges = Mat()
        val contours = mutableListOf<MatOfPoint>()

        try {
            Imgproc.GaussianBlur(grayMat, blurred, Size(5.0, 5.0), 0.0)

            Imgproc.adaptiveThreshold(
                blurred,
                thresh,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                11,
                2.0
            )

            Imgproc.Canny(thresh, edges, 50.0, 150.0)

            val hierarchy = Mat()
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            hierarchy.release()

            val candidates = contours
                .filter { contour ->
                    val area = Imgproc.contourArea(contour)
                    val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                    if (perimeter == 0.0) return@filter false
                    val ratio = area / (perimeter * perimeter)
                    area in 10_000.0..5_000_000.0 && ratio in 0.005..0.1
                }
                .sortedByDescending { Imgproc.contourArea(it) }

            for (contour in candidates) {
                val result = tryExtractQuadrilateral(contour, rgbMat)
                if (result != null) {
                    previousCorners = result.corners
                    return result
                }
            }

            return KeyboardDetectionResult(
                detected = false,
                corners = previousCorners ?: emptyList(),
                confidence = 0f,
                contourArea = 0f
            )
        } finally {
            blurred.release()
            thresh.release()
            edges.release()
            contours.forEach { it.release() }
        }
    }

    fun detectUsingORB(
        grayMat: Mat,
        rgbMat: Mat
    ): KeyboardDetectionResult {
        val currentFeatures = featureDetector.get().extractFeatures(grayMat)
        if (currentFeatures.isEmpty()) {
            return KeyboardDetectionResult(
                detected = false,
                corners = previousCorners ?: emptyList(),
                confidence = 0f,
                contourArea = 0f
            )
        }

        val currentDescriptor = Mat()
        val orb = org.opencv.features2d.ORB.create(1000)
        orb.detectAndCompute(grayMat, Mat(), currentFeatures.toMutableList(), currentDescriptor)

        if (referenceDescriptor == null || referenceDescriptor!!.empty() || referenceKeypoints == null) {
            referenceDescriptor = currentDescriptor.clone()
            referenceKeypoints = currentFeatures
            return KeyboardDetectionResult(
                detected = false,
                corners = emptyList(),
                confidence = 0f,
                contourArea = 0f
            )
        }

        val matchResult = featureDetector.get().matchFeatures(
            referenceKeypoints!!,
            referenceDescriptor!!,
            currentFeatures,
            currentDescriptor
        )

        currentDescriptor.release()

        val corners = previousCorners
        if (matchResult.confidence > 0.3f && corners != null && corners.size == 4) {
            if (matchResult.homography != null && !matchResult.homography!!.empty()) {
                val projected = projectCorners(corners, matchResult.homography!!)
                previousCorners = projected
                return KeyboardDetectionResult(
                    detected = true,
                    corners = projected,
                    confidence = matchResult.confidence,
                    contourArea = computeQuadArea(projected)
                )
            }
            return KeyboardDetectionResult(
                detected = true,
                corners = corners,
                confidence = matchResult.confidence,
                contourArea = computeQuadArea(corners)
            )
        }

        return KeyboardDetectionResult(
            detected = false,
            corners = corners ?: emptyList(),
            confidence = matchResult.confidence,
            contourArea = 0f
        )
    }

    fun setReferenceFrame(
        grayMat: Mat,
        corners: List<PointF>
    ) {
        val keypoints = featureDetector.get().extractFeatures(grayMat)
        if (keypoints.isNotEmpty()) {
            val descriptor = Mat()
            val orb = org.opencv.features2d.ORB.create(1000)
            orb.detectAndCompute(grayMat, Mat(), keypoints.toMutableList(), descriptor)
            referenceDescriptor = descriptor
            referenceKeypoints = keypoints
            previousCorners = corners
        }
    }

    fun reset() {
        previousCorners = null
        referenceDescriptor?.release()
        referenceDescriptor = null
        referenceKeypoints = null
    }

    private fun tryExtractQuadrilateral(
        contour: MatOfPoint,
        rgbMat: Mat
    ): KeyboardDetectionResult? {
        val approx = MatOfPoint2f()
        val contour2f = MatOfPoint2f(*contour.toArray())

        val perimeter = Imgproc.arcLength(contour2f, true)
        Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)

        if (approx.toArray().size != 4) {
            approx.release()
            contour2f.release()
            return null
        }

        if (!Imgproc.isContourConvex(approx)) {
            approx.release()
            contour2f.release()
            return null
        }

        val points = approx.toArray()
        val ordered = orderCorners(points)
        val area = Imgproc.contourArea(approx)

        val corners = ordered.map { PointF(it.x.toFloat(), it.y.toFloat()) }

        val contourArea = Imgproc.contourArea(contour)
        val rectArea = rgbMat.rows() * rgbMat.cols().toDouble()
        val areaRatio = contourArea / rectArea
        val confidence = (areaRatio * 10f).coerceIn(0f, 1f)

        approx.release()
        contour2f.release()

        return KeyboardDetectionResult(
            detected = true,
            corners = corners,
            confidence = confidence,
            contourArea = area.toFloat()
        )
    }

    private fun orderCorners(points: Array<Point>): List<Point> {
        val center = Point(
            points.sumOf { it.x } / 4.0,
            points.sumOf { it.y } / 4.0
        )

        val sorted = points.sortedWith(compareBy<Point> { it.y < center.y }.thenBy { it.x < center.x })

        val topLeft = sorted.filter { it.x <= center.x && it.y <= center.y }
            .minByOrNull { it.x + it.y }
            ?: sorted[0]
        val topRight = sorted.filter { it.x >= center.x && it.y <= center.y }
            .maxByOrNull { it.x - it.y }
            ?: sorted[1]
        val bottomRight = sorted.filter { it.x >= center.x && it.y >= center.y }
            .maxByOrNull { it.x + it.y }
            ?: sorted[2]
        val bottomLeft = sorted.filter { it.x <= center.x && it.y >= center.y }
            .minByOrNull { it.x - it.y }
            ?: sorted[3]

        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun projectCorners(
        corners: List<PointF>,
        homography: Mat
    ): List<PointF> {
        val src = Mat(4, 1, CvType.CV_64FC2)
        for (i in corners.indices) {
            src.put(i, 0, corners[i].x.toDouble(), corners[i].y.toDouble())
        }

        val dst = Mat(4, 1, CvType.CV_64FC2)
        org.opencv.core.Core.perspectiveTransform(src, dst, homography)

        val result = mutableListOf<PointF>()
        for (i in 0 until dst.rows()) {
            val data = dst[i, 0]
            result.add(PointF(data[0].toFloat(), data[1].toFloat()))
        }

        src.release()
        dst.release()

        return result
    }

    private fun computeQuadArea(corners: List<PointF>): Float {
        if (corners.size < 4) return 0f
        val a = corners[0]
        val b = corners[1]
        val c = corners[2]
        val d = corners[3]

        val diag1x = c.x - a.x
        val diag1y = c.y - a.y
        val diag2x = d.x - b.x
        val diag2y = d.y - b.y

        return 0.5f * kotlin.math.abs(diag1x * diag2y - diag1y * diag2x)
    }
}
