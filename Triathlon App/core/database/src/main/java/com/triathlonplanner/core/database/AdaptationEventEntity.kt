package com.triathlonplanner.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** User-facing audit trail row - shown as "Why did my plan change?" */
@Entity(tableName = "adaptation_event", indices = [Index("timestampEpochMilli")])
data class AdaptationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMilli: Long,
    val triggerType: String,
    val actionTaken: String,
    val description: String,
    val relatedPlannedWorkoutId: Long?,
)
