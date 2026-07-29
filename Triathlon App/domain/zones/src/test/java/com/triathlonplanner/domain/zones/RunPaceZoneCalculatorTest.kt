package com.triathlonplanner.domain.zones

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.IntensityZone
import org.junit.Test

class RunPaceZoneCalculatorTest {

    @Test
    fun `threshold pace from a 20-minute 5K matches Daniels VDOT tables`() {
        // A 20:00 5K is VDOT ~50, whose published T-pace is 4:15/km (255 sec).
        val threshold = RunPaceZoneCalculator.thresholdPaceFromRacePb(5_000.0, 20 * 60)

        assertThat(threshold).isIn(250..260)
    }

    @Test
    fun `threshold pace from a 45-minute 10K sits just slower than 10K race pace`() {
        // 45:00 for 10K is 4:30/km. Threshold is sustainable for ~an hour, so it must be slower.
        val threshold = RunPaceZoneCalculator.thresholdPaceFromRacePb(10_000.0, 45 * 60)

        assertThat(threshold).isGreaterThan(270)
        assertThat(threshold).isLessThan(285)
    }

    @Test
    fun `a faster runner gets a faster threshold pace`() {
        val fast = RunPaceZoneCalculator.thresholdPaceFromRacePb(10_000.0, 35 * 60)
        val slow = RunPaceZoneCalculator.thresholdPaceFromRacePb(10_000.0, 55 * 60)

        assertThat(fast).isLessThan(slow)
    }

    @Test
    fun `zone 4 brackets threshold pace itself`() {
        val threshold = 270
        val zone4 = RunPaceZoneCalculator.zoneFor(threshold, IntensityZone(4))

        assertThat(zone4.lowerBound).isLessThan(threshold)
        assertThat(zone4.upperBound).isGreaterThan(threshold)
    }

    @Test
    fun `zones get faster as the level rises, and lowerBound is always the faster end`() {
        val zones = RunPaceZoneCalculator.calculateZones(270)

        zones.forEach { assertThat(it.lowerBound).isAtMost(it.upperBound) }
        // Zone 1 recovery running is slower (larger sec/km) than zone 5 intervals.
        assertThat(zones.first().lowerBound).isGreaterThan(zones.last().upperBound)
    }

    @Test
    fun `rejects non-positive inputs rather than emitting nonsense paces`() {
        runCatching { RunPaceZoneCalculator.thresholdPaceFromRacePb(0.0, 1200) }
            .also { assertThat(it.isFailure).isTrue() }
        runCatching { RunPaceZoneCalculator.calculateZones(0) }
            .also { assertThat(it.isFailure).isTrue() }
    }
}
