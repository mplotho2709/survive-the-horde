package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.TrainingPhase
import org.junit.Test

class WeeklyLoadCurveTest {

    @Test
    fun `non-recovery weeks never grow more than 10 percent`() {
        val weeks = (1..12).map { WeekPlan(it, TrainingPhase.BUILD, isRecoveryWeek = it % 4 == 0) }
        val loads = WeeklyLoadCurve.calculate(weeks)

        for (i in 1 until loads.size) {
            if (!weeks[i].isRecoveryWeek && weeks[i - 1].isRecoveryWeek.not()) {
                val growth = loads[i].toDouble() / loads[i - 1]
                assertThat(growth).isAtMost(1.10)
            }
        }
    }

    @Test
    fun `recovery week cuts load below the preceding peak`() {
        val weeks = listOf(
            WeekPlan(1, TrainingPhase.BASE, isRecoveryWeek = false),
            WeekPlan(2, TrainingPhase.BASE, isRecoveryWeek = false),
            WeekPlan(3, TrainingPhase.BASE, isRecoveryWeek = false),
            WeekPlan(4, TrainingPhase.BASE, isRecoveryWeek = true),
        )
        val loads = WeeklyLoadCurve.calculate(weeks)

        assertThat(loads[3]).isLessThan(loads[2])
    }

    @Test
    fun `week after a recovery week rebounds above the pre-cutback peak`() {
        val weeks = listOf(
            WeekPlan(1, TrainingPhase.BASE, isRecoveryWeek = false),
            WeekPlan(2, TrainingPhase.BASE, isRecoveryWeek = false),
            WeekPlan(3, TrainingPhase.BASE, isRecoveryWeek = false),
            WeekPlan(4, TrainingPhase.BASE, isRecoveryWeek = true),
            WeekPlan(5, TrainingPhase.BASE, isRecoveryWeek = false),
        )
        val loads = WeeklyLoadCurve.calculate(weeks)

        assertThat(loads[4]).isGreaterThan(loads[2]) // rebounds above the pre-cutback (week 3) peak
    }

    @Test
    fun `taper and race week load are sharply reduced`() {
        val weeks = listOf(
            WeekPlan(1, TrainingPhase.PEAK, isRecoveryWeek = false),
            WeekPlan(2, TrainingPhase.TAPER, isRecoveryWeek = false),
            WeekPlan(3, TrainingPhase.RACE_WEEK, isRecoveryWeek = false),
        )
        val loads = WeeklyLoadCurve.calculate(weeks)

        assertThat(loads[1]).isLessThan(loads[0])
        assertThat(loads[2]).isLessThan(loads[1])
    }
}
