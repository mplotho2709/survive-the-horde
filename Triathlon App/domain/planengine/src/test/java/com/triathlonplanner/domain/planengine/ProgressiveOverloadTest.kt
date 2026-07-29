package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.TrainingPhase
import org.junit.Test

/** The <=10% weekly progression boundary, and its one documented exception. */
class ProgressiveOverloadTest {

    @Test
    fun `load never rises more than 10 percent week over week outside a post-cutback rebound`() {
        Distance.entries.forEach { distance ->
            (distance.minWeeks..distance.idealWeeks + 4).forEach { totalWeeks ->
                val weeks = PeriodizationCalculator.calculate(distance, totalWeeks).weeks
                val loads = WeeklyLoadCurve.calculate(weeks)

                loads.forEachIndexed { index, load ->
                    if (index == 0) return@forEachIndexed
                    val previousWasCutback = weeks[index - 1].isRecoveryWeek
                    if (previousWasCutback) return@forEachIndexed // documented exception

                    val previousLoad = loads[index - 1]
                    if (load <= previousLoad) return@forEachIndexed

                    val growth = load.toDouble() / previousLoad
                    assertThat(growth).isAtMost(AdaptationEngine.MAX_WEEK_OVER_WEEK_GROWTH + 0.001)
                }
            }
        }
    }

    @Test
    fun `the post-cutback rebound never exceeds the pre-cutback peak by more than the rebound factor`() {
        val weeks = PeriodizationCalculator.calculate(Distance.OLYMPIC, 16).weeks
        val loads = WeeklyLoadCurve.calculate(weeks)

        loads.forEachIndexed { index, load ->
            if (index == 0 || !weeks[index - 1].isRecoveryWeek) return@forEachIndexed
            // Rebounds return toward a load the athlete already tolerated, not beyond it.
            val priorPeak = loads.take(index - 1).maxOrNull() ?: return@forEachIndexed
            assertThat(load.toDouble()).isAtMost(priorPeak * 1.10)
        }
    }

    @Test
    fun `taper and race week strictly reduce load`() {
        Distance.entries.forEach { distance ->
            val weeks = PeriodizationCalculator.calculate(distance, distance.idealWeeks).weeks
            val loads = WeeklyLoadCurve.calculate(weeks)
            val peakLoad = weeks.indices
                .filter { weeks[it].phase == TrainingPhase.PEAK }
                .maxOfOrNull { loads[it] } ?: return@forEach

            weeks.indices.filter { weeks[it].phase == TrainingPhase.RACE_WEEK }.forEach { index ->
                assertThat(loads[index]).isLessThan(peakLoad)
            }
        }
    }
}
