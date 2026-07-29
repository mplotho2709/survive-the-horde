package com.triathlonplanner.domain.zones

import com.triathlonplanner.core.model.Distance
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Estimates "where the user currently stands" relative to a target race time, and how much
 * weekly-hours volume that gap justifies. Deliberately does NOT predict a bike-leg time - doing
 * so from FTP alone would need course profile, rider weight, and aerodynamics data this app
 * doesn't collect, and inventing a flat-course approximation would introduce real, unstated
 * error. The estimate below is swim (CSS-based) + run (Riegel-based) only; FTP continues to set
 * %FTP training-zone targets elsewhere, it's just not stretched into a time prediction.
 */
object RaceTimePredictor {

    /** Pete Riegel's 1977 race-time extrapolation exponent - still the most widely used formula
     * for predicting a race time at one distance from a known time at another. Most accurate when
     * the two distances aren't too far apart. */
    const val RIEGEL_EXPONENT = 1.06

    // Coaching-judgment fade applied on top of CSS pace, NOT independently research-cited (unlike
    // the Riegel exponent above). CSS reflects a near-maximal short time-trial pace; a full race
    // swim leg is deliberately paced easier, more so the longer the leg - these offsets are a
    // reasonable approximation of that, in the same spirit as RecommendedAvailability's own
    // "coaching judgment, not domain logic" defaults.
    private val SWIM_FADE_FRACTION = mapOf(
        Distance.SPRINT to 0.00,
        Distance.OLYMPIC to 0.02,
        Distance.HALF_IRON to 0.05,
        Distance.FULL_IRON to 0.08,
    )

    /** T2 = T1 * (D2/D1)^1.06. */
    fun riegelPredictedTimeSec(knownDistanceM: Double, knownTimeSec: Int, targetDistanceM: Double): Int {
        require(knownDistanceM > 0 && targetDistanceM > 0) { "distances must be positive" }
        require(knownTimeSec > 0) { "knownTimeSec must be positive" }
        return (knownTimeSec * (targetDistanceM / knownDistanceM).pow(RIEGEL_EXPONENT)).roundToInt()
    }

    fun estimateSwimLegTimeSec(cssPaceSecPer100m: Int, raceSwimMeters: Int, distance: Distance): Int {
        require(cssPaceSecPer100m > 0) { "cssPaceSecPer100m must be positive" }
        val fade = SWIM_FADE_FRACTION.getValue(distance)
        val racePaceSecPer100m = cssPaceSecPer100m * (1.0 + fade)
        return (racePaceSecPer100m * (raceSwimMeters / 100.0)).roundToInt()
    }

    /** Swim + run only - see class doc for why bike is intentionally excluded. */
    fun estimateCurrentFinishTimeSec(distance: Distance, cssPaceSecPer100m: Int, runPbDistanceM: Double, runPbTimeSec: Int): Int {
        val swimSec = estimateSwimLegTimeSec(cssPaceSecPer100m, distance.swimMeters, distance)
        val runSec = riegelPredictedTimeSec(runPbDistanceM, runPbTimeSec, distance.runMeters.toDouble())
        return swimSec + runSec
    }

    /** Positive = the target is faster than current fitness implies (a real gap to close);
     * zero/negative = the target is already achievable at current fitness. */
    fun requiredImprovementPercent(currentEstimateSec: Int, targetFinishTimeSec: Int): Double {
        require(currentEstimateSec > 0) { "currentEstimateSec must be positive" }
        return ((currentEstimateSec - targetFinishTimeSec) / currentEstimateSec.toDouble()) * 100.0
    }

    /** Every 1% of required improvement adds ~1.5% more weekly hours, hard-capped at +35% over
     * baseline - the bounded-growth guardrail from the "diminishing returns beyond ~14h/week"
     * training-volume research (a large gap nudges volume up meaningfully but never inflates
     * without limit). Zero or negative improvement (target already achievable) means no bump. */
    fun recommendedHoursBumpFraction(requiredImprovementPercent: Double): Double =
        (requiredImprovementPercent / 100.0 * 1.5).coerceIn(0.0, 0.35)
}
