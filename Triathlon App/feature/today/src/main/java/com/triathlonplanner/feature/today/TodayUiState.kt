package com.triathlonplanner.feature.today

import com.triathlonplanner.core.designsystem.WorkoutLegView
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutType

data class TodayWorkoutView(
    val id: Long,
    val title: String,
    val discipline: Discipline,
    val workoutType: WorkoutType,
    val status: WorkoutStatus,
    val durationMin: Int,
    val zoneLabel: String?,
)

data class TodayUiState(
    val workouts: List<TodayWorkoutView> = emptyList(),
    // Populated only when there's exactly one workout today, so Today can show the full
    // planned/actual breakdown directly instead of requiring a tap into a separate detail screen -
    // the same shared view the Plan tab's day panel renders, so a substitution looks identical.
    val singleWorkoutDetail: WorkoutLegView? = null,
    val isLoading: Boolean = true,
)
