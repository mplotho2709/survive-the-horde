package com.triathlonplanner.feature.today

import com.triathlonplanner.core.designsystem.WorkoutLegView

data class TodayWorkoutDetailUiState(
    val legDetail: WorkoutLegView? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
)
