package com.triathlonplanner.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per active plan, keyed by [planId] directly (no separate autoGenerate id needed). */
@Entity(tableName = "rolling_load_state")
data class RollingLoadStateEntity(
    @PrimaryKey val planId: Long,
    val acuteLoad7d: Double,
    val chronicLoad28d: Double,
    val consecutiveMissedDays: Int,
    val lastEvaluatedDateEpochDay: Long?,
)
