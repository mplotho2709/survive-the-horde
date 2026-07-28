package com.triathlonplanner.domain.planengine

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Converts a completed activity's raw metrics into the same abstract load unit PlanGenerator
 * uses for planned load, so [AdaptationEngine]'s loadRatio = completed/planned is comparing like
 * with like. Combining TSS (power), TRIMP (HR) and session-RPE this way is itself an open
 * problem in exercise science - these formulas are standard within each modality, but the
 * cross-modality calibration (e.g. the x3 factor on RPE load) is a pragmatic approximation, not a
 * validated equivalence.
 */
object LoadCalculator {

    /** Standard TSS: (duration_sec * NP * IF) / (FTP * 3600) * 100, where IF = NP/FTP. */
    fun cyclingTss(durationSec: Long, normalizedPowerW: Int, ftpWatts: Int): Int {
        require(ftpWatts > 0) { "ftpWatts must be positive" }
        val intensityFactor = normalizedPowerW.toDouble() / ftpWatts
        return (durationSec * normalizedPowerW * intensityFactor / (ftpWatts * 3600.0) * 100).roundToInt()
    }

    /**
     * Banister TRIMP using heart-rate reserve fraction, with the standard male exponential
     * weighting (0.64 * e^(1.92x)); falls back to a simplified %MaxHR^2 proxy when resting HR
     * is unknown and HRR can't be computed.
     */
    fun hrTrimp(durationSec: Long, avgHr: Int, maxHr: Int, restingHr: Int?): Int {
        require(maxHr > 0) { "maxHr must be positive" }
        val durationMin = durationSec / 60.0
        return if (restingHr != null && restingHr in 1 until maxHr) {
            val hrrFraction = ((avgHr - restingHr).toDouble() / (maxHr - restingHr)).coerceIn(0.0, 1.5)
            (durationMin * hrrFraction * 0.64 * exp(1.92 * hrrFraction)).roundToInt()
        } else {
            val pctMax = (avgHr.toDouble() / maxHr).coerceIn(0.0, 1.3)
            (durationMin * pctMax * pctMax * 100).roundToInt()
        }
    }

    /** Foster's session-RPE method (duration_min * RPE), scaled down ~3x to sit near TSS/TRIMP magnitude. */
    fun sessionRpeLoad(durationSec: Long, rpe1To10: Int): Int {
        require(rpe1To10 in 1..10) { "rpe1To10 must be between 1 and 10, was $rpe1To10" }
        val durationMin = durationSec / 60.0
        return (durationMin * rpe1To10 * 3.0).roundToInt()
    }
}
