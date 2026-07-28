package com.triathlonplanner.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "training_plan",
    foreignKeys = [
        ForeignKey(
            entity = RaceGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["raceGoalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("raceGoalId")],
)
data class TrainingPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val raceGoalId: Long,
    val startDateEpochDay: Long,
    val totalWeeks: Int,
    val status: String,
    val generatedAtEpochMilli: Long,
)
