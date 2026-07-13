package com.visionboard.vision.keyboard

import android.graphics.PointF
import com.visionboard.vision.model.KeyData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyPolygonMapper @Inject constructor() {

    private val keyLayout: Map<String, KeyData> = KeyboardLayouts.usQwerty()

    fun getKeyPressed(fingertipPosition: PointF): KeyData? {
        for ((_, keyData) in keyLayout) {
            if (isPointInPolygon(fingertipPosition, keyData.relativePolygon)) {
                return keyData
            }
        }
        return null
    }

    fun getAllKeyPolygons(): Map<String, List<PointF>> {
        return keyLayout.mapValues { it.value.relativePolygon }
    }

    fun isPointInPolygon(point: PointF, polygon: List<PointF>): Boolean {
        if (polygon.size < 3) return false

        var inside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y

            val intersect = ((yi > point.y) != (yj > point.y)) &&
                (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)

            if (intersect) {
                inside = !inside
            }

            j = i
        }

        return inside
    }

    fun getNeighbors(keyLabel: String): List<KeyData> {
        val key = keyLayout[keyLabel.uppercase()] ?: return emptyList()
        return key.neighbors.mapNotNull { keyLayout[it] }
    }

    fun findNearestKey(point: PointF): KeyData? {
        var bestKey: KeyData? = null
        var bestDist = Float.MAX_VALUE

        for ((_, keyData) in keyLayout) {
            val center = computePolygonCenter(keyData.relativePolygon)
            val dx = point.x - center.x
            val dy = point.y - center.y
            val dist = dx * dx + dy * dy

            if (dist < bestDist) {
                bestDist = dist
                bestKey = keyData
            }
        }

        return bestKey
    }

    fun getKeyByLabel(label: String): KeyData? {
        return keyLayout[label.uppercase()]
    }

    fun mapToKeyboardCoords(
        screenX: Float,
        screenY: Float,
        keyboardTopLeft: PointF,
        keyboardBottomRight: PointF
    ): PointF {
        val kbWidth = keyboardBottomRight.x - keyboardTopLeft.x
        val kbHeight = keyboardBottomRight.y - keyboardTopLeft.y
        if (kbWidth <= 0 || kbHeight <= 0) return PointF(0f, 0f)

        val relX = (screenX - keyboardTopLeft.x) / kbWidth
        val relY = (screenY - keyboardTopLeft.y) / kbHeight

        return PointF(relX.coerceIn(0f, 1f), relY.coerceIn(0f, 1f))
    }

    private fun computePolygonCenter(polygon: List<PointF>): PointF {
        if (polygon.isEmpty()) return PointF(0f, 0f)
        val sumX = polygon.sumOf { it.x.toDouble() }.toFloat()
        val sumY = polygon.sumOf { it.y.toDouble() }.toFloat()
        return PointF(sumX / polygon.size, sumY / polygon.size)
    }
}
