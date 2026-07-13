package com.visionboard.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.visionboard.database.entity.CalibrationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalibrationDao {

    @Query("SELECT * FROM calibrations ORDER BY updated_at DESC")
    fun getAllCalibrations(): Flow<List<CalibrationEntity>>

    @Query("SELECT * FROM calibrations WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveCalibration(): CalibrationEntity?

    @Query("SELECT * FROM calibrations WHERE id = :id")
    suspend fun getCalibrationById(id: Long): CalibrationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calibration: CalibrationEntity): Long

    @Update
    suspend fun update(calibration: CalibrationEntity)

    @Delete
    suspend fun delete(calibration: CalibrationEntity)

    @Query("DELETE FROM calibrations")
    suspend fun deleteAll()

    @Query("UPDATE calibrations SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE calibrations SET is_active = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun setActive(id: Long, timestamp: Long = System.currentTimeMillis())
}
