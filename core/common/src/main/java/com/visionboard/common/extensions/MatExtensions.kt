package com.visionboard.common.extensions

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfFloat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Subdiv2D
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin

// ── Conversion ────────────────────────────────────────────────────────────────

fun Mat.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(this, bitmap)
    return bitmap
}

fun Mat.toBitmap(config: Bitmap.Config): Bitmap {
    val bitmap = Bitmap.createBitmap(cols(), rows(), config)
    Utils.matToBitmap(this, bitmap)
    return bitmap
}

fun Mat.cloneMat(): Mat = this.clone()

fun Mat.toGrayscale(): Mat {
    if (this.channels() == 1) return this.clone()
    val gray = Mat()
    Imgproc.cvtColor(this, gray, Imgproc.COLOR_BGR2GRAY)
    return gray
}

fun Mat.toByteArray(format: String = ".jpg", quality: Int = 90): ByteArray {
    val mat = if (this.channels() == 1) {
        val bgr = Mat()
        Imgproc.cvtColor(this, bgr, Imgproc.COLOR_GRAY2BGR)
        bgr
    } else {
        this
    }
    val ext = if (format.startsWith(".")) format else ".$format"
    val encoded = org.opencv.imgcodecs.Imgcodecs.imencode(ext, mat, org.opencv.core.MatOfInt(
        org.opencv.imgcodecs.Imgcodecs.IMWRITE_JPEG_QUALITY, quality
    ))
    if (mat !== this) mat.release()
    val result = ByteArray(encoded.second.total().toInt())
    encoded.first.get(0, 0, result)
    encoded.first.release()
    return result
}

// ── Transform ─────────────────────────────────────────────────────────────────

fun Mat.resize(width: Int, height: Int, interpolation: Int = Imgproc.INTER_LINEAR): Mat {
    val resized = Mat()
    Imgproc.resize(this, resized, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, interpolation)
    return resized
}

fun Mat.resize(factor: Double, interpolation: Int = Imgproc.INTER_LINEAR): Mat {
    val resized = Mat()
    Imgproc.resize(this, resized, Size(), factor, factor, interpolation)
    return resized
}

fun Mat.resize(targetWidth: Int, maintainAspectRatio: Boolean = true): Mat {
    if (!maintainAspectRatio) {
        val aspect = cols().toDouble() / rows().toDouble()
        val targetHeight = (targetWidth / aspect).toInt()
        return resize(targetWidth, targetHeight)
    }
    val aspect = cols().toDouble() / rows().toDouble()
    val targetHeight = (targetWidth / aspect).toInt()
    return resize(targetWidth, targetHeight)
}

fun Mat.crop(rect: Rect): Mat {
    return this.submat(rect)
}

fun Mat.crop(x: Int, y: Int, width: Int, height: Int): Mat {
    return this.submat(Rect(x, y, width.coerceAtMost(cols() - x), height.coerceAtMost(rows() - y)))
}

fun Mat.applyGaussianBlur(kernelSize: Int = 5, sigmaX: Double = 0.0): Mat {
    val blurred = Mat()
    Imgproc.GaussianBlur(this, blurred, Size(kernelSize.toDouble(), kernelSize.toDouble()), sigmaX)
    return blurred
}

fun Mat.applyGaussianBlur(kSize: Size, sigmaX: Double = 0.0, sigmaY: Double = 0.0): Mat {
    val blurred = Mat()
    Imgproc.GaussianBlur(this, blurred, kSize, sigmaX, sigmaY)
    return blurred
}

fun Mat.applyAdaptiveThreshold(
    maxValue: Double = 255.0,
    adaptiveMethod: Int = Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
    thresholdType: Int = Imgproc.THRESH_BINARY,
    blockSize: Int = 11,
    c: Double = 2.0,
): Mat {
    val gray = if (this.channels() > 1) this.toGrayscale() else this
    val result = Mat()
    Imgproc.adaptiveThreshold(gray, result, maxValue, adaptiveMethod, thresholdType, blockSize, c)
    if (gray !== this) gray.release()
    return result
}

fun Mat.applyBinaryThreshold(
    threshold: Double = 128.0,
    maxValue: Double = 255.0,
    type: Int = Imgproc.THRESH_BINARY,
): Mat {
    val gray = if (this.channels() > 1) this.toGrayscale() else this
    val result = Mat()
    Imgproc.threshold(gray, result, threshold, maxValue, type)
    if (gray !== this) gray.release()
    return result
}

fun Mat.applyCanny(threshold1: Double = 50.0, threshold2: Double = 150.0): Mat {
    val gray = if (this.channels() > 1) this.toGrayscale() else this
    val edges = Mat()
    Imgproc.Canny(gray, edges, threshold1, threshold2)
    if (gray !== this) gray.release()
    return edges
}

// ── Drawing ───────────────────────────────────────────────────────────────────

fun Mat.drawCircle(
    center: PointF,
    radius: Int = 5,
    color: Scalar = Scalar(0.0, 255.0, 0.0),
    thickness: Int = 2,
) {
    Imgproc.circle(this, center.toOpenCVPoint(), radius, color, thickness)
}

