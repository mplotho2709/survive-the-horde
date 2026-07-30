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
    // Day preferences as 7-bit masks (bit 0 = Monday .. bit 6 = Sunday). A mask is cheaper and
    // less error-prone to migrate than a delimited string, and Room stores it as a plain INTEGER.
    // Null means the athlete never expressed a preference, which is distinct from "no days".
    val swimDaysMask: Int? = null,
    val bikeDaysMask: Int? = null,
    val runDaysMask: Int? = null,
    val longSessionDaysMask: Int? = null,
)
