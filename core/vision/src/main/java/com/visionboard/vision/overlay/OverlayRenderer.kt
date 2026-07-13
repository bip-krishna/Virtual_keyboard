package com.visionboard.vision.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import com.visionboard.vision.model.FingertipState
import com.visionboard.vision.model.FingertipType
import com.visionboard.vision.model.Landmark
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.opencv.core.Mat

@Singleton
class OverlayRenderer @Inject constructor() {

    private val keyboardOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_KEYBOARD_OUTLINE
        strokeWidth = OUTLINE_STROKE_WIDTH
    }

    private val keyboardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_KEYBOARD_FILL
    }

    private val keyOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_KEY_OUTLINE
        strokeWidth = KEY_OUTLINE_STROKE_WIDTH
    }

    private val keyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_KEY_FILL
    }

    private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_KEY_PRESSED
    }

    private val keyLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_KEY_LABEL
        textSize = KEY_LABEL_TEXT_SIZE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val fingertipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_FINGERTIP
    }

    private val velocityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_VELOCITY
        strokeWidth = VELOCITY_STROKE_WIDTH
    }

    private val skeletonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_SKELETON
        strokeWidth = SKELETON_STROKE_WIDTH
    }

    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_LANDMARK
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_GRID
        strokeWidth = GRID_STROKE_WIDTH
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }

    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_FPS_TEXT
        textSize = FPS_TEXT_SIZE
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }

    private val fpsBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_FPS_BACKGROUND
    }

    fun drawKeyboardOverlay(
        canvas: Canvas,
        corners: List<PointF>,
        keyPolygons: Map<String, List<PointF>>,
        detectedFingertips: List<FingertipState>,
        pressedKey: String?
    ) {
        if (corners.size < 4) return

        canvas.drawColor(Color.argb(20, 0, 0, 0))

        canvas.drawPath(
            createPolygonPath(corners),
            keyboardFillPaint
        )

        canvas.drawPath(
            createPolygonPath(corners),
            keyboardOutlinePaint
        )

        for ((label, polygon) in keyPolygons) {
            val mappedPolygon = mapPolygonToScreen(polygon, corners)
            if (mappedPolygon.size < 3) continue

            val fillPaint = if (label == pressedKey) keyPressedPaint else keyFillPaint

            canvas.drawPath(createPolygonPath(mappedPolygon), fillPaint)
            canvas.drawPath(createPolygonPath(mappedPolygon), keyOutlinePaint)

            val center = computePolygonCenter(mappedPolygon)
            val displayLabel = when {
                label == " " -> "SPACE"
                label.length > 1 -> label.uppercase()
                else -> label.uppercase()
            }
            canvas.drawText(displayLabel, center.x, center.y + keyLabelPaint.textSize / 3, keyLabelPaint)
        }

        for (fingertip in detectedFingertips) {
            drawFingertipMarker(canvas, fingertip)
        }
    }

    fun drawSkeleton(
        canvas: Canvas,
        landmarks: List<Landmark>
    ) {
        if (landmarks.isEmpty()) return

        for (landmark in landmarks) {
            canvas.drawCircle(
                landmark.x,
                landmark.y,
                LANDMARK_RADIUS,
                landmarkPaint
            )
        }

        val connections = listOf(
            0 to 1, 1 to 2
        )

        for ((startIdx, endIdx) in connections) {
            if (startIdx < landmarks.size && endIdx < landmarks.size) {
                canvas.drawLine(
                    landmarks[startIdx].x,
                    landmarks[startIdx].y,
                    landmarks[endIdx].x,
                    landmarks[endIdx].y,
                    skeletonPaint
                )
            }
        }
    }

    fun drawVelocityVectors(
        canvas: Canvas,
        fingertips: List<FingertipState>
    ) {
        for (fingertip in fingertips) {
            val velocityMag = kotlin.math.sqrt(
                fingertip.velocity.x * fingertip.velocity.x +
                    fingertip.velocity.y * fingertip.velocity.y
            )

            if (velocityMag < MIN_VELOCITY_THRESHOLD) continue

            val endX = fingertip.position.x + fingertip.velocity.x * VELOCITY_SCALE
            val endY = fingertip.position.y + fingertip.velocity.y * VELOCITY_SCALE

            velocityPaint.color = when (fingertip.type) {
                FingertipType.INDEX -> COLOR_VELOCITY_INDEX
                FingertipType.MIDDLE -> COLOR_VELOCITY_MIDDLE
                FingertipType.THUMB -> COLOR_VELOCITY_THUMB
            }

            canvas.drawLine(
                fingertip.position.x,
                fingertip.position.y,
                endX,
                endY,
                velocityPaint
            )

            drawArrowHead(canvas, fingertip.position.x, fingertip.position.y, endX, endY)
        }
    }

    fun drawHomographyGrid(
        canvas: Canvas,
        homography: Mat,
        corners: List<PointF>
    ) {
        if (homography.empty() || corners.size < 4) return

        val gridSize = GRID_DENSITY
        val stepX = 1.0f / gridSize
        val stepY = 1.0f / gridSize

        for (i in 0..gridSize) {
            val points = mutableListOf<PointF>()
            for (j in 0..gridSize) {
                val relX = j * stepX
                val relY = i * stepY
                val mapped = mapRelativePoint(relX, relY, corners)
                points.add(mapped)
            }
            val path = createPolylinePath(points)
            canvas.drawPath(path, gridPaint)
        }

        for (j in 0..gridSize) {
            val points = mutableListOf<PointF>()
            for (i in 0..gridSize) {
                val relX = j * stepX
                val relY = i * stepY
                val mapped = mapRelativePoint(relX, relY, corners)
                points.add(mapped)
            }
            val path = createPolylinePath(points)
            canvas.drawPath(path, gridPaint)
        }
    }

    fun drawFPS(
        canvas: Canvas,
        fps: Float,
        latency: Float
    ) {
        val text = "FPS: %.1f | Latency: %.1fms".format(fps, latency)
        val textWidth = fpsPaint.measureText(text)
        val padding = 16f
        val bgRect = RectF(
            12f,
            12f,
            12f + textWidth + padding * 2,
            12f + FPS_TEXT_SIZE + padding * 2
        )

        canvas.drawRoundRect(bgRect, 8f, 8f, fpsBackgroundPaint)
        canvas.drawText(text, 12f + padding, 12f + padding + FPS_TEXT_SIZE, fpsPaint)
    }

    private fun drawFingertipMarker(canvas: Canvas, fingertip: FingertipState) {
        fingertipPaint.color = when (fingertip.type) {
            FingertipType.THUMB -> COLOR_THUMB
            FingertipType.INDEX -> COLOR_INDEX
            FingertipType.MIDDLE -> COLOR_MIDDLE
        }

        canvas.drawCircle(
            fingertip.position.x,
            fingertip.position.y,
            FINGERTIP_RADIUS,
            fingertipPaint
        )

        if (fingertip.isDown) {
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = fingertipPaint.color
                strokeWidth = 3f
            }
            canvas.drawCircle(
                fingertip.position.x,
                fingertip.position.y,
                FINGERTIP_RADIUS + 6f,
                ringPaint
            )
        }
    }

    private fun drawArrowHead(
        canvas: Canvas,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float
    ) {
        val angle = kotlin.math.atan2((toY - fromY).toDouble(), (toX - fromX).toDouble())
        val arrowLen = 12f
        val arrowAngle = 0.5

        val x1 = toX - arrowLen * kotlin.math.cos(angle - arrowAngle).toFloat()
        val y1 = toY - arrowLen * kotlin.math.sin(angle - arrowAngle).toFloat()
        val x2 = toX - arrowLen * kotlin.math.cos(angle + arrowAngle).toFloat()
        val y2 = toY - arrowLen * kotlin.math.sin(angle + arrowAngle).toFloat()

        canvas.drawLine(toX, toY, x1, y1, velocityPaint)
        canvas.drawLine(toX, toY, x2, y2, velocityPaint)
    }

    private fun createPolygonPath(points: List<PointF>): android.graphics.Path {
        val path = android.graphics.Path()
        if (points.isEmpty()) return path

        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        path.close()
        return path
    }

    private fun createPolylinePath(points: List<PointF>): android.graphics.Path {
        val path = android.graphics.Path()
        if (points.isEmpty()) return path

        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        return path
    }

    private fun mapPolygonToScreen(
        relativePolygon: List<PointF>,
        keyboardCorners: List<PointF>
    ): List<PointF> {
        if (keyboardCorners.size < 4) return relativePolygon

        return relativePolygon.map { point ->
            mapRelativePoint(point.x, point.y, keyboardCorners)
        }
    }

    private fun mapRelativePoint(
        relX: Float,
        relY: Float,
        corners: List<PointF>
    ): PointF {
        val topLeft = corners[0]
        val topRight = corners[1]
        val bottomRight = corners[2]
        val bottomLeft = corners[3]

        val topX = topLeft.x + (topRight.x - topLeft.x) * relX
        val topY = topLeft.y + (topRight.y - topLeft.y) * relX
        val bottomX = bottomLeft.x + (bottomRight.x - bottomLeft.x) * relX
        val bottomY = bottomLeft.y + (bottomRight.y - bottomLeft.y) * relX

        val x = topX + (bottomX - topX) * relY
        val y = topY + (bottomY - topY) * relY

        return PointF(x, y)
    }

    private fun computePolygonCenter(polygon: List<PointF>): PointF {
        if (polygon.isEmpty()) return PointF(0f, 0f)
        val sumX = polygon.sumOf { it.x.toDouble() }.toFloat()
        val sumY = polygon.sumOf { it.y.toDouble() }.toFloat()
        return PointF(sumX / polygon.size, sumY / polygon.size)
    }

    companion object {
        private const val COLOR_KEYBOARD_OUTLINE = 0xCCFFFFFF.toInt()
        private const val COLOR_KEYBOARD_FILL = 0x40000000
        private const val COLOR_KEY_OUTLINE = 0x80FFFFFF.toInt()
        private const val COLOR_KEY_FILL = 0x30FFFFFF
        private const val COLOR_KEY_PRESSED = 0x6000CCFF.toInt()
        private const val COLOR_KEY_LABEL = 0xE0FFFFFF.toInt()

        private const val COLOR_FINGERTIP = 0xFF00CCFF.toInt()
        private const val COLOR_THUMB = 0xFFFF6644.toInt()
        private const val COLOR_INDEX = 0xFF00CCFF.toInt()
        private const val COLOR_MIDDLE = 0xFF44FF66.toInt()

        private const val COLOR_VELOCITY = 0xAAFFCC00.toInt()
        private const val COLOR_VELOCITY_INDEX = 0xFF00CCFF.toInt()
        private const val COLOR_VELOCITY_MIDDLE = 0xFF44FF66.toInt()
        private const val COLOR_VELOCITY_THUMB = 0xFFFF6644.toInt()

        private const val COLOR_SKELETON = 0xCC00FF88.toInt()
        private const val COLOR_LANDMARK = 0xFF00FF88.toInt()

        private const val COLOR_GRID = 0x40FFCC00.toInt()

        private const val COLOR_FPS_TEXT = 0xFF00FF00.toInt()
        private const val COLOR_FPS_BACKGROUND = 0xB0000000

        private const val OUTLINE_STROKE_WIDTH = 4f
        private const val KEY_OUTLINE_STROKE_WIDTH = 1.5f
        private const val VELOCITY_STROKE_WIDTH = 3f
        private const val SKELETON_STROKE_WIDTH = 2f
        private const val GRID_STROKE_WIDTH = 1f

        private const val KEY_LABEL_TEXT_SIZE = 18f
        private const val FPS_TEXT_SIZE = 28f

        private const val FINGERTIP_RADIUS = 12f
        private const val LANDMARK_RADIUS = 4f

        private const val VELOCITY_SCALE = 5f
        private const val MIN_VELOCITY_THRESHOLD = 2f

        private const val GRID_DENSITY = 10
    }
}
