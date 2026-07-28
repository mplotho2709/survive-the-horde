package com.triathlonplanner.domain.zones

import com.triathlonplanner.core.model.IntensityZone
import kotlin.math.roundToInt

/**
 * 5-zone heart-rate model. Prefers Karvonen/%HRR (requires resting HR) since it's more accurate
 * than raw %MaxHR; falls back to %MaxHR when resting HR is unknown, per PlanGenerator's
 * graceful-degradation design (documented limitation: true LTHR-based zones would be more
 * accurate still, deferred to a later version once a threshold test exists).
 */
object HrZoneCalculator {

    private val ZONE_LABELS = listOf("Recovery", "Aerobic", "Tempo", "Threshold", "VO2max")

    // (lowerPercent, upperPercent) of heart-rate reserve, one pair per zone 1..5
    private val HRR_BOUNDS = listOf(
        0.50 to 0.60,
        0.60 to 0.70,
        0.70 to 0.80,
        0.80 to 0.90,
        0.90 to 1.00,
    )

    // (lowerPercent, upperPercent) of max HR, one pair per zone 1..5 - used only when resting HR
    // is unknown and Karvonen can't be computed. Capped at 100% of maxHr (a zone can never
    // exceed the person's own max by definition) - same round breakpoints as HRR_BOUNDS above.
    private val PERCENT_MAX_BOUNDS = listOf(
        0.0 to 0.60,
        0.60 to 0.70,
        0.70 to 0.80,
        0.80 to 0.90,
        0.90 to 1.00,
    )

    fun calculateZones(maxHr: Int, restingHr: Int?): List<ZoneRange> {
        require(maxHr > 0) { "maxHr must be positive" }
        return if (restingHr != null && restingHr > 0 && restingHr < maxHr) {
            karvonenZones(maxHr, restingHr)
        } else {
            percentMaxHrZones(maxHr)
        }
    }

    /** bpm target for a single zone using whichever method [calculateZones] would pick. */
    fun zoneFor(maxHr: Int, restingHr: Int?, zone: IntensityZone): ZoneRange =
        calculateZones(maxHr, restingHr)[zone.level - 1]

    private fun karvonenZones(maxHr: Int, restingHr: Int): List<ZoneRange> {
        val hrr = maxHr - restingHr
        return HRR_BOUNDS.mapIndexed { index, (lowPct, highPct) ->
            ZoneRange(
                zone = IntensityZone(index + 1),
                label = ZONE_LABELS[index],
                lowerBound = (restingHr + lowPct * hrr).roundToInt(),
                upperBound = (restingHr + highPct * hrr).roundToInt(),
            )
        }
    }

    private fun percentMaxHrZones(maxHr: Int): List<ZoneRange> =
        PERCENT_MAX_BOUNDS.mapIndexed { index, (lowPct, highPct) ->
            ZoneRange(
                zone = IntensityZone(index + 1),
                label = ZONE_LABELS[index],
                lowerBound = (maxHr * lowPct).roundToInt(),
                upperBound = (maxHr * highPct).roundToInt(),
            )
        }
}
