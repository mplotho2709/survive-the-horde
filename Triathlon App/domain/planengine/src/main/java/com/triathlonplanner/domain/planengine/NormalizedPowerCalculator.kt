package com.triathlonplanner.domain.planengine

import java.time.Instant
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Normalized Power: 30s rolling average of power, each raised to the 4th power, averaged, then
 * take the 4th root. Health Connect doesn't compute this itself, so we do it here from raw
 * [PowerSample]s - the one case where AdaptationEngine's pipeline needs raw samples rather than
 * an aggregate, per the plan's Health Connect integration design.
 */
object NormalizedPowerCalculator {

    data class PowerSample(val time: Instant, val watts: Int)

    private const val ROLLING_WINDOW_SEC = 30

    fun calculate(samples: List<PowerSample>): Int? {
        if (samples.isEmpty()) return null
        val sorted = samples.sortedBy { it.time }
        val start = sorted.first().time
        val end = sorted.last().time
        val totalDurationSec = end.epochSecond - start.epochSecond
        if (totalDurationSec < ROLLING_WINDOW_SEC) {
            // Too short for a meaningful rolling average; fall back to a plain average.
            return sorted.map { it.watts }.average().roundToInt()
        }

        val rollingAverages = mutableListOf<Double>()
        var windowStartIndex = 0
        var windowSum = 0.0
        var windowCount = 0

        // Two-pointer sliding window over time, since samples aren't necessarily evenly spaced.
        for (i in sorted.indices) {
            windowSum += sorted[i].watts
            windowCount++
            while (sorted[i].time.epochSecond - sorted[windowStartIndex].time.epochSecond > ROLLING_WINDOW_SEC) {
                windowSum -= sorted[windowStartIndex].watts
                windowCount--
                windowStartIndex++
            }
            rollingAverages += windowSum / windowCount
        }

        val meanOfFourthPowers = rollingAverages.map { it.pow(4) }.average()
        return meanOfFourthPowers.pow(0.25).roundToInt()
    }
}
