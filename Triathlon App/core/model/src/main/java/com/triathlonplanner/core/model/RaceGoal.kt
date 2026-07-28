package com.triathlonplanner.core.model

import java.time.LocalDate

data class RaceGoal(
    val distance: Distance,
    val raceDate: LocalDate,
    val raceName: String? = null,
    val targetFinishTimeSec: Int? = null,
    val trainingAvailability: TrainingAvailability? = null,
)
