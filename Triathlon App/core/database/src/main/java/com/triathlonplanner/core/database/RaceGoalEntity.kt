package com.triathlonplanner.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "race_goal")
data class RaceGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val distance: String,
    val raceDateEpochDay: Long,
    val raceName: String?,
    val targetFinishTimeSec: Int?,
    val isActive: Boolean,
    val createdAtEpochMilli: Long,
    val targetWeeklyHours: Double? = null,
    val targetDaysPerWeek: Int? = null,
    val currentFitnessEstimateSec: Int? = null,
)
