package com.visionboard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.visionboard.ui.theme.DetectedKey
import com.visionboard.ui.theme.FingerPosition
import com.visionboard.ui.theme.HomographyGrid
import com.visionboard.ui.theme.KeyboardHighlight
import com.visionboard.ui.theme.KeyPress
import com.visionboard.ui.theme.VelocityVector

data class KeyPolygon(
    val id: String,
    val label: String,
    val corners: List<Offset>,
    val isPressed: Boolean = false,
    val confidence: Float = 1f
)

data class Fingertip(
    val id: Int,
    val position: Offset,
    val velocity: Offset = Offset.Zero,
    val isActive: Boolean = true
)

data class KeyboardLayout(
    val keys: List<KeyPolygon>,
    val width: Float = 1080f,
    val height: Float = 480f
)

@Composable
fun KeyboardOverlayView(
    keyboardLayout: KeyboardLayout,
    pressedKey: String?,
    fingertips: List<Fingertip>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        drawGridLines(keyboardLayout)

        keyboardLayout.keys.forEach { key ->
            drawKeyPolygon(
                key = key,
                isHighlighted = key.id == pressedKey
            )
        }

        fingertips.filter { it.isActive }.forEach { fingertip ->
            drawFingertipMarker(fingertip)
            drawVelocityArrow(fingertip)
        }
    }
}

private fun DrawScope.drawGridLines(layout: KeyboardLayout) {
    val gridColor = HomographyGrid.copy(alpha = 0.3f)
    val strokeWidth = 1.dp.toPx()
    val spacing = 60.dp.toPx()

    var x = 0f
    while (x <= layout.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, layout.height),
            strokeWidth = strokeWidth
        )
        x += spacing
    }

    var y = 0f
    while (y <= layout.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(layout.width, y),
            strokeWidth = strokeWidth
        )
        y += spacing
    }
}

private fun DrawScope.drawKeyPolygon(
    key: KeyPolygon,
    isHighlighted: Boolean
) {
    if (key.corners.size < 3) return

    val path = Path().apply {
        moveTo(key.corners[0].x, key.corners[0].y)
        for (i in 1 until key.corners.size) {
            lineTo(key.corners[i].x, key.corners[i].y)
        }
        close()
    }

    val fillColor = when {
        isHighlighted -> KeyPress.copy(alpha = 0.4f)
        key.confidence > 0.8f -> DetectedKey.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val strokeColor = when {
        isHighlighted -> KeyPress
        key.confidence > 0.8f -> DetectedKey.copy(alpha = 0.6f)
        else -> HomographyGrid.copy(alpha = 0.5f)
    }

    drawPath(
        path = path,
        color = fillColor,
        style = Fill
    )

    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = if (isHighlighted) 3.dp.toPx() else 1.5.dp.toPx()
        )
    )

    val textSize = if (isHighlighted) 18.dp.toPx() else 14.dp.toPx()
    val textPaint = android.graphics.Paint().apply {
        color = if (isHighlighted) android.graphics.Color.WHITE else android.graphics.Color.LTGRAY
        this.textSize = textSize
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    val centerX = key.corners.map { it.x }.average().toFloat()
    val centerY = key.corners.map { it.y }.average().toFloat()

    drawContext.canvas.nativeCanvas.drawText(
        key.label,
        centerX,
        centerY + textSize / 3,
        textPaint
    )

    if (isHighlighted) {
        val glowPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(80, 66, 165, 245)
            style = android.graphics.Paint.Style.FILL
            maskFilter = android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), glowPaint)
    }
}

private fun DrawScope.drawFingertipMarker(fingertip: Fingertip) {
    val radius = 12.dp.toPx()
    val outerRadius = 20.dp.toPx()

    drawCircle(
        color = FingerPosition.copy(alpha = 0.2f),
        radius = outerRadius,
        center = fingertip.position
    )

    drawCircle(
        color = FingerPosition,
        radius = radius,
        center = fingertip.position,
        style = Stroke(width = 2.dp.toPx())
    )

    drawCircle(
        color = FingerPosition,
        radius = 4.dp.toPx(),
        center = fingertip.position
    )

    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 10.dp.toPx()
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    drawContext.canvas.nativeCanvas.drawText(
        "F${fingertip.id}",
        fingertip.position.x,
        fingertip.position.y - outerRadius - 4.dp.toPx(),
        labelPaint
    )
}

private fun DrawScope.drawVelocityArrow(fingertip: Fingertip) {
    if (fingertip.velocity == Offset.Zero) return

    val velocityMagnitude = fingertip.velocity.getDistance()
    if (velocityMagnitude < 5f) return

    val scale = 0.5f
    val endX = fingertip.position.x + fingertip.velocity.x * scale
    val endY = fingertip.position.y + fingertip.velocity.y * scale
    val end = Offset(endX, endY)

    drawLine(
        color = VelocityVector,
        start = fingertip.position,
        end = end,
        strokeWidth = 2.dp.toPx()
    )

    val angle = kotlin.math.atan2(
        fingertip.velocity.y.toDouble(),
        fingertip.velocity.x.toDouble()
    ).toFloat()
    val arrowLength = 8.dp.toPx()

    val arrowPath = Path().apply {
        moveTo(endX, endY)
        lineTo(
            endX - arrowLength * kotlin.math.cos(angle - 0.5f),
            endY - arrowLength * kotlin.math.sin(angle - 0.5f)
        )
        moveTo(endX, endY)
        lineTo(
            endX - arrowLength * kotlin.math.cos(angle + 0.5f),
            endY - arrowLength * kotlin.math.sin(angle + 0.5f)
        )
    }

    drawPath(
        path = arrowPath,
        color = VelocityVector,
        style = Stroke(width = 2.dp.toPx())
    )
}
