package com.triathlonplanner.feature.onboarding

import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.TrainingAvailability
import com.triathlonplanner.domain.zones.TrainingVolumeAdvisor

/**
 * Thin UI-facing delegates over [TrainingVolumeAdvisor]. The actual volume logic lives in the
 * domain layer so the plan engine and this screen cannot drift apart: the engine judges the
 * athlete's committed hours against the same recommendation the screen showed them.
 */
fun recommendedFor(distance: Distance): TrainingAvailability = TrainingVolumeAdvisor.baselineFor(distance)

fun adjustForFitnessGap(distance: Distance, requiredImprovementPercent: Double): TrainingAvailability =
    TrainingVolumeAdvisor.adjustForFitnessGap(distance, requiredImprovementPercent)
