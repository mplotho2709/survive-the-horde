package com.triathlonplanner.feature.onboarding

import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.TrainingAvailability

/** Coaching-judgment defaults shown to the user during onboarding - not domain logic, so it lives
 * here rather than in :core:model or :domain:planengine. */
fun recommendedFor(distance: Distance): TrainingAvailability = when (distance) {
    Distance.SPRINT -> TrainingAvailability(weeklyHoursTarget = 5.0, daysPerWeekTarget = 5)
    Distance.OLYMPIC -> TrainingAvailability(weeklyHoursTarget = 7.0, daysPerWeekTarget = 5)
    Distance.HALF_IRON -> TrainingAvailability(weeklyHoursTarget = 9.0, daysPerWeekTarget = 6)
    Distance.FULL_IRON -> TrainingAvailability(weeklyHoursTarget = 12.0, daysPerWeekTarget = 6)
}
