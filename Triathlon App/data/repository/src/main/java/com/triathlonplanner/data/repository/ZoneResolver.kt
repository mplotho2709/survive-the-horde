package com.triathlonplanner.data.repository

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.domain.zones.HrZoneCalculator
import com.triathlonplanner.domain.zones.PowerZoneCalculator
import com.triathlonplanner.domain.zones.SwimPaceZoneCalculator
import com.triathlonplanner.domain.zones.ZoneRange

enum class ZoneKind { HEART_RATE, POWER, PACE }

/** A resolved zone plus which calculator actually produced it - callers must not infer the unit
 * (bpm/W/pace) from discipline alone, since e.g. a bike zone falls back to heart rate when FTP
 * isn't set, and labeling that fallback as watts would be silently wrong. */
data class ResolvedZone(val range: ZoneRange, val kind: ZoneKind)

/**
 * Resolves a workout's stored (discipline, zone) pair into concrete bpm/watts/pace numbers using
 * the *current* profile - this is what makes editing max HR/FTP later re-contextualize the whole
 * plan instead of leaving stale numbers baked into old rows (see the plan's "store zones as
 * relative" decision). Returns null when the profile lacks what that discipline needs (e.g. a
 * swim zone requested but no CSS pace set - there's no HR fallback for swim, see PlanGenerator's
 * rationale for why swim is pace-based only).
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
                profile.cssPaceSecPer100m?.let { ResolvedZone(SwimPaceZoneCalculator.zoneFor(it, zone), ZoneKind.PACE) }

            Discipline.RUN, Discipline.BRICK_RUN, Discipline.STRENGTH ->
                ResolvedZone(HrZoneCalculator.zoneFor(profile.maxHr, profile.restingHr, zone), ZoneKind.HEART_RATE)

            Discipline.REST -> null
        }
    }
}
