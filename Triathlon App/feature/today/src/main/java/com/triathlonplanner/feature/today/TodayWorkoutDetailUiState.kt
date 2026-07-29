package com.triathlonplanner.feature.today

import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.GeneratedWorkoutStep
import com.triathlonplanner.core.model.MatchStatus
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutStepType
import com.triathlonplanner.data.repository.ZoneKind
import com.triathlonplanner.data.repository.ZoneResolver

data class ActualWorkoutView(
    val discipline: Discipline,
    val durationMin: Int,
    val distanceM: Double?,
    val avgHr: Int?,
    val avgPowerW: Int?,
    val calculatedLoad: Int,
    val matchStatus: MatchStatus,
)

data class StepView(
    val stepType: WorkoutStepType,
    val durationMin: Int,
    val zoneLabel: String?,
    val repeatCount: Int?,
    val cueText: String?,
)

data class TodayWorkoutDetailUiState(
    val planned: TodayWorkoutView? = null,
    val steps: List<StepView> = emptyList(),
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

fun GeneratedWorkoutStep.toStepView(discipline: Discipline, profile: UserZoneProfile?): StepView {
    val resolved = intensityZone?.let { ZoneResolver.resolve(discipline, it, profile) }
    val zoneLabel = when (resolved?.kind) {
        null -> null
        ZoneKind.PACE -> "Zone ${resolved.range.zone.level} (${formatPace(resolved.range.upperBound)}-${formatPace(resolved.range.lowerBound)} /100m)"
        ZoneKind.POWER -> "Zone ${resolved.range.zone.level} (${resolved.range.lowerBound}-${resolved.range.upperBound} W)"
        ZoneKind.HEART_RATE -> "Zone ${resolved.range.zone.level} (${resolved.range.lowerBound}-${resolved.range.upperBound} bpm)"
    }
    return StepView(
        stepType = stepType,
        durationMin = (durationSec ?: 0) / 60,
        zoneLabel = zoneLabel,
        repeatCount = repeatCount,
        cueText = cueText,
    )
}

private fun formatPace(secPer100m: Int): String {
    val minutes = secPer100m / 60
    val seconds = secPer100m % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
