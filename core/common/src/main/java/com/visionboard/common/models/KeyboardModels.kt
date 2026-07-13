package com.visionboard.common.models

import android.graphics.PointF
import android.graphics.RectF
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opencv.core.Mat

@Serializable
data class SerializablePointF(
    val x: Float,
    val y: Float,
) {
    fun toPointF(): PointF = PointF(x, y)

    companion object {
        fun fromPointF(point: PointF): SerializablePointF = SerializablePointF(point.x, point.y)
    }
}

object PointFSerializer : KSerializer<PointF> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PointF", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PointF) {
        encoder.encodeString("${value.x},${value.y}")
    }

    override fun deserialize(decoder: Decoder): PointF {
        val parts = decoder.decodeString().split(",")
        return PointF(parts[0].toFloat(), parts[1].toFloat())
    }
}

object RectFSerializer : KSerializer<RectF> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RectF", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RectF) {
        encoder.encodeString("${value.left},${value.top},${value.right},${value.bottom}")
    }

    override fun deserialize(decoder: Decoder): RectF {
        val parts = decoder.decodeString().split(",")
        return RectF(parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat())
    }
}

object MatSerializer : KSerializer<Mat> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Mat", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Mat) {
        val data = ByteArray(value.total().toInt() * value.channels())
        value.get(0, 0, data)
        encoder.encodeString(java.util.Base64.getEncoder().encodeToString(data))
    }

    override fun deserialize(decoder: Decoder): Mat {
        val data = java.util.Base64.getDecoder().decode(decoder.decodeString())
        val mat = Mat()
        mat.put(0, 0, data)
        return mat
    }
}

@Serializable
data class KeyData(
    val label: String,
    val hidKeycode: Int,
    @Serializable(with = PointFListSerializer::class)
    val polygon: List<PointF>,
    val neighbors: List<String>,
)

@Serializable
data class KeyboardLayout(
    val name: String,
    val keys: List<KeyData>,
    @Serializable(with = RectFSerializer::class)
    val bounds: RectF,
) {
    fun findKeyByLabel(label: String): KeyData? = keys.find { it.label == label }

    fun findKeyAtPoint(point: PointF, tolerance: Float = 30f): KeyData? {
        return keys.firstOrNull { key ->
            key.polygon.any { vertex ->
                val dx = vertex.x - point.x
                val dy = vertex.y - point.y
                Math.sqrt((dx * dx + dy * dy).toDouble()) <= tolerance
            } || isPointInPolygon(point, key.polygon)
        }
    }

    private fun isPointInPolygon(point: PointF, polygon: List<PointF>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            if (((yi > point.y) != (yj > point.y)) &&
                (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}

@Serializable
data class CalibrationResult(
    val masterLayout: KeyboardLayout,
    @Serializable(with = MatSerializer::class)
    val homographyMatrix: Mat,
    @Serializable(with = PointFListSerializer::class)
    val corners: List<PointF>,
    val featureCount: Int,
    val confidence: Float,
) {
    fun isValid(): Boolean =
        confidence >= 0.5f && featureCount >= 4 && !homographyMatrix.empty()

    fun transformPoint(point: PointF): PointF {
        if (homographyMatrix.empty()) return point
        val src = org.opencv.core.MatOfPoint2f(
            org.opencv.core.Point(point.x.toDouble(), point.y.toDouble())
        )
        val dst = org.opencv.core.MatOfPoint2f()
        org.opencv.core.Core.perspectiveTransform(src, dst, homographyMatrix)
        val result = dst.toArray()[0]
        dst.release()
        src.release()
        return PointF(result.x.toFloat(), result.y.toFloat())
    }
}

object PointFListSerializer : KSerializer<List<PointF>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PointFList", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: List<PointF>) {
        val encoded = value.joinToString(";") { "${it.x},${it.y}" }
        encoder.encodeString(encoded)
    }

    override fun deserialize(decoder: Decoder): List<PointF> {
        val encoded = decoder.decodeString()
        if (encoded.isEmpty()) return emptyList()
        return encoded.split(";").map { part ->
            val coords = part.split(",")
            PointF(coords[0].toFloat(), coords[1].toFloat())
        }
    }
}
