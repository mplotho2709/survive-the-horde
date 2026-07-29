package com.triathlonplanner.domain.zones

import com.triathlonplanner.core.model.IntensityZone
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Run pace zones anchored on **threshold pace** (Daniels' "T-pace"), the run-specific analogue of
 * FTP on the bike and CSS in the water.
 *
 * Physiology: lactate threshold is the highest intensity at which lactate production and clearance
 * remain in equilibrium. Operationally it is very close to the pace a trained runner can hold for
 * ~60 minutes, which is why Daniels defines T-pace as roughly one-hour race pace. Anchoring zones
 * here (rather than on %MaxHR) matters for running because pace is a direct measure of external
 * work, whereas heart rate lags, drifts upward with cardiac drift and heat, and is blunted by
 * fatigue - so HR-only run zones systematically misprescribe long and hard sessions alike.
 *
 * [thresholdPaceFromRacePb] derives T-pace from any single race result by using Riegel's formula
 * to solve for the distance the athlete could cover in exactly one hour, then taking the pace at
 * that distance. Validated against Daniels' VDOT tables: a 20:00 5K yields ~4:15/km (VDOT ~50,
 * published T-pace 4:15/km) and a 45:00 10K yields ~4:34/km.
 */
object RunPaceZoneCalculator {

    private const val ONE_HOUR_SEC = 3600.0

    private val ZONE_LABELS = listOf("Recovery", "Easy / Aerobic", "Tempo", "Threshold", "VO2max / Interval")

    // (lowerSpeedFraction, upperSpeedFraction) of threshold *speed*, one pair per zone 1..5.
    // Expressed as speed (not pace) because the zone model is multiplicative in velocity; the
    // conversion to sec/km below inverts them, so a faster speed becomes a smaller pace number.
    private val THRESHOLD_SPEED_BOUNDS = listOf(
        0.65 to 0.79,
        0.80 to 0.88,
        0.89 to 0.94,
        0.95 to 1.05,
        1.06 to 1.20,
    )

    /**
     * Threshold pace (sec per km) from a single race personal best.
     *
     * Solves Riegel's `T2 = T1 * (D2/D1)^1.06` for the distance `D2` where `T2` is one hour:
     * `D2 = D1 * (3600 / T1)^(1 / 1.06)`. The pace at that distance is, by definition of T-pace,
     * the athlete's threshold pace.
     */
    fun thresholdPaceFromRacePb(pbDistanceM: Double, pbTimeSec: Int): Int {
        require(pbDistanceM > 0) { "pbDistanceM must be positive" }
        require(pbTimeSec > 0) { "pbTimeSec must be positive" }
        val oneHourDistanceM = pbDistanceM * (ONE_HOUR_SEC / pbTimeSec).pow(1.0 / RaceTimePredictor.RIEGEL_EXPONENT)
        return (ONE_HOUR_SEC / (oneHourDistanceM / 1000.0)).roundToInt()
    }

    /** Five pace zones (sec per km). Per [ZoneRange], lowerBound is the *faster* end of the range. */
    fun calculateZones(thresholdPaceSecPerKm: Int): List<ZoneRange> {
        require(thresholdPaceSecPerKm > 0) { "thresholdPaceSecPerKm must be positive" }
        return THRESHOLD_SPEED_BOUNDS.mapIndexed { index, (lowSpeed, highSpeed) ->
            ZoneRange(
                zone = IntensityZone(index + 1),
                label = ZONE_LABELS[index],
                // Faster speed -> smaller sec/km, so the *upper* speed bound gives the lower bound.
                lowerBound = (thresholdPaceSecPerKm / highSpeed).roundToInt(),
                upperBound = (thresholdPaceSecPerKm / lowSpeed).roundToInt(),
            )
        }
    }

    fun zoneFor(thresholdPaceSecPerKm: Int, zone: IntensityZone): ZoneRange =
        calculateZones(thresholdPaceSecPerKm)[zone.level - 1]
}
