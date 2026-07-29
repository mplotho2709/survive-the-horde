package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.TrainingPhase
import org.junit.Test

/** Placement rules for dynamically scheduled cutback weeks - see PeriodizationCalculator docs. */
class DeloadSchedulingTest {

    private fun allPlans() = Distance.entries.flatMap { distance ->
        (distance.minWeeks..distance.idealWeeks + 4).map { weeks ->
            Triple(distance, weeks, PeriodizationCalculator.calculate(distance, weeks).weeks)
        }
    }

    @Test
    fun `a deload never lands on the first week of a phase`() {
        allPlans().forEach { (distance, totalWeeks, weeks) ->
            weeks.forEachIndexed { index, week ->
                if (!week.isRecoveryWeek) return@forEachIndexed
                val isFirstOfPhase = index == 0 || weeks[index - 1].phase != week.phase
                assertThat(isFirstOfPhase).isFalse()
            }
        }
    }

    @Test
    fun `the final peak week is never a deload`() {
        allPlans().forEach { (_, _, weeks) ->
            val lastPeak = weeks.lastOrNull { it.phase == TrainingPhase.PEAK }
            if (lastPeak != null) {
                assertThat(lastPeak.isRecoveryWeek).isFalse()
            }
        }
    }

    @Test
    fun `taper and race week are never marked as deloads - they are already reductions`() {
        allPlans().forEach { (_, _, weeks) ->
            weeks.filter { it.phase == TrainingPhase.TAPER || it.phase == TrainingPhase.RACE_WEEK }
                .forEach { assertThat(it.isRecoveryWeek).isFalse() }
        }
    }

    @Test
    fun `loading blocks never run longer than the recovery interval without a cutback`() {
        allPlans().forEach { (distance, totalWeeks, weeks) ->
            val loading = weeks.filter {
                it.phase == TrainingPhase.BASE || it.phase == TrainingPhase.BUILD || it.phase == TrainingPhase.PEAK
            }
            var streak = 0
            var longestStreak = 0
            loading.forEach { week ->
                if (week.isRecoveryWeek) streak = 0 else streak++
                longestStreak = maxOf(longestStreak, streak)
            }
            // A blocked slot defers the deload by a week, so allow a small amount of slack over
            // the nominal interval - but never an unbounded ramp.
            assertThat(longestStreak).isAtMost(PeriodizationCalculator.RECOVERY_INTERVAL_WEEKS + 3)
        }
    }

    @Test
    fun `a long base block still receives cutback weeks`() {
        val weeks = PeriodizationCalculator.calculate(Distance.FULL_IRON, 28).weeks

        assertThat(weeks.count { it.isRecoveryWeek }).isAtLeast(3)
    }
}
