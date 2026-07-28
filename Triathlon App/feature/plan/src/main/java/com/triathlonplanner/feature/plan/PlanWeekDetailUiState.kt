package com.triathlonplanner.feature.plan

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutType
import com.triathlonplanner.data.repository.ZoneKind
import com.triathlonplanner.data.repository.ZoneResolver
import java.time.LocalDate

data class PlanWorkoutDetailView(
    val id: Long,
    val date: LocalDate,
    val discipline: Discipline,
    val workoutType: WorkoutType,
    val status: WorkoutStatus,
    val durationMin: Int,
    val plannedLoad: Int,
    val zoneLabel: String?,
)

data class PlanWeekDetailUiState(
    val weekIndex: Int = 0,
    val phase: TrainingPhase? = null,
    val isRecoveryWeek: Boolean = false,
    val plannedWeeklyLoad: Int = 0,
    val workouts: List<PlanWorkoutDetailView> = emptyList(),
    val isLoading: Boolean = true,
)

fun PlannedWorkoutSnapshot.toDetailView(profile: UserZoneProfile?): PlanWorkoutDetailView {
    val resolved = ZoneResolver.resolve(discipline, zone, profile)
    val zoneLabel = when (resolved?.kind) {
        null -> null
        ZoneKind.PACE -> "Zone ${resolved.range.zone.level} (${formatPace(resolved.range.upperBound)}-${formatPace(resolved.range.lowerBound)} /100m)"
        ZoneKind.POWER -> "Zone ${resolved.range.zone.level} (${resolved.range.lowerBound}-${resolved.range.upperBound} W)"
        ZoneKind.HEART_RATE -> "Zone ${resolved.range.zone.level} (${resolved.range.lowerBound}-${resolved.range.upperBound} bpm)"
    }
    return PlanWorkoutDetailView(
        id = id,
        date = date,
        discipline = discipline,
        workoutType = workoutType,
        status = status,
        durationMin = plannedDurationSec / 60,
        plannedLoad = plannedLoad,
        zoneLabel = zoneLabel,
    )
}

private fun formatPace(secPer100m: Int): String {
    val minutes = secPer100m / 60
    val seconds = secPer100m % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
