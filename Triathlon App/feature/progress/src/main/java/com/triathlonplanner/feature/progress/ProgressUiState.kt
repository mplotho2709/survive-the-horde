package com.triathlonplanner.feature.progress

import com.triathlonplanner.core.model.AdaptationEvent
import com.triathlonplanner.core.model.WeeklyLoadPoint

data class ProgressUiState(
    val weeklyLoad: List<WeeklyLoadPoint> = emptyList(),
    val events: List<AdaptationEvent> = emptyList(),
    val isLoading: Boolean = true,
)
