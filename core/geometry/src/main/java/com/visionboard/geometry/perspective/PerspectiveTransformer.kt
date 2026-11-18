package com.visionboard.geometry.perspective

import android.graphics.PointF
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

data class Quadrilateral(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF,
) {
    val asList: List<PointF>
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    val area: Float
        get() {
            val diag1x = bottomRight.x - topLeft.x
            val diag1y = bottomRight.y - topLeft.y
            val diag2x = bottomLeft.x - topRight.x
            val diag2y = bottomLeft.y - topRight.y
            return 0.5f * abs(diag1x * diag2y - diag1y * diag2x)
        }

    val width: Float
        get() {
            val topWidth = sqrt((topRight.x - topLeft.x).let { it * it } + (topRight.y - topLeft.y).let { it * it })
            val bottomWidth = sqrt((bottomRight.x - bottomLeft.x).let { it * it } + (bottomRight.y - bottomLeft.y).let { it * it })
            return (topWidth + bottomWidth) / 2f
        }

    val height: Float
        get() {
            val leftHeight = sqrt((bottomLeft.x - topLeft.x).let { it * it } + (bottomLeft.y - topLeft.y).let { it * it })
            val rightHeight = sqrt((bottomRight.x - topRight.x).let { it * it } + (bottomRight.y - topRight.y).let { it * it })
            return (leftHeight + rightHeight) / 2f
        }

    val aspectRatio: Float
        get() = if (height > 0f) width / height else 0f

    fun orderClockwise(): Quadrilateral {
        val points = asList
        val center = PointF(
            points.sumOf { it.x.toDouble() }.toFloat() / 4f,
            points.sumOf { it.y.toDouble() }.toFloat() / 4f,
        )

        val sorted = points.sortedWith(
            compareBy<PointF> { it.y < center.y }
                .thenBy { it.x < center.x }
        )

        val topLeft = sorted.filter { it.x <= center.x && it.y <= center.y }
            .minByOrNull { it.x + it.y } ?: sorted[0]
        val topRight = sorted.filter { it.x >= center.x && it.y <= center.y }
            .maxByOrNull { it.x - it.y } ?: sorted[1]
        val bottomRight = sorted.filter { it.x >= center.x && it.y >= center.y }
            .maxByOrNull { it.x + it.y } ?: sorted[2]
        val bottomLeft = sorted.filter { it.x <= center.x && it.y >= center.y }
            .minByOrNull { it.x - it.y } ?: sorted[3]

        return Quadrilateral(topLeft, topRight, bottomRight, bottomLeft)
    }

    fun isConvex(): Boolean {
        val pts = asList
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val c = pts[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (cross > 0) return false
        }
        return true
    }

    fun isValidForHomography(minArea: Float = 1000f): Boolean {
        return area >= minArea && isConvex() && width > 0f && height > 0f
    }
}

@Singleton
class PerspectiveTransformer @Inject constructor() {

    fun warpPerspective(
        sourceImage: Mat,
        sourceCorners: List<PointF>,
        targetWidth: Int,
        targetHeight: Int,
    ): Mat? {
        if (sourceImage.empty() || sourceCorners.size != 4) return null

        val srcMat = MatOfPoint2f(
            Point(sourceCorners[0].x.toDouble(), sourceCorners[0].y.toDouble()),
            Point(sourceCorners[1].x.toDouble(), sourceCorners[1].y.toDouble()),
            Point(sourceCorners[2].x.toDouble(), sourceCorners[2].y.toDouble()),
            Point(sourceCorners[3].x.toDouble(), sourceCorners[3].y.toDouble()),
        )

        val dstMat = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(targetWidth.toDouble(), 0.0),
            Point(targetWidth.toDouble(), targetHeight.toDouble()),
            Point(0.0, targetHeight.toDouble()),
        )

        val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val output = Mat()
        Imgproc.warpPerspective(sourceImage, output, perspectiveMatrix, Size(targetWidth.toDouble(), targetHeight.toDouble()))

        srcMat.release()
        dstMat.release()
        perspectiveMatrix.release()

