package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.TrainingPhase
import org.junit.Test

class PeriodizationCalculatorTest {

    @Test
    fun `ideal weeks allocation sums correctly and ends in race week`() {
        val result = PeriodizationCalculator.calculate(Distance.SPRINT, totalWeeks = Distance.SPRINT.idealWeeks)

        assertThat(result.weeks).hasSize(Distance.SPRINT.idealWeeks)
        assertThat(result.weeks.last().phase).isEqualTo(TrainingPhase.RACE_WEEK)
        assertThat(result.weeks.map { it.weekIndex }).isEqualTo((1..Distance.SPRINT.idealWeeks).toList())
    }

    @Test
    fun `phases appear in base build peak taper race-week order`() {
        val result = PeriodizationCalculator.calculate(Distance.HALF_IRON, totalWeeks = Distance.HALF_IRON.idealWeeks)

        val phaseSequence = result.weeks.map { it.phase }.distinct()
        assertThat(phaseSequence).isEqualTo(
            listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER, TrainingPhase.RACE_WEEK),
        )
    }

    @Test
    fun `peak and taper are never compressed below their target when runway is short`() {
        val shortRunway = Distance.FULL_IRON.minWeeks - 2 // strictly below minWeeks, to trigger the warning
        val result = PeriodizationCalculator.calculate(Distance.FULL_IRON, totalWeeks = shortRunway)

        val expectedPeakWeeks = maxOf(1, Math.round(shortRunway * Distance.FULL_IRON.peakPercent).toInt())
        assertThat(result.weeks.count { it.phase == TrainingPhase.PEAK }).isEqualTo(expectedPeakWeeks)
        assertThat(result.weeks.count { it.phase == TrainingPhase.TAPER } + 1) // +1 for race week
            .isEqualTo(Distance.FULL_IRON.taperWeeks)
        assertThat(result.warnings).isNotEmpty()
    }

    @Test
    fun `base is compressed to a 1-week floor under extreme time pressure`() {
        // Deliberately far shorter than minWeeks to force maximum compression of Base (and Build).
        val result = PeriodizationCalculator.calculate(Distance.FULL_IRON, totalWeeks = 6)

        assertThat(result.weeks.count { it.phase == TrainingPhase.BASE }).isEqualTo(1)
        assertThat(result.weeks.count { it.phase == TrainingPhase.PEAK }).isAtLeast(1)
        assertThat(result.weeks.count { it.phase == TrainingPhase.TAPER } + 1)
            .isEqualTo(Distance.FULL_IRON.taperWeeks)
        assertThat(result.warnings).isNotEmpty()
    }

    @Test
    fun `recovery weeks are scheduled against phase structure, not a fixed calendar modulus`() {
        val result = PeriodizationCalculator.calculate(Distance.OLYMPIC, totalWeeks = Distance.OLYMPIC.idealWeeks)

        // Cutbacks exist, and they respect the placement rules rather than landing on week % 4.
        // See DeloadSchedulingTest for the rules themselves, exercised across every distance and
        // runway length; this pins the behaviour change for the canonical Olympic plan.
        assertThat(result.weeks.count { it.isRecoveryWeek }).isAtLeast(2)
        result.weeks.forEach { week ->
            if (week.phase == TrainingPhase.TAPER || week.phase == TrainingPhase.RACE_WEEK) {
                assertThat(week.isRecoveryWeek).isFalse()
            }
        }
        // The old fixed rule put a cutback on week 8, which is the first week of Build here.
        val firstBuildWeek = result.weeks.first { it.phase == TrainingPhase.BUILD }
        assertThat(firstBuildWeek.isRecoveryWeek).isFalse()
    }

    @Test
    fun `no warning when runway meets the minimum`() {
        val result = PeriodizationCalculator.calculate(Distance.SPRINT, totalWeeks = Distance.SPRINT.minWeeks)

        assertThat(result.warnings).isEmpty()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `too few weeks throws`() {
        PeriodizationCalculator.calculate(Distance.SPRINT, totalWeeks = 2)
    }
}
