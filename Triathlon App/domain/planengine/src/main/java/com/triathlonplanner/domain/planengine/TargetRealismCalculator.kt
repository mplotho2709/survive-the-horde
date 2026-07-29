package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.domain.zones.RaceTimePredictor

/**
 * Surfaces an honest warning when a target finish time implies a bigger improvement than a
 * single training block can realistically deliver. The two thresholds below are a
 * coaching-judgment heuristic *informed by* (not a directly-cited number from) the training-volume
 * research already applied in [com.triathlonplanner.feature.onboarding] - there's no clean
 * universally-cited "% improvement per training block" figure, so this is deliberately
 * conservative rather than precise.
 */
object TargetRealismCalculator {

    private const val ALWAYS_WARN_THRESHOLD_PERCENT = 15.0
    private const val TIMELINE_SENSITIVE_THRESHOLD_PERCENT = 8.0

    fun checkRealism(distance: Distance, totalWeeks: Int, currentEstimateSec: Int?, targetFinishTimeSec: Int?): String? {
        if (currentEstimateSec == null || targetFinishTimeSec == null) return null
        val requiredImprovement = RaceTimePredictor.requiredImprovementPercent(currentEstimateSec, targetFinishTimeSec)
        if (requiredImprovement <= 0.0) return null

        val isAggressive = requiredImprovement > ALWAYS_WARN_THRESHOLD_PERCENT ||
            (requiredImprovement > TIMELINE_SENSITIVE_THRESHOLD_PERCENT && totalWeeks < distance.idealWeeks)
        if (!isAggressive) return null

        val percentLabel = "%.0f".format(requiredImprovement)
        return "Your target is about $percentLabel% faster than your current estimated fitness - " +
            "that's an aggressive improvement for a single training block. Treat this as a stretch " +
            "goal, and consider a longer runway or a more moderate target if you want a safer plan."
    }

    /**
     * Catches the case the gap analysis alone misses: the athlete was *shown* a higher recommended
     * weekly volume because of their target, then committed to fewer hours than that anyway.
     *
     * This is a distinct failure from an aggressive target. There the goal is merely ambitious;
     * here the plan the athlete actually agreed to cannot, by its own volume, deliver the goal
     * they set. Silently generating that plan would mean showing them a schedule that is
     * arithmetically incapable of reaching their stated time.
     *
     * Returns null when there's no target, no fitness estimate, no committed hours, or when the
     * committed volume is within [COMMITMENT_TOLERANCE] of what the gap implies.
     */
    fun checkVolumeCommitment(
        recommendedWeeklyHours: Double,
        committedWeeklyHours: Double?,
    ): String? {
        val committed = committedWeeklyHours ?: return null
        if (committed <= 0.0 || recommendedWeeklyHours <= 0.0) return null
        if (committed >= recommendedWeeklyHours * (1.0 - COMMITMENT_TOLERANCE)) return null

        val shortfallPercent = ((recommendedWeeklyHours - committed) / recommendedWeeklyHours) * 100.0
        return "Your target time suggests about ${"%.1f".format(recommendedWeeklyHours)}h/week, but you've " +
            "committed ${"%.1f".format(committed)}h - roughly ${"%.0f".format(shortfallPercent)}% less. " +
            "The plan will fit the hours you have, so treat the target as a stretch: either find more " +
            "training time, or expect a finish closer to your current fitness."
    }

    /** Committing slightly under the recommendation is normal and not worth warning about. */
    private const val COMMITMENT_TOLERANCE = 0.10
}
