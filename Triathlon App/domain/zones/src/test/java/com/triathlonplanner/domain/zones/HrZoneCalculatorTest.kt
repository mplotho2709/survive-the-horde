package com.triathlonplanner.domain.zones

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.IntensityZone
import org.junit.Test

class HrZoneCalculatorTest {

    @Test
    fun `karvonen zones used when resting hr is known`() {
        val zones = HrZoneCalculator.calculateZones(maxHr = 190, restingHr = 50)

        assertThat(zones).hasSize(5)
        // HRR = 140. Zone 1 = 50-60%: 50+0.50*140=120 .. 50+0.60*140=134
        assertThat(zones[0].lowerBound).isEqualTo(120)
        assertThat(zones[0].upperBound).isEqualTo(134)
        // Zone 5 = 90-100%: 50+0.90*140=176 .. 50+1.00*140=190
        assertThat(zones[4].lowerBound).isEqualTo(176)
        assertThat(zones[4].upperBound).isEqualTo(190)
    }

    @Test
    fun `percent max hr fallback used when resting hr is unknown`() {
        val zones = HrZoneCalculator.calculateZones(maxHr = 200, restingHr = null)

        assertThat(zones).hasSize(5)
        // Zone 1 upper bound = 60% of 200 = 120
        assertThat(zones[0].upperBound).isEqualTo(120)
        // Zone 5 lower bound = 90% of 200 = 180
        assertThat(zones[4].lowerBound).isEqualTo(180)
    }

    @Test
    fun `no fallback zone ever exceeds the person's own max hr`() {
        val maxHr = 185
        val zones = HrZoneCalculator.calculateZones(maxHr = maxHr, restingHr = null)

        zones.forEach { zone ->
            assertThat(zone.upperBound).isAtMost(maxHr)
        }
    }

    @Test
    fun `zones are monotonically increasing`() {
        val zones = HrZoneCalculator.calculateZones(maxHr = 185, restingHr = 55)

        for (i in 0 until zones.size - 1) {
            assertThat(zones[i].upperBound).isAtMost(zones[i + 1].lowerBound + 1)
            assertThat(zones[i].lowerBound).isLessThan(zones[i + 1].lowerBound)
        }
    }

    @Test
    fun `zoneFor returns the requested zone`() {
        val zone3 = HrZoneCalculator.zoneFor(maxHr = 190, restingHr = 50, zone = IntensityZone(3))

        assertThat(zone3.zone.level).isEqualTo(3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive max hr throws`() {
        HrZoneCalculator.calculateZones(maxHr = 0, restingHr = null)
    }

    @Test
    fun `resting hr greater than or equal to max hr falls back to percent max`() {
        val zones = HrZoneCalculator.calculateZones(maxHr = 190, restingHr = 190)

        // Falls back to %MaxHR zones rather than dividing by zero HRR.
        assertThat(zones[0].upperBound).isEqualTo((190 * 0.60).toInt())
    }
}
