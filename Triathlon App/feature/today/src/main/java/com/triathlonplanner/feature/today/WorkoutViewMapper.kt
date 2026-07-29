package com.triathlonplanner.feature.today

import com.triathlonplanner.core.designsystem.ActualWorkoutView
import com.triathlonplanner.core.designsystem.WorkoutLegView
import com.triathlonplanner.core.designsystem.WorkoutStepView
import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.GeneratedWorkoutStep
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.data.repository.ResolvedZone
import com.triathlonplanner.data.repository.ZoneKind
import com.triathlonplanner.data.repository.ZoneResolver

fun PlannedWorkoutSnapshot.toTodayView(profile: UserZoneProfile?): TodayWorkoutView {
    val resolved = ZoneResolver.resolve(discipline, zone, profile)
    val zoneLabel = zoneLabelFor(resolved)

    return TodayWorkoutView(
        id = id,
        // Always the plan, never overridden by what actually happened - a substitution is a
        // property of the *actual* activity, and belongs only in "what you actually did", not
        // rewritten into the title of what was planned.
        title = "${workoutType.name.lowercase().replaceFirstChar(Char::uppercase)} ${disciplineLabel(discipline)}",
        discipline = discipline,
        workoutType = workoutType,
        status = status,
        durationMin = plannedDurationSec / 60,
        zoneLabel = zoneLabel,
    )
}

/** Full planned/actual detail for one workout - shared with the Plan tab's day panel via
 * [WorkoutLegView] so a substituted session renders identically wherever it's shown. */
fun PlannedWorkoutSnapshot.toWorkoutLegView(
    profile: UserZoneProfile?,
    steps: List<GeneratedWorkoutStep>,
    activity: CompletedActivity?,
): WorkoutLegView {
    val zoneLabel = zoneLabelFor(ZoneResolver.resolve(discipline, zone, profile))
    return WorkoutLegView(
        title = "${workoutType.name.lowercase().replaceFirstChar(Char::uppercase)} ${disciplineLabel(discipline)}",
        durationMin = plannedDurationSec / 60,
        zoneLabel = zoneLabel,
        status = status,
        steps = steps.sortedBy { it.stepOrder }.map { it.toWorkoutStepView(discipline, profile) },
        actual = activity?.toActualWorkoutView(),
    )
}

private fun GeneratedWorkoutStep.toWorkoutStepView(discipline: Discipline, profile: UserZoneProfile?): WorkoutStepView {
    val resolved = intensityZone?.let { ZoneResolver.resolve(discipline, it, profile) }
    return WorkoutStepView(
        stepType = stepType,
        durationMin = (durationSec ?: 0) / 60,
        zoneLabel = zoneLabelFor(resolved),
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

private fun disciplineLabel(discipline: Discipline): String = when (discipline) {
    Discipline.BRICK_BIKE -> "Bike"
    Discipline.BRICK_RUN -> "Run"
    else -> discipline.name.lowercase().replaceFirstChar(Char::uppercase)
}

private fun zoneLabelFor(resolved: ResolvedZone?): String? = when (resolved?.kind) {
    null -> null
    ZoneKind.PACE -> "Zone ${resolved.range.zone.level} (${formatPace(resolved.range.upperBound)}-${formatPace(resolved.range.lowerBound)} /100m)"
    ZoneKind.POWER -> "Zone ${resolved.range.zone.level} (${resolved.range.lowerBound}-${resolved.range.upperBound} W)"
    ZoneKind.HEART_RATE -> "Zone ${resolved.range.zone.level} (${resolved.range.lowerBound}-${resolved.range.upperBound} bpm)"
}

private fun formatPace(secPer100m: Int): String {
    val minutes = secPer100m / 60
    val seconds = secPer100m % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
