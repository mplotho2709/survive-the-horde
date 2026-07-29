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
}
