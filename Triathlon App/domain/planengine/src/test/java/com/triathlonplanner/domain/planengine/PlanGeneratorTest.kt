package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.RaceGoal
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.UserZoneProfile
import org.junit.Test
import java.time.LocalDate

class PlanGeneratorTest {

    private val profile = UserZoneProfile(maxHr = 185, restingHr = 55, ftpWatts = 220)

    @Test
    fun `generates a plan spanning from start to race week`() {
        val startDate = LocalDate.of(2026, 1, 5) // a Monday
        val raceDate = startDate.plusWeeks(15) // lands in Olympic's ideal-ish range
        val plan = PlanGenerator.generate(RaceGoal(Distance.OLYMPIC, raceDate), profile, startDate)

        assertThat(plan.weeks.first().startDate).isEqualTo(startDate)
        assertThat(plan.weeks.last().phase).isEqualTo(TrainingPhase.RACE_WEEK)
        assertThat(plan.weeks.last().workouts.all { it.date <= raceDate }).isTrue()
    }

    @Test
    fun `every workout falls within its own week's 7-day window`() {
        val startDate = LocalDate.of(2026, 1, 5)
        val plan = PlanGenerator.generate(
            RaceGoal(Distance.SPRINT, startDate.plusWeeks(12)),
            profile,
            startDate,
        )

        plan.weeks.forEach { week ->
            week.workouts.forEach { workout ->
                assertThat(workout.date).isAtLeast(week.startDate)
                assertThat(workout.date).isAtMost(week.startDate.plusDays(6))
            }
        }
    }

    @Test
    fun `build and peak weeks include a same-day brick pairing`() {
        val startDate = LocalDate.of(2026, 1, 5)
        val plan = PlanGenerator.generate(
            RaceGoal(Distance.HALF_IRON, startDate.plusWeeks(20)),
            profile,
            startDate,
        )

        val buildWeek = plan.weeks.first { it.phase == TrainingPhase.BUILD && !it.isRecoveryWeek }
        val brickWorkouts = buildWeek.workouts.filter { it.discipline == Discipline.BRICK_BIKE || it.discipline == Discipline.BRICK_RUN }

        assertThat(brickWorkouts).hasSize(2)
        assertThat(brickWorkouts.map { it.date }.distinct()).hasSize(1) // same day
        assertThat(brickWorkouts.map { it.brickGroupId }.distinct()).hasSize(1) // same group id
        assertThat(brickWorkouts.first().brickGroupId).isNotNull()
    }

    @Test
    fun `short runway produces a warning`() {
        val startDate = LocalDate.of(2026, 1, 5)
        val plan = PlanGenerator.generate(
            RaceGoal(Distance.FULL_IRON, startDate.plusWeeks(10)),
            profile,
            startDate,
        )

        assertThat(plan.warnings).isNotEmpty()
    }

    @Test
    fun `aggressive target time relative to current fitness produces a realism warning`() {
        val startDate = LocalDate.of(2026, 1, 5)
        val raceGoal = RaceGoal(
            distance = Distance.OLYMPIC,
            raceDate = startDate.plusWeeks(16),
            currentFitnessEstimateSec = 10_000,
            targetFinishTimeSec = 8_000, // 20% faster - well past the always-warn threshold
        )
        val plan = PlanGenerator.generate(raceGoal, profile, startDate)

        assertThat(plan.warnings.any { it.contains("aggressive") }).isTrue()
    }

    @Test
    fun `very close race date still produces a valid minimal plan`() {
        val startDate = LocalDate.of(2026, 1, 5)
        val plan = PlanGenerator.generate(
            RaceGoal(Distance.SPRINT, startDate.plusWeeks(1)),
            profile,
            startDate,
        )

        assertThat(plan.totalWeeks).isAtLeast(4)
        assertThat(plan.warnings).isNotEmpty()
    }

    @Test
    fun `each week's workout loads sum close to the week's planned load`() {
        val startDate = LocalDate.of(2026, 1, 5)
        val plan = PlanGenerator.generate(
            RaceGoal(Distance.OLYMPIC, startDate.plusWeeks(16)),
            profile,
            startDate,
        )

        // RACE_WEEK is excluded: sessions scheduled after the actual race day are filtered out,
        // so its load sum is legitimately below the pre-filter weekly target - see PlanGenerator.
        plan.weeks.filter { it.phase != TrainingPhase.RACE_WEEK }.forEach { week ->
            val sumOfWorkoutLoads = week.workouts.sumOf { it.plannedLoad }
            // Rounding per-workout can drift the sum by a few units; allow a small tolerance.
            assertThat(sumOfWorkoutLoads.toDouble()).isWithin(week.workouts.size.toDouble()).of(week.plannedWeeklyLoad.toDouble())
        }
    }
}
