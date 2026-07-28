package com.triathlonplanner.data.repository

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.domain.zones.HrZoneCalculator
import com.triathlonplanner.domain.zones.PowerZoneCalculator
import com.triathlonplanner.domain.zones.SwimPaceZoneCalculator
import com.triathlonplanner.domain.zones.ZoneRange

/**
 * Resolves a workout's stored (discipline, zone) pair into concrete bpm/watts/pace numbers using
 * the *current* profile - this is what makes editing max HR/FTP later re-contextualize the whole
 * plan instead of leaving stale numbers baked into old rows (see the plan's "store zones as
 * relative" decision). Returns null when the profile lacks what that discipline needs (e.g. bike
 * zone requested but no FTP set - falls back to HR further up the call chain, not here).
 */
object ZoneResolver {

    fun resolve(discipline: Discipline, zone: IntensityZone?, profile: UserZoneProfile?): ZoneRange? {
        if (zone == null || profile == null) return null
        return when (discipline) {
            Discipline.BIKE, Discipline.BRICK_BIKE ->
                profile.ftpWatts?.let { PowerZoneCalculator.zoneFor(it, zone) }
                    ?: HrZoneCalculator.zoneFor(profile.maxHr, profile.restingHr, zone)

            Discipline.SWIM ->
                profile.cssPaceSecPer100m?.let { SwimPaceZoneCalculator.zoneFor(it, zone) }

            Discipline.RUN, Discipline.BRICK_RUN, Discipline.STRENGTH ->
                HrZoneCalculator.zoneFor(profile.maxHr, profile.restingHr, zone)

            Discipline.REST -> null
        }
    }
}
