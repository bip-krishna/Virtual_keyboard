package com.visionboard.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.visionboard.database.dao.CalibrationDao
import com.visionboard.database.dao.StatsDao
import com.visionboard.database.entity.CalibrationEntity
import com.visionboard.database.entity.HeatmapEntity
import com.visionboard.database.entity.TypingStatsEntity

@Database(
    entities = [
        CalibrationEntity::class,
        TypingStatsEntity::class,
        HeatmapEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VisionBoardDatabase : RoomDatabase() {

    abstract fun calibrationDao(): CalibrationDao

    abstract fun statsDao(): StatsDao

    companion object {
        const val DATABASE_NAME = "visionboard_database"
    }
}
