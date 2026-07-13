package com.visionboard.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calibrations")
data class CalibrationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val name: String,

    @ColumnInfo(name = "layout_json")
    val layoutJson: String,

    @ColumnInfo(name = "homography_data")
    val homographyData: ByteArray,

    @ColumnInfo(name = "corners_json")
    val cornersJson: String,

    @ColumnInfo(name = "feature_count")
    val featureCount: Int,

    val confidence: Float,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalibrationEntity) return false
        return id == other.id &&
                name == other.name &&
                layoutJson == other.layoutJson &&
                homographyData.contentEquals(other.homographyData) &&
                cornersJson == other.cornersJson &&
                featureCount == other.featureCount &&
                confidence == other.confidence &&
                createdAt == other.createdAt &&
                updatedAt == other.updatedAt &&
                isActive == other.isActive
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + layoutJson.hashCode()
        result = 31 * result + homographyData.contentHashCode()
        result = 31 * result + cornersJson.hashCode()
        result = 31 * result + featureCount
        result = 31 * result + confidence.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + isActive.hashCode()
        return result
    }
}
