package com.triathlonplanner.feature.plan

import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.TrainingPhase

data class PlanWeekUiModel(
    val weekIndex: Int,
    val phase: TrainingPhase,
    val isRecoveryWeek: Boolean,
    val plannedWeeklyLoad: Int,
    val workouts: List<PlannedWorkoutSnapshot>,
)

data class PlanUiState(
    val weeks: List<PlanWeekUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val hasActivePlan: Boolean = false,
)
