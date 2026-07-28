package com.triathlonplanner.domain.zones

import com.triathlonplanner.core.model.IntensityZone
import kotlin.math.roundToInt

/**
 * 7-zone Coggan power model, from FTP. Zone 7 is open-ended in reality; capped at 300% of FTP
 * here purely so callers get a finite range to render (e.g. a zone bar chart) rather than
 * meaning anything physiologically.
 */
object PowerZoneCalculator {

    private val ZONE_LABELS = listOf(
        "Active Recovery",
        "Endurance",
        "Tempo",
        "Threshold",
        "VO2max",
        "Anaerobic Capacity",
        "Neuromuscular Power",
    )

    // (lowerPercent, upperPercent) of FTP, one pair per zone 1..7
    private val FTP_BOUNDS = listOf(
        0.0 to 0.55,
        0.56 to 0.75,
        0.76 to 0.90,
        0.91 to 1.05,
        1.06 to 1.20,
        1.21 to 1.50,
        1.51 to 3.00,
    )

    fun calculateZones(ftpWatts: Int): List<ZoneRange> {
        require(ftpWatts > 0) { "ftpWatts must be positive" }
        return FTP_BOUNDS.mapIndexed { index, (lowPct, highPct) ->
            ZoneRange(
                zone = IntensityZone(index + 1),
                label = ZONE_LABELS[index],
                lowerBound = (ftpWatts * lowPct).roundToInt(),
                upperBound = (ftpWatts * highPct).roundToInt(),
            )
        }
    }

    fun zoneFor(ftpWatts: Int, zone: IntensityZone): ZoneRange =
        calculateZones(ftpWatts)[zone.level - 1]

    /** FTP estimate from a 20-minute all-out test: FTP is ~95% of the 20-min average power. */
    fun estimateFtpFrom20MinTest(avgPowerWatts20Min: Int): Int =
        (avgPowerWatts20Min * 0.95).roundToInt()
}
