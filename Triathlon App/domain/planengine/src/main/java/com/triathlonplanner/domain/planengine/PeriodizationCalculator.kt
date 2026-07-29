package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.TrainingPhase

data class WeekPlan(val weekIndex: Int, val phase: TrainingPhase, val isRecoveryWeek: Boolean)

data class PeriodizationResult(val weeks: List<WeekPlan>, val warnings: List<String>)

/**
 * Splits the available weeks into Base/Build/Peak/Taper/RaceWeek. Peak and Taper are never
 * compressed - if the runway is short, Base shrinks first, then Build, down to a 1-week floor
 * each.
 *
 * Recovery ("cutback"/deload) weeks are scheduled **dynamically against phase structure**, not on
 * a fixed calendar modulus. A deload exists to let accumulated fatigue dissipate so the adaptation
 * it triggered can express itself, which means its placement only makes sense relative to the
 * loading block it follows. Three placement rules fall out of that:
 *
 * - Never on the **first week of a phase**: no fatigue has accumulated in the new block yet, and a
 *   deload there blunts the intensity step-up that defines the phase transition.
 * - Never on the **final Peak week**: that week is the highest-quality race-specific work in the
 *   plan and immediately precedes the taper, which is itself a large reduction. Deloading into a
 *   taper wastes the peak and detrains.
 * - Otherwise after every [RECOVERY_INTERVAL_WEEKS] consecutive loading weeks, deferring by a week
 *   when the natural slot is blocked by either rule above.
 */
object PeriodizationCalculator {

    /** Consecutive loading weeks before a cutback. Three-up/one-down is the common age-group
     * pattern; masters or high-stress athletes often do better on two-up/one-down, which is a
     * future personalization hook rather than a fixed constant. */
    const val RECOVERY_INTERVAL_WEEKS = 4

    fun calculate(distance: Distance, totalWeeks: Int): PeriodizationResult {
        require(totalWeeks >= 4) { "totalWeeks must be at least 4, was $totalWeeks" }

        val warnings = mutableListOf<String>()
        if (totalWeeks < distance.minWeeks) {
            warnings += "$totalWeeks weeks is tight for a ${distance.displayName} " +
                "(recommended minimum is ${distance.minWeeks}) - the plan will be aggressive."
        }

        val taperWeeks = distance.taperWeeks
        val peakWeeks = maxOf(1, Math.round(totalWeeks * distance.peakPercent).toInt())
        val buildWeeksIdeal = maxOf(1, Math.round(totalWeeks * distance.buildPercent).toInt())
        val baseWeeksIdeal = totalWeeks - taperWeeks - peakWeeks - buildWeeksIdeal

        val (baseWeeks, buildWeeks) = if (baseWeeksIdeal >= 1) {
            baseWeeksIdeal to buildWeeksIdeal
        } else {
            // Base compressed to its 1-week floor; absorb the remaining shortfall into Build.
            val compressedBuild = maxOf(1, totalWeeks - taperWeeks - peakWeeks - 1)
            1 to compressedBuild
        }

        // Loading phases first, without recovery marks - placement needs the whole sequence.
        val loadingPhases = buildList {
            repeat(baseWeeks) { add(TrainingPhase.BASE) }
            repeat(buildWeeks) { add(TrainingPhase.BUILD) }
            repeat(peakWeeks) { add(TrainingPhase.PEAK) }
        }
        val recoveryFlags = scheduleRecoveryWeeks(loadingPhases)

        val weeks = mutableListOf<WeekPlan>()
        loadingPhases.forEachIndexed { index, phase ->
            weeks += WeekPlan(index + 1, phase, recoveryFlags[index])
        }
        var weekIndex = loadingPhases.size + 1
        // All taper weeks but the last are TAPER; the final week (race week) is RACE_WEEK.
        repeat(taperWeeks - 1) {
            weeks += WeekPlan(weekIndex, TrainingPhase.TAPER, isRecoveryWeek = false)
            weekIndex++
        }
        weeks += WeekPlan(weekIndex, TrainingPhase.RACE_WEEK, isRecoveryWeek = false)

        check(weeks.size == totalWeeks) {
            "Internal error: allocated ${weeks.size} weeks, expected $totalWeeks"
        }

        return PeriodizationResult(weeks, warnings)
    }

    /**
     * Marks cutback weeks across the Base/Build/Peak sequence, honouring the placement rules in
     * the class docs. When a slot is blocked the counter is *not* reset, so the deload lands on
     * the next eligible week rather than being skipped entirely.
     */
    private fun scheduleRecoveryWeeks(phases: List<TrainingPhase>): List<Boolean> {
        val flags = MutableList(phases.size) { false }
        val lastPeakIndex = phases.indexOfLast { it == TrainingPhase.PEAK }
        var consecutiveLoadWeeks = 0

        phases.forEachIndexed { index, phase ->
            consecutiveLoadWeeks++
            if (consecutiveLoadWeeks < RECOVERY_INTERVAL_WEEKS) return@forEachIndexed

            val isFirstWeekOfPhase = index == 0 || phases[index - 1] != phase
            val isFinalPeakWeek = index == lastPeakIndex
            if (isFirstWeekOfPhase || isFinalPeakWeek) return@forEachIndexed

            flags[index] = true
            consecutiveLoadWeeks = 0
        }
        return flags
    }
}
