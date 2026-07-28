package com.triathlonplanner.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plan_week",
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId"), Index("planId", "weekIndex", unique = true)],
)
data class PlanWeekEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val weekIndex: Int,
    val startDateEpochDay: Long,
    val phase: String,
    val isRecoveryWeek: Boolean,
    val plannedWeeklyLoad: Int,
)
