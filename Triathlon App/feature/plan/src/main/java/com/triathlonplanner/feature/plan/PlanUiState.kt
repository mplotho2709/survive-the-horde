package com.triathlonplanner.feature.plan

import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.GeneratedWorkoutStep
import com.triathlonplanner.core.model.MatchStatus
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutStepType
import com.triathlonplanner.data.repository.ResolvedZone
import com.triathlonplanner.data.repository.ZoneKind
import com.triathlonplanner.data.repository.ZoneResolver
import java.time.LocalDate
import java.time.YearMonth

data class PlanStepView(
    val stepType: WorkoutStepType,
    val durationMin: Int,
    val zoneLabel: String?,
    val repeatCount: Int?,
    val cueText: String?,
)

data class PlanActualWorkoutView(
    val discipline: Discipline,
    val disciplineLabel: String,
    val durationMin: Int,
    val distanceM: Double?,
    val avgHr: Int?,
    val avgPowerW: Int?,
    val calculatedLoad: Int,
    val matchStatus: MatchStatus,
)

/** One underlying planned workout row within a day - usually the whole day, but a brick day has
 * two (bike leg + run leg). Carries the full planned structure (steps/cues/zones) and whatever
 * completed activity matched it, so the Plan tab can show full detail directly with no extra tap. */
data class DayLegView(
    val workoutId: Long,
    val title: String,
    val disciplineLabel: String,
    val durationMin: Int,
    val plannedLoad: Int,
    val zoneLabel: String?,
    val status: WorkoutStatus,
    val steps: List<PlanStepView> = emptyList(),
    val actual: PlanActualWorkoutView? = null,
)

/** One calendar day's training, collapsed into a single unit even on brick days (bike + run
 * legs) - the Plan tab is day-based, not workout-based. */
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

fun PlannedWorkoutSnapshot.toLegView(
    profile: UserZoneProfile?,
    steps: List<GeneratedWorkoutStep>,
    activity: CompletedActivity?,
): DayLegView {
    val zoneLabel = zoneLabelFor(ZoneResolver.resolve(discipline, zone, profile))
    return DayLegView(
        workoutId = id,
        title = "${workoutType.name.lowercase().replaceFirstChar(Char::uppercase)} ${disciplineLabel(discipline)}",
        disciplineLabel = disciplineLabel(discipline),
        durationMin = plannedDurationSec / 60,
        plannedLoad = plannedLoad,
        zoneLabel = zoneLabel,
        status = status,
        steps = steps.sortedBy { it.stepOrder }.map { it.toPlanStepView(discipline, profile) },
        actual = activity?.toPlanActualView(),
    )
}

fun List<DayLegView>.toDayPlanView(date: LocalDate): DayPlanView {
    val title = if (size > 1) "Brick: " + joinToString(" + ") { it.disciplineLabel } else single().title
    return DayPlanView(
        date = date,
        title = title,
        totalDurationMin = sumOf { it.durationMin },
        totalLoad = sumOf { it.plannedLoad },
        legs = this,
    )
}

private fun GeneratedWorkoutStep.toPlanStepView(discipline: Discipline, profile: UserZoneProfile?): PlanStepView {
    val resolved = intensityZone?.let { ZoneResolver.resolve(discipline, it, profile) }
    return PlanStepView(
        stepType = stepType,
        durationMin = (durationSec ?: 0) / 60,
        zoneLabel = zoneLabelFor(resolved),
        repeatCount = repeatCount,
        cueText = cueText,
    )
}

private fun CompletedActivity.toPlanActualView(): PlanActualWorkoutView = PlanActualWorkoutView(
    discipline = discipline,
    disciplineLabel = disciplineLabel(discipline),
    durationMin = (durationSec / 60).toInt(),
    distanceM = distanceM,
    avgHr = avgHr,
    avgPowerW = avgPowerW,
    calculatedLoad = calculatedLoad,
    matchStatus = matchStatus,
)

private fun zoneLabelFor(resolved: ResolvedZone?): String? = when (resolved?.kind) {
    null -> null
    ZoneKind.PACE -> "Zone ${resolved.range.zone.level} (${formatPace(resolved.range.upperBound)}-${formatPace(resolved.range.lowerBound)} /100m)"
    ZoneKind.POWER -> "Zone ${resolved.range.zone.level} (${resolved.range.lowerBound}-${resolved.range.upperBound} W)"
    ZoneKind.HEART_RATE -> "Zone ${resolved.range.zone.level} (${resolved.range.lowerBound}-${resolved.range.upperBound} bpm)"
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
