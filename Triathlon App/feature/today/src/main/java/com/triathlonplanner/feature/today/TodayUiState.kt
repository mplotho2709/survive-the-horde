package com.triathlonplanner.feature.today

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
    val isLoading: Boolean = true,
)
