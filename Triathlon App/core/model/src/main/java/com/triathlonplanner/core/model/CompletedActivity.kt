package com.triathlonplanner.core.model

import java.time.Instant

/**
 * A workout as read from Health Connect, after mapping from HC records into our domain shape.
 * [matchedPlannedWorkoutId] and [matchStatus] are populated by SessionMatcher; null/UNMATCHED_EXTRA
 * until matched against that day's plan.
 */
data class CompletedActivity(
    val healthConnectRecordId: String,
    val discipline: Discipline,
    val startTime: Instant,
    val endTime: Instant,
    val distanceM: Double? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val avgPowerW: Int? = null,
    val normalizedPowerW: Int? = null,
    val calculatedLoad: Int,
    val matchedPlannedWorkoutId: Long? = null,
    val matchStatus: MatchStatus = MatchStatus.UNMATCHED_EXTRA,
) {
    val durationSec: Long get() = endTime.epochSecond - startTime.epochSecond
}
