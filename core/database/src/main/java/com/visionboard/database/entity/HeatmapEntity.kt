package com.visionboard.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "heatmap",
    indices = [Index(value = ["key_label"], unique = true)],
)
data class HeatmapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "key_label")
    val keyLabel: String,

    @ColumnInfo(name = "press_count")
    val pressCount: Int = 0,

    @ColumnInfo(name = "total_confidence")
    val totalConfidence: Float = 0f,

    @ColumnInfo(name = "last_pressed_at")
    val lastPressedAt: Long = 0L,

    @ColumnInfo(name = "average_latency_ms")
    val averageLatencyMs: Float = 0f,
)
