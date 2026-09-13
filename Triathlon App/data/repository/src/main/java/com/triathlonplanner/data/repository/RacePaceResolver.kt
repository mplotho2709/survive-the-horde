package com.triathlonplanner.data.repository

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.RaceGoal
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutType
import com.triathlonplanner.domain.zones.RacePaceCalculator
import com.triathlonplanner.domain.zones.RacePaceSource
import com.triathlonplanner.domain.zones.RacePaceTarget
import com.triathlonplanner.domain.zones.RacePaceUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A race-day target rendered down to the strings a card needs: the number to hold, its unit, and
 * one line saying where it came from.
 *
 * Formatted here rather than in each feature module because the alternative - handing every
 * consumer a [RacePaceTarget] and a unit enum - has each of them re-implementing three unit
 * formatters, which is how the same prescription ends up written two different ways on two tabs.
 * [value] and [unit] stay separate so the UI can set the unit smaller beside the number; joining
 * them early would force every caller to re-split the string to do that.
 *
 * Deliberately *not* the design system's `RacePaceTargetView`: :data:repository must not depend on
 * a UI module, so the feature layer performs the one-line mapping.
 */
data class RacePacePrescription(val value: String, val unit: String, val caption: String) {
    /** Compact one-line form for list rows, where a caption would not fit. */
    val shortLabel: String get() = "Race target $value $unit"
}

/**
 * Turns a planned race-pace session into a concrete prescription for *this* athlete's race.
 *
 * Companion to [ZoneResolver], and deliberately separate from it. A training zone and a race-day
 * target answer different questions: a zone is a band to train within, derived only from the
 * athlete's physiology, whereas a race target also depends on how long the race lasts. Collapsing
 * the two is what produces the "race pace = Zone 4" error [RacePaceCalculator] exists to correct -
 * Zone 4 is ~91-105% of FTP, and no one rides an Iron-distance bike leg there.
 *
 * Returns null - rather than a plausible-looking guess - whenever the inputs are missing: no race
 * goal, no profile, or no FTP/CSS/threshold pace for the discipline in question. The session still
 * renders with its zone label, which is a weaker prescription but an honest one.
 */
object RacePaceResolver {

    fun resolve(
        discipline: Discipline,
        workoutType: WorkoutType,
        goal: RaceGoal?,
        profile: UserZoneProfile?,
    ): RacePacePrescription? {
        if (workoutType != WorkoutType.RACE_PACE) return null
        if (goal == null || profile == null) return null
        val distance = goal.distance

        return when (discipline) {
            Discipline.BIKE, Discipline.BRICK_BIKE ->
                RacePaceCalculator.bikeTarget(distance, profile)?.let { bike(it, distance, profile) }

            Discipline.SWIM ->
                RacePaceCalculator.swimTarget(distance, profile)?.let { swim(it, distance, profile) }

            Discipline.RUN, Discipline.BRICK_RUN ->
                RacePaceCalculator.runTarget(distance, profile, goal.targetFinishTimeSec)
                    ?.let { run(it, distance, goal.targetFinishTimeSec, offTheBike = discipline == Discipline.BRICK_RUN) }

            Discipline.STRENGTH, Discipline.REST -> null
        }
    }

    private fun bike(target: RacePaceTarget, distance: Distance, profile: UserZoneProfile): RacePacePrescription {
        // Unreachable given bikeTarget's contract (no FTP, no target), but degrading to a caption
        // that simply omits the derivation beats asserting on it in a UI path.
        val ftp = profile.ftpWatts ?: return prescription(target, "${legLabel(distance, Discipline.BIKE)} race power.")
        // Recovered from the target rather than read from the calculator's table: one source of
        // truth for the band, so the caption can never drift from the number above it.
        val lowPct = (100.0 * target.lowerBound / ftp).roundToInt()
        val highPct = (100.0 * target.upperBound / ftp).roundToInt()
        return prescription(
            target,
            "${legLabel(distance, Discipline.BIKE)} bike - $lowPct-$highPct% of your $ftp W FTP. " +
                "Longer races are ridden further below threshold, not at it.",
        )
    }

