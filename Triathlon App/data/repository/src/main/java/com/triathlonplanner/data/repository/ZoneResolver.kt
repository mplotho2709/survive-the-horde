package com.triathlonplanner.data.repository

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.domain.zones.HrZoneCalculator
import com.triathlonplanner.domain.zones.PowerZoneCalculator
import com.triathlonplanner.domain.zones.RunPaceZoneCalculator
import com.triathlonplanner.domain.zones.SwimPaceZoneCalculator
import com.triathlonplanner.domain.zones.ZoneRange

/** PACE is swim pace (sec/100m); RUN_PACE is run pace (sec/km) - different units, so they
 * must not share a case or the UI will render one with the other's label. */
enum class ZoneKind { HEART_RATE, POWER, PACE, RUN_PACE }

/** A resolved zone plus which calculator actually produced it - callers must not infer the unit
 * (bpm/W/pace) from discipline alone, since e.g. a bike zone falls back to heart rate when FTP
 * isn't set, and labeling that fallback as watts would be silently wrong. */
data class ResolvedZone(val range: ZoneRange, val kind: ZoneKind)

/**
 * Resolves a workout's stored (discipline, zone) pair into concrete bpm/watts/pace numbers using
 * the *current* profile - this is what makes editing max HR/FTP later re-contextualize the whole
 * plan instead of leaving stale numbers baked into old rows (see the plan's "store zones as
 * relative" decision). Bike falls back to heart rate when FTP isn't set, and swim falls back to
 * heart rate when CSS isn't set - HR response in swimming is dampened/delayed relative to pace
 * (see SwimPaceZoneCalculator's docs), so this is a deliberately rougher approximation, but a
 * heart-rate-based target still beats showing the user no zone at all until they run a CSS test.
 */
object ZoneResolver {

    fun resolve(discipline: Discipline, zone: IntensityZone?, profile: UserZoneProfile?): ResolvedZone? {
        if (zone == null || profile == null) return null
        return when (discipline) {
            Discipline.BIKE, Discipline.BRICK_BIKE ->
                profile.ftpWatts
                    ?.let { ResolvedZone(PowerZoneCalculator.zoneFor(it, zone), ZoneKind.POWER) }
                    ?: ResolvedZone(HrZoneCalculator.zoneFor(profile.maxHr, profile.restingHr, zone), ZoneKind.HEART_RATE)

            Discipline.SWIM ->
                profile.cssPaceSecPer100m
                    ?.let { ResolvedZone(SwimPaceZoneCalculator.zoneFor(it, zone), ZoneKind.PACE) }
                    ?: ResolvedZone(HrZoneCalculator.zoneFor(profile.maxHr, profile.restingHr, zone), ZoneKind.HEART_RATE)

            // Run prefers threshold pace for the same reason bike prefers power: it measures
            // external work directly, where HR lags, drifts with heat/fatigue and is blunted late
            // in long sessions. Falls back to HR when no run PB has been provided.
            Discipline.RUN, Discipline.BRICK_RUN ->
                profile.thresholdRunPaceSecPerKm
                    ?.let { ResolvedZone(RunPaceZoneCalculator.zoneFor(it, zone), ZoneKind.RUN_PACE) }
                    ?: ResolvedZone(HrZoneCalculator.zoneFor(profile.maxHr, profile.restingHr, zone), ZoneKind.HEART_RATE)

            Discipline.STRENGTH ->
                ResolvedZone(HrZoneCalculator.zoneFor(profile.maxHr, profile.restingHr, zone), ZoneKind.HEART_RATE)

            Discipline.REST -> null
        }
    }
}
