package com.triathlonplanner.feature.plan

import com.triathlonplanner.core.designsystem.ActualWorkoutView
import com.triathlonplanner.core.designsystem.WorkoutLegView
import com.triathlonplanner.core.designsystem.WorkoutStepView
import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.GeneratedWorkoutStep
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.data.repository.ResolvedZone
import com.triathlonplanner.data.repository.ZoneKind
import com.triathlonplanner.data.repository.ZoneResolver
import java.time.LocalDate
import java.time.YearMonth

/** One underlying planned workout row within a day - usually the whole day, but a brick day has
 * two (bike leg + run leg). Carries the full planned structure (steps/cues/zones) and whatever
 * completed activity matched it, so the Plan tab can show full detail directly with no extra tap.
 * [detail] is the shared, renderable view - also used as-is by the Today tab. */
data class DayLegView(
    val workoutId: Long,
    val title: String,
    val discipline: Discipline,
    val disciplineLabel: String,
    val durationMin: Int,
    val plannedLoad: Int,
    val zoneLabel: String?,
    val zoneLevel: Int?,
    val status: WorkoutStatus,
    val steps: List<WorkoutStepView> = emptyList(),
    val actual: ActualWorkoutView? = null,
) {
    val detail: WorkoutLegView
        get() = WorkoutLegView(
            title = title,
            discipline = discipline,
            durationMin = durationMin,
            zoneLabel = zoneLabel,
            zoneLevel = zoneLevel,
            status = status,
            steps = steps,
            actual = actual,
        )
}

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
    /** Distinct disciplines that day, so a cell shows *what kind* of training it holds - a grid of
     * identical dots tells the athlete nothing a blank cell wouldn't. */
    val disciplines: List<Discipline> = emptyList(),
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
        discipline = discipline,
        disciplineLabel = disciplineLabel(discipline),
        durationMin = plannedDurationSec / 60,
        plannedLoad = plannedLoad,
        zoneLabel = zoneLabel,
        zoneLevel = zone?.level,
        status = status,
        steps = steps.sortedBy { it.stepOrder }.map { it.toWorkoutStepView(discipline, profile) },
        actual = activity?.toActualWorkoutView(),
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

private fun GeneratedWorkoutStep.toWorkoutStepView(discipline: Discipline, profile: UserZoneProfile?): WorkoutStepView {
    val resolved = intensityZone?.let { ZoneResolver.resolve(discipline, it, profile) }
    return WorkoutStepView(
        stepType = stepType,
        durationMin = (durationSec ?: 0) / 60,
        zoneLabel = zoneLabelFor(resolved),
        zoneLevel = intensityZone?.level,
        repeatCount = repeatCount,
        cueText = cueText,
    )
}

private fun CompletedActivity.toActualWorkoutView(): ActualWorkoutView = ActualWorkoutView(
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
    ZoneKind.RUN_PACE -> "Zone ${resolved.range.zone.level} (${formatPace(resolved.range.lowerBound)}-${formatPace(resolved.range.upperBound)} /km)"
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
