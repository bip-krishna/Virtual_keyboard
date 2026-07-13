package com.visionboard.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.visionboard.database.entity.HeatmapEntity
import com.visionboard.database.entity.TypingStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Query("SELECT * FROM typing_stats WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getStatsByDateRange(start: Long, end: Long): Flow<List<TypingStatsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: TypingStatsEntity): Long

    @Query("SELECT * FROM typing_stats")
    suspend fun getTotalStats(): List<TypingStatsEntity>

    @Query("SELECT * FROM heatmap ORDER BY press_count DESC")
    fun getHeatmapData(): Flow<List<HeatmapEntity>>

    @Query("SELECT * FROM heatmap ORDER BY press_count DESC LIMIT :limit")
    fun getTopKeys(limit: Int): Flow<List<HeatmapEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHeatmapEntry(entry: HeatmapEntity): Long

    @Query(
        """
        INSERT INTO heatmap (key_label, press_count, total_confidence, last_pressed_at, average_latency_ms)
        VALUES (:keyLabel, :increment, :confidence, :timestamp, :latencyMs)
        ON CONFLICT(key_label) DO UPDATE SET
            press_count = press_count + :increment,
            total_confidence = total_confidence + :confidence,
            last_pressed_at = :timestamp,
            average_latency_ms = (
                (average_latency_ms * (press_count - :increment) + :latencyMs * :increment) / press_count
            )
        """
    )
    suspend fun updateHeatmap(
        keyLabel: String,
        increment: Int,
        confidence: Float,
        timestamp: Long,
        latencyMs: Float,
    )

    @Query("DELETE FROM heatmap")
    suspend fun deleteAllHeatmapData()
}