    private fun swim(target: RacePaceTarget, distance: Distance, profile: UserZoneProfile): RacePacePrescription {
        val css = profile.cssPaceSecPer100m
        val caption = if (css == null) {
            // As in [bike]: unreachable, since swimTarget needs CSS to produce anything at all.
            "${legLabel(distance, Discipline.SWIM)} swim race pace."
        } else {
            val mid = (target.lowerBound + target.upperBound) / 2
            val drift = mid - css
            val relation = when {
                drift > 0 -> "${abs(drift)} sec/100m slower than"
                drift < 0 -> "${abs(drift)} sec/100m faster than"
                else -> "right on"
            }
            "${legLabel(distance, Discipline.SWIM)} swim - $relation your ${formatPace(css)} CSS."
        }
        return prescription(target, caption)
    }

    private fun run(
        target: RacePaceTarget,
        distance: Distance,
        targetFinishTimeSec: Int?,
        offTheBike: Boolean,
    ): RacePacePrescription {
        val leg = legLabel(distance, Discipline.RUN) + " run" + if (offTheBike) " off the bike" else ""
        // Which source the calculator actually used decides what the caption may claim. A
        // threshold-derived band is a generic capability estimate; a goal-derived pace is what the
        // athlete's stated finish time actually demands, and saying so is the point of showing it.
        val caption = if (target.source == RacePaceSource.GOAL_TIME && targetFinishTimeSec != null) {
            "$leg - what your ${formatClock(targetFinishTimeSec)} goal needs once estimated swim, " +
                "bike and transition times are taken out."
        } else if (targetFinishTimeSec != null) {
            // The goal was either unreachable or so relaxed the run isn't the limiting leg; either
            // way it can't be quoted as the source of this number.
            "$leg, from your threshold pace - your ${formatClock(targetFinishTimeSec)} goal doesn't " +
                "pin down a raceable run pace."
        } else {
            "$leg, from your threshold pace. " +
                "Set a goal finish time to pace this from the race itself instead."
        }
        return prescription(target, caption)
    }

    private fun prescription(target: RacePaceTarget, caption: String): RacePacePrescription {
        val bounds = when (target.unit) {
            RacePaceUnit.WATTS -> target.lowerBound.toString() to target.upperBound.toString()
            // Fewer seconds is faster, so the numerically lower bound is printed first and the
            // range reads fast-to-slow, matching how a pace band is spoken.
            RacePaceUnit.SEC_PER_100M, RacePaceUnit.SEC_PER_KM ->
                formatPace(target.lowerBound) to formatPace(target.upperBound)
        }
        val unit = when (target.unit) {
            RacePaceUnit.WATTS -> "W"
            RacePaceUnit.SEC_PER_100M -> "/100m"
            RacePaceUnit.SEC_PER_KM -> "/km"
        }
        val value = if (target.isPoint) bounds.first else "${bounds.first}-${bounds.second}"
        return RacePacePrescription(value = value, unit = unit, caption = caption)
    }

    /** e.g. "1900 m", "90 km", "21.1 km" - the leg the target applies to, in its natural unit. */
    private fun legLabel(distance: Distance, discipline: Discipline): String = when (discipline) {
        Discipline.SWIM -> "${distance.swimMeters} m"
        Discipline.BIKE -> formatKm(distance.bikeMeters)
        else -> formatKm(distance.runMeters)
    }

    private fun formatKm(meters: Int): String {
        val km = meters / 1000.0
        return if (km == km.roundToInt().toDouble()) "${km.roundToInt()} km" else "%.1f km".format(km)
    }

    private fun formatPace(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

    private fun formatClock(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}:${minutes.toString().padStart(2, '0')}" else "${minutes}min"
    }
}
