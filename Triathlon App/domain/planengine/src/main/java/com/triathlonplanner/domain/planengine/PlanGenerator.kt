package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.GeneratedPlan
import com.triathlonplanner.core.model.GeneratedWeek
import com.triathlonplanner.core.model.GeneratedWorkout
import com.triathlonplanner.core.model.RaceGoal
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Orchestrates PeriodizationCalculator + WeeklyLoadCurve + WeeklyTemplate into a full
 * [GeneratedPlan]. [profile] is accepted for API symmetry with a future version that varies
 * session structure by fitness level; today it doesn't change the generated structure (zones are
 * resolved from it later, at read-time, by the zone calculators - see the plan's "store
 * prescriptions as relative zones" design decision).
 */
object PlanGenerator {

    private const val MIN_PLAN_WEEKS = 4

    fun generate(raceGoal: RaceGoal, profile: UserZoneProfile, startDate: LocalDate): GeneratedPlan {
        val firstMonday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val raceWeekMonday = raceGoal.raceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val rawTotalWeeks = ChronoUnit.WEEKS.between(firstMonday, raceWeekMonday).toInt() + 1

        val warnings = mutableListOf<String>()
        val totalWeeks = if (rawTotalWeeks < MIN_PLAN_WEEKS) {
            warnings += "Your race is very soon - only $rawTotalWeeks week(s) away. " +
                "Using a minimal $MIN_PLAN_WEEKS-week plan; expect an aggressive ramp."
            MIN_PLAN_WEEKS
        } else {
            rawTotalWeeks
        }

        val periodization = PeriodizationCalculator.calculate(raceGoal.distance, totalWeeks)
        warnings += periodization.warnings
        TargetRealismCalculator.checkRealism(
            raceGoal.distance,
            totalWeeks,
            raceGoal.currentFitnessEstimateSec,
            raceGoal.targetFinishTimeSec,
        )?.let { warnings += it }
        val weeklyLoads = WeeklyLoadCurve.calculate(periodization.weeks)

        val generatedWeeks = periodization.weeks.mapIndexed { idx, weekPlan ->
            buildWeek(raceGoal, weekPlan, weeklyLoads[idx], firstMonday)
        }

        return GeneratedPlan(
            raceGoal = raceGoal,
            startDate = firstMonday,
            totalWeeks = totalWeeks,
            weeks = generatedWeeks,
            warnings = warnings,
        )
    }

    private fun buildWeek(
        raceGoal: RaceGoal,
        weekPlan: WeekPlan,
        weekTotalLoad: Int,
        firstMonday: LocalDate,
    ): GeneratedWeek {
        val weekMonday = firstMonday.plusWeeks((weekPlan.weekIndex - 1).toLong())
        val specs = WeeklyTemplate.sessionsFor(raceGoal.distance, weekPlan.phase, raceGoal.trainingAvailability)

        val rawLoads = specs.map { it.durationSec * (it.zone?.level ?: 1).toDouble() }
        val totalRawLoad = rawLoads.sum().takeIf { it > 0.0 } ?: 1.0

        val workouts = specs.mapIndexed { index, spec ->
            val date = weekMonday.plusDays((spec.dayOfWeek - 1).toLong())
            val load = (rawLoads[index] / totalRawLoad * weekTotalLoad).roundToInt()
            val brickGroupId = if (spec.isBrickLeg) "w${weekPlan.weekIndex}-d${spec.dayOfWeek}-brick" else null

            GeneratedWorkout(
                date = date,
                discipline = spec.discipline,
                workoutType = spec.workoutType,
                title = buildTitle(spec.discipline, spec.workoutType),
                plannedDurationSec = spec.durationSec,
                plannedLoad = load,
                zone = spec.zone,
                brickGroupId = brickGroupId,
                sortOrderInDay = spec.sortOrderInDay,
                steps = WorkoutStepBuilder.build(spec.durationSec, spec.zone, spec.workoutType, spec.discipline),
            )
            // Race day can fall on any weekday within its calendar week - never schedule a
            // "race week" shakeout session after the race has actually happened.
        }.filter { weekPlan.phase != TrainingPhase.RACE_WEEK || it.date <= raceGoal.raceDate }

        return GeneratedWeek(
            weekIndex = weekPlan.weekIndex,
            startDate = weekMonday,
            phase = weekPlan.phase,
            isRecoveryWeek = weekPlan.isRecoveryWeek,
            plannedWeeklyLoad = weekTotalLoad,
            workouts = workouts,
        )
    }

    private fun buildTitle(discipline: Discipline, workoutType: WorkoutType): String {
        val disciplineLabel = when (discipline) {
            Discipline.SWIM -> "Swim"
            Discipline.BIKE -> "Bike"
            Discipline.RUN -> "Run"
            Discipline.STRENGTH -> "Strength"
            Discipline.BRICK_BIKE -> "Brick - Bike Leg"
            Discipline.BRICK_RUN -> "Brick - Run Leg"
            Discipline.REST -> "Rest"
        }
        val typeLabel = when (workoutType) {
            WorkoutType.EASY -> "Easy"
            WorkoutType.TEMPO -> "Tempo"
            WorkoutType.THRESHOLD -> "Threshold"
            WorkoutType.VO2MAX -> "VO2max"
            WorkoutType.LONG -> "Long"
            WorkoutType.RACE_PACE -> "Race Pace"
            WorkoutType.FTP_TEST -> "FTP Test"
            WorkoutType.CSS_TEST -> "CSS Test"
            WorkoutType.RECOVERY -> "Recovery"
            WorkoutType.STRENGTH_SESSION -> "Session"
            WorkoutType.REST -> "Rest Day"
        }
        return "$typeLabel $disciplineLabel"
    }
}
