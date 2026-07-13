package com.visionboard.repository

import android.graphics.PointF
import com.visionboard.database.dao.CalibrationDao
import com.visionboard.database.entity.CalibrationEntity
import com.visionboard.domain.model.CalibrationDomain
import com.visionboard.domain.repository.CalibrationRepositoryInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalibrationRepositoryImpl @Inject constructor(
    private val calibrationDao: CalibrationDao,
) : CalibrationRepositoryInterface {

    override fun getActiveCalibration(): Flow<CalibrationDomain?> {
        return calibrationDao.getAllCalibrations().map { entities ->
            entities.firstOrNull { it.isActive }?.toDomain()
        }
    }

    override fun getAllCalibrations(): Flow<List<CalibrationDomain>> {
        return calibrationDao.getAllCalibrations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveCalibration(calibration: CalibrationDomain): Long {
        val entity = calibration.toEntity()
        return calibrationDao.insert(entity)
    }

    override suspend fun setActive(id: Long) {
        calibrationDao.deactivateAll()
        calibrationDao.setActive(id)
    }

    override suspend fun deleteCalibration(id: Long) {
        val calibration = calibrationDao.getCalibrationById(id) ?: return
        calibrationDao.delete(calibration)
    }

    override suspend fun deleteAll() {
        calibrationDao.deleteAll()
    }

    override suspend fun exportCalibration(id: Long): String {
        val calibration = calibrationDao.getCalibrationById(id)
            ?: throw IllegalArgumentException("Calibration not found: $id")
        val domain = calibration.toDomain()

        val cornersArray = JSONArray()
        domain.corners.forEach { point ->
            val cornerObj = JSONObject()
            cornerObj.put("x", point.x.toDouble())
            cornerObj.put("y", point.y.toDouble())
            cornersArray.put(cornerObj)
        }

        val root = JSONObject()
        root.put("name", domain.name)
        root.put("layout", domain.layout)
        root.put("corners", cornersArray)
        root.put("featureCount", domain.featureCount)
        root.put("confidence", domain.confidence.toDouble())
        root.put("createdAt", domain.createdAt)
        return root.toString(2)
    }

    override suspend fun importCalibration(json: String): Long {
        val root = JSONObject(json)

        val cornersArray = root.getJSONArray("corners")
        val corners = mutableListOf<PointF>()
        for (i in 0 until cornersArray.length()) {
            val cornerObj = cornersArray.getJSONObject(i)
            corners.add(PointF(cornerObj.getDouble("x").toFloat(), cornerObj.getDouble("y").toFloat()))
        }

        val domain = CalibrationDomain(
            name = root.getString("name"),
            layout = root.getString("layout"),
            corners = corners,
            featureCount = root.getInt("featureCount"),
            confidence = root.getDouble("confidence").toFloat(),
            createdAt = root.optLong("createdAt", System.currentTimeMillis()),
        )
        return saveCalibration(domain)
    }

    private fun CalibrationEntity.toDomain(): CalibrationDomain {
        val cornersList = try {
            val raw = cornersJson
            if (raw.isBlank()) emptyList()
            else raw.split(";").map { part ->
                val coords = part.split(",")
                PointF(coords[0].toFloat(), coords[1].toFloat())
            }
        } catch (_: Exception) {
            emptyList()
        }

        return CalibrationDomain(
            id = id,
            name = name,
            layout = layoutJson,
            corners = cornersList,
            featureCount = featureCount,
            confidence = confidence,
            createdAt = createdAt,
            isActive = isActive,
        )
    }

    private fun CalibrationDomain.toEntity(): CalibrationEntity {
        val cornersString = corners.joinToString(";") { "${it.x},${it.y}" }

        return CalibrationEntity(
            id = id,
            name = name,
            layoutJson = layout,
            homographyData = ByteArray(0),
            cornersJson = cornersString,
            featureCount = featureCount,
            confidence = confidence,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            isActive = isActive,
        )
    }
}