        return output
    }

    fun warpWithHomography(
        sourceImage: Mat,
        homography: Mat,
        targetWidth: Int,
        targetHeight: Int,
    ): Mat? {
        if (sourceImage.empty() || homography.empty()) return null

        val output = Mat()
        Imgproc.warpPerspective(sourceImage, output, homography, Size(targetWidth.toDouble(), targetHeight.toDouble()))
        return output
    }

    fun computeQuadBounds(corners: List<PointF>): Quadrilateral {
        require(corners.size == 4) { "Exactly 4 corners required" }
        return Quadrilateral(
            corners[0], corners[1], corners[2], corners[3],
        ).orderClockwise()
    }

    fun resizeQuadrilateral(
        quad: Quadrilateral,
        targetWidth: Float,
        targetHeight: Float,
    ): Quadrilateral {
        val ordered = quad.orderClockwise()
        val scaleX = targetWidth / ordered.width.coerceAtLeast(1f)
        val scaleY = targetHeight / ordered.height.coerceAtLeast(1f)

        return Quadrilateral(
            topLeft = PointF(ordered.topLeft.x * scaleX, ordered.topLeft.y * scaleY),
            topRight = PointF(ordered.topRight.x * scaleX, ordered.topRight.y * scaleY),
            bottomRight = PointF(ordered.bottomRight.x * scaleX, ordered.bottomRight.y * scaleY),
            bottomLeft = PointF(ordered.bottomLeft.x * scaleX, ordered.bottomLeft.y * scaleY),
        )
    }

    fun getAffineTransform(
        sourceTriangle: List<PointF>,
        destinationTriangle: List<PointF>,
    ): Mat? {
        if (sourceTriangle.size != 3 || destinationTriangle.size != 3) return null

        val srcMat = MatOfPoint2f(
            *sourceTriangle.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray()
        )
        val dstMat = MatOfPoint2f(
            *destinationTriangle.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray()
        )

        return try {
            val matrix = Imgproc.getAffineTransform(srcMat, dstMat)
            srcMat.release()
            dstMat.release()
            matrix
        } catch (e: Exception) {
            srcMat.release()
            dstMat.release()
            null
        }
    }

    fun applyAffineTransform(
        sourceImage: Mat,
        affineMatrix: Mat,
        targetWidth: Int,
        targetHeight: Int,
    ): Mat {
        val output = Mat()
        Imgproc.warpAffine(sourceImage, output, affineMatrix, Size(targetWidth.toDouble(), targetHeight.toDouble()))
        return output
    }

    fun normalizePerspective(corners: List<PointF>): List<PointF> {
        if (corners.size != 4) return corners

        val ordered = Quadrilateral(
            corners[0], corners[1], corners[2], corners[3]
        ).orderClockwise()

        val maxWidth = maxOf(ordered.width, ordered.height)
        val maxHeight = maxWidth

        return listOf(
            PointF(0f, 0f),
            PointF(maxWidth, 0f),
            PointF(maxWidth, maxHeight),
            PointF(0f, maxHeight),
        )
    }

    fun detectSkew(corners: List<PointF>): Float {
        if (corners.size != 4) return 0f

        val ordered = Quadrilateral(
            corners[0], corners[1], corners[2], corners[3]
        ).orderClockwise()

        val topDx = ordered.topRight.x - ordered.topLeft.x
        val topDy = ordered.topRight.y - ordered.topLeft.y
        val angle = Math.toDegrees(Math.atan2(topDy.toDouble(), topDx.toDouble())).toFloat()

        return angle
    }

    fun correctSkew(
        sourceImage: Mat,
        corners: List<PointF>,
    ): Pair<Mat, Float> {
        val angle = detectSkew(corners)
        if (abs(angle) < 0.5f) return sourceImage to 0f

        val center = Point(sourceImage.cols() / 2.0, sourceImage.rows() / 2.0)
        val rotationMatrix = Imgproc.getRotationMatrix2D(center, angle.toDouble(), 1.0)

        val rotated = Mat()
        Imgproc.warpAffine(sourceImage, rotated, rotationMatrix, Size(sourceImage.cols().toDouble(), sourceImage.rows().toDouble()))

        rotationMatrix.release()

        return rotated to angle
    }

    private fun sqrt(value: Float): Float = kotlin.math.sqrt(value)

    private fun abs(value: Float): Float = kotlin.math.abs(value)
}