fun Mat.drawCircle(
    x: Float,
    y: Float,
    radius: Int = 5,
    color: Scalar = Scalar(0.0, 255.0, 0.0),
    thickness: Int = 2,
) {
    Imgproc.circle(this, Point(x.toDouble(), y.toDouble()), radius, color, thickness)
}

fun Mat.drawLine(
    start: PointF,
    end: PointF,
    color: Scalar = Scalar(0.0, 255.0, 0.0),
    thickness: Int = 2,
    lineType: Int = Imgproc.LINE_AA,
) {
    Imgproc.line(this, start.toOpenCVPoint(), end.toOpenCVPoint(), color, thickness, lineType)
}

fun Mat.drawPolygon(
    points: List<PointF>,
    color: Scalar = Scalar(0.0, 255.0, 0.0),
    thickness: Int = 2,
    closed: Boolean = true,
) {
    if (points.size < 2) return
    val matOfPoints = MatOfPoint(*points.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
    val contourIdx = 0
    val lineType = Imgproc.LINE_AA
    Imgproc.polylines(
        this,
        listOf(matOfPoints),
        closed,
        color,
        thickness,
        lineType
    )
    matOfPoints.release()
}

fun Mat.drawPolygon(
    points: List<PointF>,
    color: Scalar,
    filledColor: Scalar,
) {
    if (points.size < 3) return
    val matOfPoints = MatOfPoint(*points.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
    val contourList = listOf(matOfPoints)
    org.opencv.core.MatOfInt().apply { /* hull placeholder */ }
    Imgproc.fillPoly(this, contourList, filledColor)
    Imgproc.polylines(this, contourList, true, color, 2, Imgproc.LINE_AA)
    matOfPoints.release()
}

fun Mat.putText(
    text: String,
    origin: PointF,
    fontFace: Int = Imgproc.FONT_HERSHEY_SIMPLEX,
    fontScale: Double = 0.8,
    color: Scalar = Scalar(255.0, 255.0, 255.0),
    thickness: Int = 2,
    lineType: Int = Imgproc.LINE_AA,
) {
    Imgproc.putText(this, text, origin.toOpenCVPoint(), fontFace, fontScale, color, thickness, lineType)
}

// ── Contour / Detection ───────────────────────────────────────────────────────

fun Mat.findContours(
    mode: Int = Imgproc.RETR_EXTERNAL,
    method: Int = Imgproc.CHAIN_APPROX_SIMPLE,
): List<MatOfPoint> {
    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(this, contours, hierarchy, mode, method)
    hierarchy.release()
    return contours
}

fun Mat.findLargestQuadrilateral(): List<PointF>? {
    val gray = if (this.channels() > 1) this.toGrayscale() else this
    val blurred = gray.applyGaussianBlur(5)
    val edges = blurred.applyCanny(50.0, 150.0)
    val contours = edges.findContours()

    if (contours.isEmpty()) {
        if (gray !== this) gray.release()
        blurred.release()
        edges.release()
        return null
    }

    val sortedContours = contours.sortedByDescending { Imgproc.contourArea(it) }

    for (contour in sortedContours) {
        val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray().map { Point(it.x, it.y) }.toTypedArray()), true)
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(
            MatOfPoint2f(*contour.toArray().map { Point(it.x, it.y) }.toTypedArray()),
            approx,
            0.02 * perimeter,
            true
        )

        if (approx.toArray().size == 4) {
            val points = approx.toArray().map { PointF(it.x.toFloat(), it.y.toFloat()) }
            contour.release()
            approx.release()
            edges.release()
            blurred.release()
            if (gray !== this) gray.release()
            return points
        }
        contour.release()
        approx.release()
    }

    edges.release()
    blurred.release()
    if (gray !== this) gray.release()
    return null
}

fun Mat.toPoint2fArray(): Array<Point> {
    return this.toArray().map { Point(it.x, it.y) }.toTypedArray()
}

// ── Companion utilities ───────────────────────────────────────────────────────

object MatUtils {
    fun fromPoints(points: List<PointF>): MatOfPoint2f {
        val mat = MatOfPoint2f()
        mat.fromList(points.map { org.opencv.core.Point(it.x.toDouble(), it.y.toDouble()) })
        return mat
    }

    fun fromPoints(points: List<PointF>): MatOfPoint {
        val mat = MatOfPoint()
        mat.fromList(points.map { org.opencv.core.Point(it.x.toDouble(), it.y.toDouble()) })
        return mat
    }

    fun identity(size: Int): Mat {
        return Mat.eye(size, size, CvType.CV_64F)
    }

    fun zeros(rows: Int, cols: Int, type: Int = CvType.CV_64F): Mat {
        return Mat.zeros(rows, cols, type)
    }

    fun ones(rows: Int, cols: Int, type: Int = CvType.CV_64F): Mat {
        return Mat.ones(rows, cols, type)
    }

    fun fromByteArray(data: ByteArray, width: Int, height: Int, channels: Int = 3): Mat {
        val mat = Mat(height, width, if (channels == 1) CvType.CV_8UC1 else CvType.CV_8UC(channels))
        mat.put(0, 0, data)
        return mat
    }

    fun fromBitmap(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }
}
