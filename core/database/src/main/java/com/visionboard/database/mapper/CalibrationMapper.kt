package com.visionboard.database.mapper

import android.graphics.PointF
import com.visionboard.common.models.CalibrationResult
import com.visionboard.common.models.KeyboardLayout
import com.visionboard.database.entity.CalibrationEntity
import kotlinx.serialization.json.Json
import org.opencv.core.Mat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun CalibrationResult.toEntity(name: String): CalibrationEntity {
    return CalibrationEntity(
        name = name,
        layoutJson = json.encodeToString(KeyboardLayout.serializer(), masterLayout),
        homographyData = homographyMatrix.toByteArray(),
        cornersJson = corners.serialize(),
        featureCount = featureCount,
        confidence = confidence,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        isActive = false,
    )
}

fun CalibrationEntity.toDomain(): CalibrationResult {
    return CalibrationResult(
        masterLayout = json.decodeFromString(KeyboardLayout.serializer(), layoutJson),
        homographyMatrix = homographyData.toMat(),
        corners = cornersJson.deserialize(),
        featureCount = featureCount,
        confidence = confidence,
    )
}

fun CalibrationEntity.toDomainOrNull(): CalibrationResult? {
    return try {
        toDomain()
    } catch (_: Exception) {
        null
    }
}

fun Mat.toByteArray(): ByteArray {
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)

    dos.writeInt(rows())
    dos.writeInt(cols())
    dos.writeInt(type())

    val totalBytes = total().toInt() * elemSize().toInt()
    val data = ByteArray(totalBytes)
    get(0, 0, data)
    dos.writeInt(data.size)
    dos.write(data)

    dos.flush()
    return baos.toByteArray()
}

fun ByteArray.toMat(): Mat {
    val bais = ByteArrayInputStream(this)
    val dis = DataInputStream(bais)

    val rows = dis.readInt()
    val cols = dis.readInt()
    val type = dis.readInt()

    val dataSize = dis.readInt()
    val data = ByteArray(dataSize)
    dis.readFully(data)

    val mat = Mat(rows, cols, type)
    mat.put(0, 0, data)
    return mat
}

fun List<PointF>.serialize(): String {
    return joinToString(";") { "${it.x},${it.y}" }
}

fun String.deserialize(): List<PointF> {
    if (isEmpty()) return emptyList()
    return split(";").map { part ->
        val coords = part.split(",")
        PointF(coords[0].toFloat(), coords[1].toFloat())
    }
}
