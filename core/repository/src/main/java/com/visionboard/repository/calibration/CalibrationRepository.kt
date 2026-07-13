package com.visionboard.repository.calibration

import android.content.Context
import android.graphics.PointF
import com.visionboard.domain.model.CalibrationDomain
import com.visionboard.domain.repository.CalibrationRepositoryInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class CalibrationJson(
    val id: Long,
    val name: String,
    val layout: String,
    val corners: List<List<Float>>,
    val featureCount: Int,
    val confidence: Float,
    val createdAt: Long,
    val isActive: Boolean,
)

@Singleton
class CalibrationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : CalibrationRepositoryInterface {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _calibrations = MutableStateFlow<List<CalibrationDomain>>(emptyList())
    private val _activeCalibration = MutableStateFlow<CalibrationDomain?>(null)

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        loadFromPrefs()
    }

    override fun getActiveCalibration(): Flow<CalibrationDomain?> {
        return _activeCalibration.asStateFlow()
    }

    override fun getAllCalibrations(): Flow<List<CalibrationDomain>> {
        return _calibrations.asStateFlow()
    }

    override suspend fun saveCalibration(calibration: CalibrationDomain): Long {
        return withContext(Dispatchers.IO) {
            val id = if (calibration.id == 0L) {
                generateId()
            } else {
                calibration.id
            }
            val saved = calibration.copy(id = id)
            val current = _calibrations.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.id == id }
            if (existingIndex >= 0) {
                current[existingIndex] = saved
            } else {
                current.add(saved)
            }
            _calibrations.value = current
            if (saved.isActive) {
                _activeCalibration.value = saved
            }
            saveToPrefs(current)
            id
        }
    }

    override suspend fun setActive(id: Long) {
        withContext(Dispatchers.IO) {
            val current = _calibrations.value.toMutableList()
            val updated = current.map { cal ->
                cal.copy(isActive = cal.id == id)
            }
            _calibrations.value = updated
            _activeCalibration.value = updated.find { it.id == id }
            saveToPrefs(updated)
        }
    }

    override suspend fun deleteCalibration(id: Long) {
        withContext(Dispatchers.IO) {
            val current = _calibrations.value.toMutableList()
            current.removeAll { it.id == id }
            _calibrations.value = current
            if (_activeCalibration.value?.id == id) {
                _activeCalibration.value = current.find { it.isActive }
                    ?: current.firstOrNull()
            }
            saveToPrefs(current)
        }
    }

    override suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            _calibrations.value = emptyList()
            _activeCalibration.value = null
            prefs.edit().remove(KEY_CALIBRATIONS).apply()
        }
    }

    override suspend fun exportCalibration(id: Long): String {
        return withContext(Dispatchers.IO) {
            val calibration = _calibrations.value.find { it.id == id }
                ?: throw IllegalArgumentException("Calibration with id $id not found")
            val jsonModel = CalibrationJson(
                id = calibration.id,
                name = calibration.name,
                layout = calibration.layout,
                corners = calibration.corners.map { listOf(it.x, it.y) },
                featureCount = calibration.featureCount,
                confidence = calibration.confidence,
                createdAt = calibration.createdAt,
                isActive = calibration.isActive,
            )
            json.encodeToString(jsonModel)
        }
    }

    override suspend fun importCalibration(jsonString: String): Long {
        return withContext(Dispatchers.IO) {
            val jsonModel = json.decodeFromString<CalibrationJson>(jsonString)
            val corners = jsonModel.corners.map { PointF(it[0], it[1]) }
            val calibration = CalibrationDomain(
                id = 0,
                name = jsonModel.name,
                layout = jsonModel.layout,
                corners = corners,
                featureCount = jsonModel.featureCount,
                confidence = jsonModel.confidence,
                createdAt = jsonModel.createdAt,
                isActive = false,
            )
            saveCalibration(calibration)
        }
    }

    private fun generateId(): Long {
        return System.currentTimeMillis()
    }

    private fun saveToPrefs(calibrations: List<CalibrationDomain>) {
        val jsonArray = JSONArray()
        for (cal in calibrations) {
            val cornersArray = JSONArray()
            for (corner in cal.corners) {
                val cornerObj = JSONObject().apply {
                    put("x", corner.x.toDouble())
                    put("y", corner.y.toDouble())
                }
                cornersArray.put(cornerObj)
            }
            val obj = JSONObject().apply {
                put("id", cal.id)
                put("name", cal.name)
                put("layout", cal.layout)
                put("corners", cornersArray)
                put("featureCount", cal.featureCount)
                put("confidence", cal.confidence.toDouble())
                put("createdAt", cal.createdAt)
                put("isActive", cal.isActive)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CALIBRATIONS, jsonArray.toString()).apply()
    }

    private fun loadFromPrefs() {
        val raw = prefs.getString(KEY_CALIBRATIONS, null) ?: return
        try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<CalibrationDomain>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val cornersArray = obj.getJSONArray("corners")
                val corners = mutableListOf<PointF>()
                for (j in 0 until cornersArray.length()) {
                    val cornerObj = cornersArray.getJSONObject(j)
                    corners.add(PointF(cornerObj.getDouble("x").toFloat(), cornerObj.getDouble("y").toFloat()))
                }
                list.add(
                    CalibrationDomain(
                        id = obj.getLong("id"),
                        name = obj.getString("name"),
                        layout = obj.getString("layout"),
                        corners = corners,
                        featureCount = obj.getInt("featureCount"),
                        confidence = obj.getDouble("confidence").toFloat(),
                        createdAt = obj.getLong("createdAt"),
                        isActive = obj.getBoolean("isActive"),
                    )
                )
            }
            _calibrations.value = list
            _activeCalibration.value = list.find { it.isActive }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val PREFS_NAME = "visionboard_calibrations"
        private const val KEY_CALIBRATIONS = "calibrations_json"
    }
}
