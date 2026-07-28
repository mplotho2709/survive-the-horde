package com.triathlonplanner.core.model

import java.time.LocalDate

/**
 * A minimal, persistence-agnostic view of a planned workout row, used as the AdaptationEngine's
 * input/output shape so :domain:planengine never has to depend on Room. The repository layer
 * maps Room entities to/from this shape.
 */
data class PlannedWorkoutSnapshot(
    val id: Long,
    val weekIndex: Int,
    val date: LocalDate,
    val discipline: Discipline,
    val workoutType: WorkoutType,
    val plannedDurationSec: Int,
    val plannedLoad: Int,
    val status: WorkoutStatus,
    val zone: IntensityZone? = null,
    val brickGroupId: String? = null,
    val sortOrderInDay: Int = 0,
)
