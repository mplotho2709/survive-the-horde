package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.TrainingPhase

data class WeekPlan(val weekIndex: Int, val phase: TrainingPhase, val isRecoveryWeek: Boolean)

data class PeriodizationResult(val weeks: List<WeekPlan>, val warnings: List<String>)

/**
 * Splits the available weeks into Base/Build/Peak/Taper/RaceWeek. Peak and Taper are never
 * compressed - if the runway is short, Base shrinks first, then Build, down to a 1-week floor
 * each. A recovery/cutback week lands every 4th week (weekIndex % 4 == 0), except inside
 * Taper/RaceWeek, which are already a deliberate volume reduction.
 */
object PeriodizationCalculator {

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

        val weeks = mutableListOf<WeekPlan>()
        var weekIndex = 1

        repeat(baseWeeks) {
            weeks += WeekPlan(weekIndex, TrainingPhase.BASE, isRecoveryWeek(weekIndex))
            weekIndex++
        }
        repeat(buildWeeks) {
            weeks += WeekPlan(weekIndex, TrainingPhase.BUILD, isRecoveryWeek(weekIndex))
            weekIndex++
        }
        repeat(peakWeeks) {
            weeks += WeekPlan(weekIndex, TrainingPhase.PEAK, isRecoveryWeek(weekIndex))
            weekIndex++
        }
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

    private fun isRecoveryWeek(weekIndex: Int): Boolean = weekIndex % 4 == 0
}
