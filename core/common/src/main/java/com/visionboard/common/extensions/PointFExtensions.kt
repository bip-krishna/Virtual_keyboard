package com.visionboard.common.extensions

import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

fun PointF.distanceTo(other: PointF): Float {
    val dx = x - other.x
    val dy = y - other.y
    return hypot(dx, dy)
}

fun PointF.midpoint(other: PointF): PointF {
    return PointF((x + other.x) / 2f, (y + other.y) / 2f)
}

fun PointF.velocity(previous: PointF, dtMs: Long): PointF {
    val dt = (dtMs / 1000f).coerceAtLeast(0.001f)
    return PointF((x - previous.x) / dt, (y - previous.y) / dt)
}

fun PointF.scale(factor: Float): PointF {
    return PointF(x * factor, y * factor)
}

fun PointF.scale(sx: Float, sy: Float): PointF {
    return PointF(x * sx, y * sy)
}

fun PointF.rotate(degrees: Float, pivot: PointF = PointF(0f, 0f)): PointF {
    val radians = Math.toRadians(degrees.toDouble()).toFloat()
    val cosA = cos(radians)
    val sinA = sin(radians)
    val dx = x - pivot.x
    val dy = y - pivot.y
    return PointF(
        pivot.x + dx * cosA - dy * sinA,
        pivot.y + dx * sinA + dy * cosA
    )
}

fun PointF.angleWith(other: PointF): Float {
    val dx = other.x - x
    val dy = other.y - y
    return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
}

fun PointF.reflect(axisX: Float, axisY: Float): PointF {
    return PointF(2f * axisX - x, 2f * axisY - y)
}

fun PointF.clamp(minX: Float, minY: Float, maxX: Float, maxY: Float): PointF {
    return PointF(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
}

fun PointF.clamp(bounds: android.graphics.RectF): PointF {
    return PointF(x.coerceIn(bounds.left, bounds.right), y.coerceIn(bounds.top, bounds.bottom))
}

fun PointF.toOpenCVPoint(): org.opencv.core.Point {
    return org.opencv.core.Point(x.toDouble(), y.toDouble())
}

fun PointF.toOpenCVPoint2f(): org.opencv.core.Point {
    return org.opencv.core.Point(x.toDouble(), y.toDouble())
}

fun PointF.isNull(): Boolean = x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite()

fun PointF.isZero(): Boolean = x == 0f && y == 0f

fun PointF.length(): Float = hypot(x, y)

fun PointF.normalize(): PointF {
    val len = length()
    return if (len > 0f) PointF(x / len, y / len) else PointF(0f, 0f)
}

fun PointF.dot(other: PointF): Float = x * other.x + y * other.y

fun PointF.cross(other: PointF): Float = x * other.y - y * other.x

fun PointF.angleTo(other: PointF): Float {
    return Math.toDegrees(atan2(
        (other.y - y).toDouble(),
        (other.x - x).toDouble()
    )).toFloat()
}

fun PointF.lerp(target: PointF, fraction: Float): PointF {
    return PointF(
        x + (target.x - x) * fraction,
        y + (target.y - y) * fraction
    )
}

fun PointF.copyWith(x: Float = this.x, y: Float = this.y): PointF = PointF(x, y)

operator fun PointF.plus(other: PointF): PointF = PointF(x + other.x, y + other.y)

operator fun PointF.minus(other: PointF): PointF = PointF(x - other.x, y - other.y)

operator fun PointF.times(scalar: Float): PointF = PointF(x * scalar, y * scalar)

operator fun PointF.times(other: PointF): PointF = PointF(x * other.x, y * other.y)

operator fun PointF.div(scalar: Float): PointF {
    return if (scalar != 0f) PointF(x / scalar, y / scalar) else PointF(0f, 0f)
}

operator fun PointF.div(other: PointF): PointF {
    return PointF(
        if (other.x != 0f) x / other.x else 0f,
        if (other.y != 0f) y / other.y else 0f
    )
}

operator fun PointF.unaryMinus(): PointF = PointF(-x, -y)

fun List<PointF>.centroid(): PointF {
    if (isEmpty()) return PointF(0f, 0f)
    val sumX = sumOf { it.x.toDouble() }.toFloat()
    val sumY = sumOf { it.y.toDouble() }.toFloat()
    return PointF(sumX / size, sumY / size)
}

fun List<PointF>.boundingRect(): android.graphics.RectF {
    if (isEmpty()) return android.graphics.RectF(0f, 0f, 0f, 0f)
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    for (point in this) {
        if (point.x < minX) minX = point.x
        if (point.y < minY) minY = point.y
        if (point.x > maxX) maxX = point.x
        if (point.y > maxY) maxY = point.y
    }
    return android.graphics.RectF(minX, minY, maxX, maxY)
}

fun List<PointF>.averagePoint(): PointF {
    if (isEmpty()) return PointF(0f, 0f)
    val sumX = sumOf { it.x.toDouble() }.toFloat()
    val sumY = sumOf { it.y.toDouble() }.toFloat()
    return PointF(sumX / size, sumY / size)
}

fun org.opencv.core.Point.toPointF(): PointF = PointF(x.toFloat(), y.toFloat())
