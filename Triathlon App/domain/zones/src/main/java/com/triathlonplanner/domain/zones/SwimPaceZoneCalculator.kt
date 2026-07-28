package com.triathlonplanner.domain.zones

import com.triathlonplanner.core.model.IntensityZone

/**
 * 5-zone swim pace model relative to Critical Swim Speed (CSS), expressed as seconds per 100m.
 * Unlike HR/power zones, a *lower* bound here is the faster (harder) end of the zone - see
 * [ZoneRange] docs. There is no single industry-standard CSS zone table (unlike Coggan power
 * zones); these offsets follow common CSS-based swim coaching practice.
 *
 * Swim intensity is pace-based rather than HR-based because HR response in swimming is dampened
 * and delayed by the horizontal body position, hydrostatic pressure on the chest, and skin
 * cooling - importing a run/bike %MaxHR zone onto swim effort would be invalid.
 */
object SwimPaceZoneCalculator {

    private val ZONE_LABELS = listOf("Easy", "Aerobic", "CSS / Threshold", "Fast Interval", "Sprint")

    // (fasterOffsetSec, slowerOffsetSec) applied to CSS pace-per-100m, one pair per zone 1..5.
    // Zone 1 is the slowest/easiest; zone 5 is faster than CSS itself.
    private val CSS_OFFSET_BOUNDS_SEC = listOf(
        8 to 12,
        4 to 8,
        -1 to 3,
        -4 to -1,
        -8 to -4,
    )

    fun calculateZones(cssPaceSecPer100m: Int): List<ZoneRange> {
        require(cssPaceSecPer100m > 0) { "cssPaceSecPer100m must be positive" }
        return CSS_OFFSET_BOUNDS_SEC.mapIndexed { index, (fasterOffset, slowerOffset) ->
            ZoneRange(
                zone = IntensityZone(index + 1),
                label = ZONE_LABELS[index],
                lowerBound = cssPaceSecPer100m + fasterOffset,
                upperBound = cssPaceSecPer100m + slowerOffset,
            )
        }
    }

    fun zoneFor(cssPaceSecPer100m: Int, zone: IntensityZone): ZoneRange =
        calculateZones(cssPaceSecPer100m)[zone.level - 1]

    /** CSS pace (sec/100m) from a 400m + 200m time trial: CSS = (t400 - t200) / (d400 - d200). */
    fun estimateCssFromTimeTrial(time400mSec: Int, time200mSec: Int): Int {
        require(time400mSec > time200mSec) { "400m time must be greater than 200m time" }
        val secPerMeter = (time400mSec - time200mSec) / (400.0 - 200.0)
        return (secPerMeter * 100).let { kotlin.math.round(it).toInt() }
    }
}
