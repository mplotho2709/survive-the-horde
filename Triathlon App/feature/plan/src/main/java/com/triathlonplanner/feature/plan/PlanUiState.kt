package com.triathlonplanner.feature.plan

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.data.repository.ZoneKind
import com.triathlonplanner.data.repository.ZoneResolver
import java.time.LocalDate
import java.time.YearMonth

data class DayLegView(
    val workoutId: Long,
    val disciplineLabel: String,
    val durationMin: Int,
    val zoneLabel: String?,
    val status: WorkoutStatus,
)

/** One calendar day's training, collapsed into a single unit even on brick days (bike + run
 * legs) - the Plan tab is day-based, not workout-based. Per-leg detail is still reachable by
 * tapping into a specific leg. */
data class DayPlanView(
    val date: LocalDate,
    val title: String,
    val totalDurationMin: Int,
    val totalLoad: Int,
    val legs: List<DayLegView>,
)

/** Drives the calendar grid cell for one date - [hasTraining] is a plain boolean rather than a
 * leg count, since the grid should read as "training day" vs "rest day", not leak the
 * workout-based storage model (e.g. 2 rows for a brick day) back into the day-based UI. */
data class CalendarDayInfo(
    val phase: TrainingPhase,
    val isRecoveryWeek: Boolean,
    val hasTraining: Boolean,
)

data class PlanUiState(
    val hasActivePlan: Boolean = false,
    val isLoading: Boolean = true,
    val visibleMonth: YearMonth = YearMonth.now(),
    val dayInfo: Map<LocalDate, CalendarDayInfo> = emptyMap(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDay: DayPlanView? = null,
)

fun List<PlannedWorkoutSnapshot>.toDayPlanView(date: LocalDate, profile: UserZoneProfile?): DayPlanView {
    val sorted = sortedBy { it.sortOrderInDay }
    val legs = sorted.map { workout ->
        val resolved = ZoneResolver.resolve(workout.discipline, workout.zone, profile)
        val zoneLabel = when (resolved?.kind) {
            null -> null
            ZoneKind.PACE -> "Zone ${resolved.range.zone.level} (${formatPace(resolved.range.upperBound)}-${formatPace(resolved.range.lowerBound)} /100m)"
            ZoneKind.POWER -> "Zone ${resolved.range.zone.level} (${resolved.range.lowerBound}-${resolved.range.upperBound} W)"
            ZoneKind.HEART_RATE -> "Zone ${resolved.range.zone.level} (${resolved.range.lowerBound}-${resolved.range.upperBound} bpm)"
        }
        DayLegView(
            workoutId = workout.id,
            disciplineLabel = disciplineLabel(workout.discipline),
            durationMin = workout.plannedDurationSec / 60,
            zoneLabel = zoneLabel,
            status = workout.status,
        )
    }
    val title = if (legs.size > 1) {
        "Brick: " + legs.joinToString(" + ") { it.disciplineLabel }
    } else {
        val single = sorted.single()
        "${single.workoutType.name.lowercase().replaceFirstChar(Char::uppercase)} ${disciplineLabel(single.discipline)}"
    }
    return DayPlanView(
        date = date,
        title = title,
        totalDurationMin = sorted.sumOf { it.plannedDurationSec } / 60,
        totalLoad = sorted.sumOf { it.plannedLoad },
        legs = legs,
    )
}

private fun disciplineLabel(discipline: Discipline): String = when (discipline) {
    Discipline.BRICK_BIKE -> "Bike"
    Discipline.BRICK_RUN -> "Run"
    else -> discipline.name.lowercase().replaceFirstChar(Char::uppercase)
}

private fun formatPace(secPer100m: Int): String {
    val minutes = secPer100m / 60
    val seconds = secPer100m % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
