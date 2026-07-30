package com.triathlonplanner.core.model

import java.time.Instant

/** User-facing audit trail entry - shown as "Why did my plan change?" */
data class AdaptationEvent(
    val timestamp: Instant,
    val triggerType: AdaptationTriggerType,
    val actionTaken: AdaptationAction,
    val description: String,
    val relatedPlannedWorkoutId: Long? = null,
)

/** A single, applyable change to the persisted plan, produced by AdaptationEngine. */
sealed class PlanMutation {
    data class MarkMissed(val workoutId: Long) : PlanMutation()
    data class DropWorkout(val workoutId: Long) : PlanMutation()
    data class DowngradeToRecovery(val workoutId: Long) : PlanMutation()
    data class DowngradeToRest(val workoutId: Long) : PlanMutation()
    data class RescaleWeekLoad(val weekIndex: Int, val newPlannedLoad: Int) : PlanMutation()
    data class RequestReEntryBlock(val fromDate: java.time.LocalDate) : PlanMutation()
    data class SoftenCutback(val weekIndex: Int, val newPlannedLoad: Int) : PlanMutation()
    data class ExtendBuildBlock(val afterWeekIndex: Int) : PlanMutation()

    /**
     * Retunes one upcoming session's volume so the block still lands near its intended stress after
     * a missed, easy, hard or swapped workout. Duration and load move together; the zone is
     * deliberately absent, because this must never change how *hard* a session is.
     */
    data class AdjustSessionLoad(
        val workoutId: Long,
        val newDurationSec: Int,
        val newPlannedLoad: Int,
    ) : PlanMutation()
}

data class AdaptationResult(
    val mutations: List<PlanMutation>,
    val events: List<AdaptationEvent>,
    val updatedRollingLoadState: RollingLoadState,
)
