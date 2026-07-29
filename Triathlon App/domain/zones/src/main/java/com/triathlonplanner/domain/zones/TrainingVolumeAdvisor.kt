package com.triathlonplanner.domain.zones

import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.TrainingAvailability

/**
 * Recommended weekly training volume, and how it should shift when an athlete's goal demands more
 * fitness than they currently have.
 *
 * Lives in the domain layer rather than in the onboarding UI because two callers need the same
 * answer and must not disagree: the onboarding screen (to pre-fill a recommendation) and the plan
 * engine (to judge whether the hours the athlete actually committed can support their goal).
 *
 * Research grounding: 6-10 h/week is sufficient for most age-group triathletes to improve
 * consistently, and beyond roughly 14 h/week additional volume shows no meaningful further
 * race-performance benefit for age-groupers while injury and life-stress costs keep rising. That
 * ceiling is enforced by [ABSOLUTE_MAX_HOURS]. The per-distance baselines and the size of the
 * fitness-gap bump are coaching judgment calibrated against those bounds, not directly cited
 * figures - see [RaceTimePredictor.recommendedHoursBumpFraction].
 */
object TrainingVolumeAdvisor {

    const val ABSOLUTE_MAX_HOURS = 14.0

    fun baselineFor(distance: Distance): TrainingAvailability = when (distance) {
        Distance.SPRINT -> TrainingAvailability(weeklyHoursTarget = 5.0, daysPerWeekTarget = 5)
        Distance.OLYMPIC -> TrainingAvailability(weeklyHoursTarget = 7.0, daysPerWeekTarget = 5)
        Distance.HALF_IRON -> TrainingAvailability(weeklyHoursTarget = 9.0, daysPerWeekTarget = 6)
        Distance.FULL_IRON -> TrainingAvailability(weeklyHoursTarget = 12.0, daysPerWeekTarget = 6)
    }

    /**
     * Baseline volume raised in proportion to the improvement the athlete's target requires, then
     * clamped to [ABSOLUTE_MAX_HOURS]. `daysPerWeekTarget` is deliberately untouched: that field
     * drives how many distinct days the template uses, not how much training happens - volume is
     * the lever here, and adding days without adding hours just fragments the same work.
     */
    fun adjustForFitnessGap(distance: Distance, requiredImprovementPercent: Double): TrainingAvailability {
        val base = baselineFor(distance)
        val bump = RaceTimePredictor.recommendedHoursBumpFraction(requiredImprovementPercent)
        return base.copy(
            weeklyHoursTarget = (base.weeklyHoursTarget * (1.0 + bump)).coerceAtMost(ABSOLUTE_MAX_HOURS),
        )
    }

    /**
     * The weekly volume a goal implies, or the plain baseline when there's no target time or no
     * current-fitness estimate to measure a gap against.
     */
    fun recommendedFor(distance: Distance, currentEstimateSec: Int?, targetFinishTimeSec: Int?): TrainingAvailability {
        if (currentEstimateSec == null || targetFinishTimeSec == null) return baselineFor(distance)
        val improvement = RaceTimePredictor.requiredImprovementPercent(currentEstimateSec, targetFinishTimeSec)
        return adjustForFitnessGap(distance, improvement)
    }
}
