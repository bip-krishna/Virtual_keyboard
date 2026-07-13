package com.visionboard.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "typing_stats")
data class TypingStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val date: Long,

    @ColumnInfo(name = "total_key_presses")
    val totalKeyPresses: Int,

    @ColumnInfo(name = "unique_keys")
    val uniqueKeys: Int,

    @ColumnInfo(name = "average_latency_ms")
    val averageLatencyMs: Float,

    @ColumnInfo(name = "average_confidence")
    val averageConfidence: Float,

    @ColumnInfo(name = "typing_speed_wpm")
    val typingSpeedWpm: Float,

    val accuracy: Float,

    @ColumnInfo(name = "session_duration_ms")
    val sessionDurationMs: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
