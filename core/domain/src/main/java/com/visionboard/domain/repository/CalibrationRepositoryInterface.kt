package com.visionboard.domain.repository

import com.visionboard.domain.model.CalibrationDomain
import kotlinx.coroutines.flow.Flow

interface CalibrationRepositoryInterface {
    fun getActiveCalibration(): Flow<CalibrationDomain?>
    fun getAllCalibrations(): Flow<List<CalibrationDomain>>
    suspend fun saveCalibration(calibration: CalibrationDomain): Long
    suspend fun setActive(id: Long)
    suspend fun deleteCalibration(id: Long)
    suspend fun deleteAll()
    suspend fun exportCalibration(id: Long): String
    suspend fun importCalibration(json: String): Long
}
