package com.triathlonplanner.domain.zones

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.UserZoneProfile
import kotlin.math.roundToInt

/** A concrete, discipline-specific race-day target with the unit it's expressed in. */
data class RacePaceTarget(
    val discipline: Discipline,
    val lowerBound: Int,
    val upperBound: Int,
    val unit: RacePaceUnit,
) {
    /** True when the two bounds collapsed to a single prescribed value rather than a range. */
    val isPoint: Boolean get() = lowerBound == upperBound
}

enum class RacePaceUnit { WATTS, SEC_PER_KM, SEC_PER_100M }

/**
 * Turns a race goal into concrete, discipline-specific intensity targets - the difference between
 * telling an athlete "ride Zone 4" and "ride 196-208 W".
 *
 * **Bike (%FTP by race distance).** Sustainable fraction of FTP falls as duration rises, because
 * the contribution of glycogen-dependent, lactate-producing work must shrink for the effort to
 * last. Published age-group targets converge on: Sprint 85-95%, Olympic 80-90%, 70.3 75-82%,
 * Full Iron 68-76% of FTP. These are intensity-factor (IF) ranges, and they are the reason a
 * single "race pace = Zone 4" label is wrong: Zone 4 is ~91-105% FTP, which is *nine* full
 * Iron-distance race intensities too hard.
 *
 * **Swim (CSS-relative).** CSS is by construction a ~critical-intensity anchor, so race pace is
 * expressed as an offset from it, widening for longer swims - see [RaceTimePredictor]'s swim-fade
 * model, which this deliberately mirrors so the predicted split and the prescribed pace agree.
 *
 * **Run (threshold-relative, or derived from the goal time).** When the athlete has stated a
 * target finish time, the run leg's required pace is computed directly from the time budget rather
 * than from a generic percentage - that is what makes the session genuinely race-specific. Falls
 * back to a threshold-relative band when no target time exists.
 */
object RacePaceCalculator {

    /** (lower, upper) fraction of FTP sustainable for the bike leg of each race distance. */
    private val BIKE_FTP_FRACTION = mapOf(
        Distance.SPRINT to (0.85 to 0.95),
        Distance.OLYMPIC to (0.80 to 0.90),
        Distance.HALF_IRON to (0.75 to 0.82),
        Distance.FULL_IRON to (0.68 to 0.76),
    )

    /**
     * (lower, upper) fraction of *threshold speed* sustainable for the run leg off the bike.
     * Tighter than the bike bands because the run follows an already-fatiguing effort; the long
     * course values sit well below threshold, which is exactly the polarization a "Zone 4 race
     * pace" label destroys.
     */
    private val RUN_THRESHOLD_SPEED_FRACTION = mapOf(
        Distance.SPRINT to (0.96 to 1.02),
        Distance.OLYMPIC to (0.92 to 0.97),
        Distance.HALF_IRON to (0.84 to 0.90),
        Distance.FULL_IRON to (0.76 to 0.83),
    )

    /** Bike race-pace band in watts, or null when the athlete has no FTP set. */
    fun bikeTarget(distance: Distance, profile: UserZoneProfile): RacePaceTarget? {
        val ftp = profile.ftpWatts ?: return null
        val (low, high) = BIKE_FTP_FRACTION.getValue(distance)
        return RacePaceTarget(
            discipline = Discipline.BIKE,
            lowerBound = (ftp * low).roundToInt(),
            upperBound = (ftp * high).roundToInt(),
            unit = RacePaceUnit.WATTS,
        )
    }

    /** Swim race-pace band in sec/100m, or null when the athlete has no CSS set. */
    fun swimTarget(distance: Distance, profile: UserZoneProfile): RacePaceTarget? {
        val css = profile.cssPaceSecPer100m ?: return null
        val racePace = RaceTimePredictor.estimateSwimLegTimeSec(css, 100, distance)
        // +-2 sec/100m of tolerance - tighter than a training zone, since race pacing is a
        // narrow target rather than a band to train within.
        return RacePaceTarget(
            discipline = Discipline.SWIM,
            lowerBound = racePace - 2,
            upperBound = racePace + 2,
            unit = RacePaceUnit.SEC_PER_100M,
        )
    }

    /**
     * Run race-pace band in sec/km. Prefers the pace implied by [targetFinishTimeSec] (a true
     * race-specific prescription); otherwise falls back to a threshold-relative band. Null when
     * neither a goal time nor a threshold pace is known.
     */
    fun runTarget(distance: Distance, profile: UserZoneProfile, targetFinishTimeSec: Int? = null): RacePaceTarget? {
        goalPaceSecPerKm(distance, profile, targetFinishTimeSec)?.let { goalPace ->
            return RacePaceTarget(Discipline.RUN, goalPace - 5, goalPace + 5, RacePaceUnit.SEC_PER_KM)
        }
        val threshold = profile.thresholdRunPaceSecPerKm ?: return null
        val (lowSpeed, highSpeed) = RUN_THRESHOLD_SPEED_FRACTION.getValue(distance)
        return RacePaceTarget(
            discipline = Discipline.RUN,
            lowerBound = (threshold / highSpeed).roundToInt(),
            upperBound = (threshold / lowSpeed).roundToInt(),
            unit = RacePaceUnit.SEC_PER_KM,
        )
    }

    /**
     * The run pace the athlete's stated goal time actually demands, in sec/km.
     *
     * Budgets the goal time across the three legs: the swim split is estimated from CSS and the
     * bike split from the %FTP band above (via a distance-specific reference speed), leaving the
     * remainder for the run. Returns null when the inputs to that budget are missing, or when the
     * goal is so aggressive that no time remains for the run - the caller should surface that as a
     * realism problem rather than prescribing an impossible pace.
     */
    fun goalPaceSecPerKm(distance: Distance, profile: UserZoneProfile, targetFinishTimeSec: Int?): Int? {
        val target = targetFinishTimeSec ?: return null
        val css = profile.cssPaceSecPer100m ?: return null
        val swimSec = RaceTimePredictor.estimateSwimLegTimeSec(css, distance.swimMeters, distance)
        val bikeSec = estimatedBikeSplitSec(distance) ?: return null
        val runSec = target - swimSec - bikeSec - transitionAllowanceSec(distance)
        if (runSec <= 0) return null
        return (runSec / (distance.runMeters / 1000.0)).roundToInt()
    }

    /**
     * Bike split estimated from a distance-typical age-group average speed. Deliberately *not*
     * derived from FTP: converting power to speed requires rider mass, CdA and course profile,
     * none of which this app collects, so an FTP-based split would carry large unstated error.
     * These are coaching-reference speeds, not a physical model - they exist only to leave a
     * sane remainder for the run budget above.
     */
    private fun estimatedBikeSplitSec(distance: Distance): Int? {
        val kph = when (distance) {
            Distance.SPRINT -> 32.0
            Distance.OLYMPIC -> 31.0
            Distance.HALF_IRON -> 29.0
            Distance.FULL_IRON -> 27.0
        }
        return ((distance.bikeMeters / 1000.0) / kph * 3600).roundToInt()
    }

    /** Combined T1 + T2 allowance; longer races have longer, less rushed transitions. */
    private fun transitionAllowanceSec(distance: Distance): Int = when (distance) {
        Distance.SPRINT -> 3 * 60
        Distance.OLYMPIC -> 5 * 60
        Distance.HALF_IRON -> 8 * 60
        Distance.FULL_IRON -> 12 * 60
    }
}
