package com.triathlonplanner.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "planned_workout",
    foreignKeys = [
        ForeignKey(
            entity = PlanWeekEntity::class,
            parentColumns = ["id"],
            childColumns = ["weekId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("weekId"), Index("dateEpochDay"), Index("status")],
)
data class PlannedWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekId: Long,
    val dateEpochDay: Long,
    val discipline: String,
    val workoutType: String,
    val title: String,
    val plannedDurationSec: Int,
    val plannedDistanceM: Int?,
    val plannedLoad: Int,
    val status: String,
    val zoneLevel: Int?,
    val brickGroupId: String?,
    val sortOrderInDay: Int,
    val substitutedDiscipline: String? = null,
)
