package com.triathlonplanner.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [healthConnectRecordId] is the dedupe key against re-processing the same Health Connect
 * session on repeated syncs. Raw HR/power time-series samples are deliberately never persisted
 * here - only aggregates and [calculatedLoad] - since Health Connect already owns that data.
 */
@Entity(
    tableName = "completed_activity",
    indices = [Index("healthConnectRecordId", unique = true), Index("startTimeEpochMilli"), Index("matchedPlannedWorkoutId")],
)
data class CompletedActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val healthConnectRecordId: String,
    val discipline: String,
    val startTimeEpochMilli: Long,
    val endTimeEpochMilli: Long,
    val distanceM: Double?,
    val avgHr: Int?,
    val maxHr: Int?,
    val avgPowerW: Int?,
    val normalizedPowerW: Int?,
    val calculatedLoad: Int,
    val matchedPlannedWorkoutId: Long?,
    val matchStatus: String,
)
