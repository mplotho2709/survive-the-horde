package com.triathlonplanner.domain.zones

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.UserZoneProfile
import org.junit.Test

class RacePaceCalculatorTest {

    private val profile = UserZoneProfile(
        maxHr = 185,
        restingHr = 55,
        ftpWatts = 250,
        cssPaceSecPer100m = 95,
        thresholdRunPaceSecPerKm = 270,
    )

    @Test
    fun `bike race power falls as race distance rises`() {
        val sprint = RacePaceCalculator.bikeTarget(Distance.SPRINT, profile)!!
        val olympic = RacePaceCalculator.bikeTarget(Distance.OLYMPIC, profile)!!
        val half = RacePaceCalculator.bikeTarget(Distance.HALF_IRON, profile)!!
        val full = RacePaceCalculator.bikeTarget(Distance.FULL_IRON, profile)!!

        assertThat(sprint.upperBound).isGreaterThan(olympic.upperBound)
        assertThat(olympic.upperBound).isGreaterThan(half.upperBound)
        assertThat(half.upperBound).isGreaterThan(full.upperBound)
    }

    @Test
    fun `full iron bike power lands in the published 68-76 percent of FTP band`() {
        val full = RacePaceCalculator.bikeTarget(Distance.FULL_IRON, profile)!!

        assertThat(full.lowerBound).isEqualTo(170) // 0.68 * 250
        assertThat(full.upperBound).isEqualTo(190) // 0.76 * 250
        assertThat(full.unit).isEqualTo(RacePaceUnit.WATTS)
    }

    @Test
    fun `full iron race power is far below the generic zone 4 a lazy label would prescribe`() {
        val full = RacePaceCalculator.bikeTarget(Distance.FULL_IRON, profile)!!
        val zone4 = PowerZoneCalculator.zoneFor(profile.ftpWatts!!, com.triathlonplanner.core.model.IntensityZone(4))

        // This is the concrete cost of the old "race pace = Zone 4" shortcut.
        assertThat(full.upperBound).isLessThan(zone4.lowerBound)
    }

    @Test
    fun `targets are null when the underlying metric is missing rather than invented`() {
        val bare = UserZoneProfile(maxHr = 185)

        assertThat(RacePaceCalculator.bikeTarget(Distance.OLYMPIC, bare)).isNull()
        assertThat(RacePaceCalculator.swimTarget(Distance.OLYMPIC, bare)).isNull()
        assertThat(RacePaceCalculator.runTarget(Distance.OLYMPIC, bare)).isNull()
    }

    @Test
    fun `swim race pace is at or slower than CSS, and fades further on longer swims`() {
        val sprint = RacePaceCalculator.swimTarget(Distance.SPRINT, profile)!!
        val full = RacePaceCalculator.swimTarget(Distance.FULL_IRON, profile)!!

        assertThat(sprint.upperBound).isAtLeast(profile.cssPaceSecPer100m!!)
        assertThat(full.lowerBound).isGreaterThan(sprint.upperBound)
    }

    @Test
    fun `a stated goal time drives the run target instead of the generic threshold band`() {
        val genericTarget = RacePaceCalculator.runTarget(Distance.OLYMPIC, profile)!!
        // A deliberately fast Olympic goal: 2h15m.
        val goalTarget = RacePaceCalculator.runTarget(Distance.OLYMPIC, profile, targetFinishTimeSec = 135 * 60)!!

        assertThat(goalTarget).isNotEqualTo(genericTarget)
        assertThat(goalTarget.unit).isEqualTo(RacePaceUnit.SEC_PER_KM)
    }

    @Test
    fun `an impossible goal time yields no run pace rather than a nonsensical one`() {
        // 30 minutes for an Olympic-distance race is not physically achievable; the swim and bike
        // legs alone exceed it, leaving negative time for the run.
        val goalPace = RacePaceCalculator.goalPaceSecPerKm(Distance.OLYMPIC, profile, targetFinishTimeSec = 30 * 60)

        assertThat(goalPace).isNull()
    }

    @Test
    fun `a slower goal time implies a slower required run pace`() {
        val ambitious = RacePaceCalculator.goalPaceSecPerKm(Distance.OLYMPIC, profile, 135 * 60)!!
        val relaxed = RacePaceCalculator.goalPaceSecPerKm(Distance.OLYMPIC, profile, 180 * 60)!!

        assertThat(relaxed).isGreaterThan(ambitious)
    }
}
