package com.triathlonplanner.feature.today

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.data.repository.ZoneResolver

fun PlannedWorkoutSnapshot.toTodayView(profile: UserZoneProfile?): TodayWorkoutView {
    val range = ZoneResolver.resolve(discipline, zone, profile)
    val zoneLabel = when {
        range == null -> null
        discipline == Discipline.SWIM -> "Zone ${range.zone.level} (${formatPace(range.upperBound)}-${formatPace(range.lowerBound)} /100m)"
        discipline == Discipline.BIKE || discipline == Discipline.BRICK_BIKE -> "Zone ${range.zone.level} (${range.lowerBound}-${range.upperBound} W)"
        else -> "Zone ${range.zone.level} (${range.lowerBound}-${range.upperBound} bpm)"
    }

    return TodayWorkoutView(
        id = id,
        title = "${workoutType.name.lowercase().replaceFirstChar(Char::uppercase)} ${discipline.name.lowercase().replaceFirstChar(Char::uppercase)}",
        discipline = discipline,
        workoutType = workoutType,
        status = status,
        durationMin = plannedDurationSec / 60,
        zoneLabel = zoneLabel,
    )
}

private fun formatPace(secPer100m: Int): String {
    val minutes = secPer100m / 60
    val seconds = secPer100m % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
