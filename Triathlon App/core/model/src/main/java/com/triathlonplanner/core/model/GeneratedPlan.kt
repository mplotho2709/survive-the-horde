package com.triathlonplanner.core.model

import java.time.LocalDate

/** Output of PlanGenerator. [warnings] surfaces things like "runway is shorter than ideal". */
data class GeneratedPlan(
    val raceGoal: RaceGoal,
    val startDate: LocalDate,
    val totalWeeks: Int,
    val weeks: List<GeneratedWeek>,
    val warnings: List<String>,
)

data class GeneratedWeek(
    val weekIndex: Int,
    val startDate: LocalDate,
    val phase: TrainingPhase,
    val isRecoveryWeek: Boolean,
    val plannedWeeklyLoad: Int,
    val workouts: List<GeneratedWorkout>,
)

data class GeneratedWorkout(
    val date: LocalDate,
    val discipline: Discipline,
    val workoutType: WorkoutType,
    val title: String,
    val plannedDurationSec: Int,
    val plannedDistanceM: Int? = null,
    val plannedLoad: Int,
    val zone: IntensityZone? = null,
    val brickGroupId: String? = null,
    val sortOrderInDay: Int = 0,
    val steps: List<GeneratedWorkoutStep> = emptyList(),
)

data class GeneratedWorkoutStep(
    val stepOrder: Int,
    val stepType: WorkoutStepType,
    val durationSec: Int? = null,
    val distanceM: Int? = null,
    val intensityZone: IntensityZone? = null,
    val repeatCount: Int? = null,
    val cueText: String? = null,
)
