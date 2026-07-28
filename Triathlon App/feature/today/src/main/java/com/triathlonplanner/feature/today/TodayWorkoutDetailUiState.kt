package com.triathlonplanner.feature.today

import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.MatchStatus

data class ActualWorkoutView(
    val discipline: Discipline,
    val durationMin: Int,
    val distanceM: Double?,
    val avgHr: Int?,
    val avgPowerW: Int?,
    val calculatedLoad: Int,
    val matchStatus: MatchStatus,
)

data class TodayWorkoutDetailUiState(
    val planned: TodayWorkoutView? = null,
    val actual: ActualWorkoutView? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
)

fun CompletedActivity.toActualView(): ActualWorkoutView = ActualWorkoutView(
    discipline = discipline,
    durationMin = (durationSec / 60).toInt(),
    distanceM = distanceM,
    avgHr = avgHr,
    avgPowerW = avgPowerW,
    calculatedLoad = calculatedLoad,
    matchStatus = matchStatus,
)
