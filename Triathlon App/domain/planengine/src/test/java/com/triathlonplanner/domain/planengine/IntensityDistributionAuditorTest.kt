package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.RaceGoal
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.UserZoneProfile
import org.junit.Test
import java.time.LocalDate

class IntensityDistributionAuditorTest {

    private val profile = UserZoneProfile(maxHr = 185, restingHr = 55, ftpWatts = 220, cssPaceSecPer100m = 95)
    private val startDate = LocalDate.of(2026, 1, 5) // a Monday

    private fun planFor(distance: Distance, weeks: Long) =
        PlanGenerator.generate(RaceGoal(distance, startDate.plusWeeks(weeks)), profile, startDate)

    @Test
    fun `distribution is measured from time in zone, not from session labels`() {
        // A 35-minute "threshold" session is mostly warmup, recoveries and cooldown; counting the
        // whole session as hard would roughly triple the apparent intensity.
        val steps = WorkoutStepBuilder.build(
            durationSec = 35 * 60,
            zone = com.triathlonplanner.core.model.IntensityZone(4),
            workoutType = com.triathlonplanner.core.model.WorkoutType.THRESHOLD,
            discipline = com.triathlonplanner.core.model.Discipline.RUN,
        )
        val week = com.triathlonplanner.core.model.GeneratedWeek(
            weekIndex = 1,
            startDate = startDate,
            phase = TrainingPhase.BUILD,
            isRecoveryWeek = false,
            plannedWeeklyLoad = 100,
            workouts = listOf(
                com.triathlonplanner.core.model.GeneratedWorkout(
                    date = startDate,
                    discipline = com.triathlonplanner.core.model.Discipline.RUN,
                    workoutType = com.triathlonplanner.core.model.WorkoutType.THRESHOLD,
                    title = "Threshold Run",
                    plannedDurationSec = 35 * 60,
                    plannedLoad = 100,
                    steps = steps,
                ),
            ),
        )

        val distribution = IntensityDistributionAuditor.distributionFor(week)

        assertThat(distribution.totalSec).isEqualTo(35 * 60)
        // Hard time is the interval work only - well under half the session.
        assertThat(distribution.hardFraction).isLessThan(0.50)
        assertThat(distribution.easySec).isGreaterThan(distribution.hardSec)
    }

    @Test
    fun `unzoned strength work is excluded rather than inflating the easy fraction`() {
        val week = com.triathlonplanner.core.model.GeneratedWeek(
            weekIndex = 1,
            startDate = startDate,
            phase = TrainingPhase.BASE,
            isRecoveryWeek = false,
            plannedWeeklyLoad = 100,
            workouts = listOf(
                com.triathlonplanner.core.model.GeneratedWorkout(
                    date = startDate,
                    discipline = com.triathlonplanner.core.model.Discipline.STRENGTH,
                    workoutType = com.triathlonplanner.core.model.WorkoutType.STRENGTH_SESSION,
                    title = "Session Strength",
                    plannedDurationSec = 40 * 60,
                    plannedLoad = 100,
                    steps = WorkoutStepBuilder.build(
                        40 * 60, null,
                        com.triathlonplanner.core.model.WorkoutType.STRENGTH_SESSION,
                        com.triathlonplanner.core.model.Discipline.STRENGTH,
                    ),
                ),
            ),
        )

        assertThat(IntensityDistributionAuditor.distributionFor(week).totalSec).isEqualTo(0)
    }

    @Test
    fun `every policed week meets its phase's easy-training floor across every distance`() {
        Distance.entries.forEach { distance ->
            val plan = planFor(distance, distance.idealWeeks.toLong())
            plan.weeks.forEach { week ->
                val floor = IntensityDistributionAuditor.minEasyFractionFor(week.phase) ?: return@forEach
                val distribution = IntensityDistributionAuditor.distributionFor(week)
                if (distribution.totalSec == 0) return@forEach

                assertThat(distribution.easyFraction).isAtLeast(floor)
            }
        }
    }

    @Test
    fun `base blocks are essentially all aerobic`() {
        // Base exists to build aerobic capacity; intensity here buys fatigue that displaces the
        // volume the phase is for.
        Distance.entries.forEach { distance ->
            val plan = planFor(distance, distance.idealWeeks.toLong())
            plan.weeks.filter { it.phase == TrainingPhase.BASE }.forEach { week ->
                val distribution = IntensityDistributionAuditor.distributionFor(week)
                if (distribution.totalSec > 0) {
                    assertThat(distribution.hardFraction).isAtMost(0.05)
                }
            }
        }
    }

    @Test
    fun `grey-zone time stays marginal in every policed week`() {
        Distance.entries.forEach { distance ->
            val plan = planFor(distance, distance.idealWeeks.toLong())
            plan.weeks
                .filter { IntensityDistributionAuditor.minEasyFractionFor(it.phase) != null }
                .forEach { week ->
                    val distribution = IntensityDistributionAuditor.distributionFor(week)
                    if (distribution.totalSec > 0) {
                        assertThat(distribution.moderateFraction)
                            .isAtMost(IntensityDistributionAuditor.MAX_MODERATE_FRACTION)
                    }
                }
        }
    }

    @Test
    fun `whole-plan distribution is polarized for every distance`() {
        Distance.entries.forEach { distance ->
            val plan = planFor(distance, distance.idealWeeks.toLong())
            val overall = IntensityDistributionAuditor.distributionFor(plan.weeks)

            assertThat(overall.easyFraction).isAtLeast(IntensityDistributionAuditor.MIN_EASY_FRACTION)
        }
    }

    @Test
    fun `a generated plan reports no intensity-distribution warnings`() {
        Distance.entries.forEach { distance ->
            val plan = planFor(distance, distance.idealWeeks.toLong())
            val intensityWarnings = plan.warnings.filter { it.contains("easy training") || it.contains("Zone 3") }

            assertThat(intensityWarnings).isEmpty()
        }
    }
}
