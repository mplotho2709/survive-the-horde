package com.triathlonplanner.feature.onboarding

import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.TrainingAvailability
import com.triathlonplanner.domain.zones.RaceTimePredictor

/** Coaching-judgment defaults shown to the user during onboarding - not domain logic, so it lives
 * here rather than in :core:model or :domain:planengine. */
fun recommendedFor(distance: Distance): TrainingAvailability = when (distance) {
    Distance.SPRINT -> TrainingAvailability(weeklyHoursTarget = 5.0, daysPerWeekTarget = 5)
    Distance.OLYMPIC -> TrainingAvailability(weeklyHoursTarget = 7.0, daysPerWeekTarget = 5)
    Distance.HALF_IRON -> TrainingAvailability(weeklyHoursTarget = 9.0, daysPerWeekTarget = 6)
    Distance.FULL_IRON -> TrainingAvailability(weeklyHoursTarget = 12.0, daysPerWeekTarget = 6)
}

// Beyond ~14h/week, research shows no meaningful additional race-performance benefit for
// age-group triathletes - a hard ceiling regardless of how large a fitness gap implies.
private const val ABSOLUTE_MAX_HOURS = 14.0

/** Bumps the plain per-distance recommendation up when a target time requires real improvement
 * over the user's estimated current fitness - see [RaceTimePredictor.recommendedHoursBumpFraction]
 * for the bounded-growth reasoning. [daysPerWeekTarget] is left untouched: that field only drives
 * how many training days the template uses, not volume - the volume lever is [weeklyHoursTarget]. */
fun adjustForFitnessGap(distance: Distance, requiredImprovementPercent: Double): TrainingAvailability {
    val base = recommendedFor(distance)
    val bump = RaceTimePredictor.recommendedHoursBumpFraction(requiredImprovementPercent)
    val adjustedHours = (base.weeklyHoursTarget * (1.0 + bump)).coerceAtMost(ABSOLUTE_MAX_HOURS)
    return base.copy(weeklyHoursTarget = adjustedHours)
}
