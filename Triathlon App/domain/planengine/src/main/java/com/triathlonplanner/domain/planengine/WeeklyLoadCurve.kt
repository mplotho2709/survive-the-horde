package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.TrainingPhase
import kotlin.math.roundToInt

/**
 * Computes the planned load (an abstract relative unit - see LoadCalculator for how actual
 * completed load is derived) for each week: <=8% growth per non-recovery week (within the
 * ceiling of AdaptationEngine.MAX_WEEK_OVER_WEEK_GROWTH), a cutback on recovery weeks, a rebound
 * slightly above the pre-cutback peak afterwards, and a hard reduction through Taper/RaceWeek.
 */
object WeeklyLoadCurve {

    // Calibrated to roughly match real-world TSS/TRIMP magnitudes (see LoadCalculator) so that
    // completed-vs-planned load ratios in AdaptationEngine are comparing like with like, rather
    // than an arbitrary internal-only scale.
    private const val STARTING_LOAD = 300.0
    private const val WEEKLY_GROWTH = 1.08
    private const val CUTBACK_FACTOR = 0.65
    private const val REBOUND_FACTOR = 1.05
    private const val TAPER_FACTOR = 0.55
    private const val RACE_WEEK_FACTOR = 0.30

    fun calculate(weeks: List<WeekPlan>): List<Int> {
        val loads = mutableListOf<Double>()
        var peakSoFar = STARTING_LOAD

        weeks.forEachIndexed { index, week ->
            val previous = weeks.getOrNull(index - 1)
            val load = when {
                week.phase == TrainingPhase.RACE_WEEK -> peakSoFar * RACE_WEEK_FACTOR
                week.phase == TrainingPhase.TAPER -> peakSoFar * TAPER_FACTOR
                week.isRecoveryWeek -> peakSoFar * CUTBACK_FACTOR
                index == 0 -> STARTING_LOAD
                previous?.isRecoveryWeek == true -> peakSoFar * REBOUND_FACTOR
                else -> loads[index - 1] * WEEKLY_GROWTH
            }

            if (week.phase !in listOf(TrainingPhase.TAPER, TrainingPhase.RACE_WEEK) && !week.isRecoveryWeek) {
                peakSoFar = load
            }
            loads += load
        }

        return loads.map { it.roundToInt() }
    }
}
