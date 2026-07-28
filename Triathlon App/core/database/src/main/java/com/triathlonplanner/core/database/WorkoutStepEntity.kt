package com.triathlonplanner.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_step",
    foreignKeys = [
        ForeignKey(
            entity = PlannedWorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannedWorkoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("plannedWorkoutId")],
)
data class WorkoutStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plannedWorkoutId: Long,
    val stepOrder: Int,
    val stepType: String,
    val durationSec: Int?,
    val distanceM: Int?,
    val intensityZoneLevel: Int?,
    val repeatCount: Int?,
)
