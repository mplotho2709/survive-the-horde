package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoadCalculatorTest {

    @Test
    fun `one hour at exactly ftp yields tss of 100`() {
        val tss = LoadCalculator.cyclingTss(durationSec = 3600, normalizedPowerW = 200, ftpWatts = 200)

        assertThat(tss).isEqualTo(100)
    }

    @Test
    fun `half hour at ftp yields roughly half the tss`() {
        val tss = LoadCalculator.cyclingTss(durationSec = 1800, normalizedPowerW = 200, ftpWatts = 200)

        assertThat(tss).isEqualTo(50)
    }

    @Test
    fun `higher intensity produces disproportionately more tss than duration alone`() {
        val moderate = LoadCalculator.cyclingTss(durationSec = 3600, normalizedPowerW = 150, ftpWatts = 200)
        val hard = LoadCalculator.cyclingTss(durationSec = 3600, normalizedPowerW = 220, ftpWatts = 200)

        // IF-squared-ish scaling means the ratio of load exceeds the ratio of power.
        assertThat(hard.toDouble() / moderate).isGreaterThan(220.0 / 150.0)
    }

    @Test
    fun `hr trimp increases with higher relative intensity`() {
        val easy = LoadCalculator.hrTrimp(durationSec = 3600, avgHr = 130, maxHr = 190, restingHr = 50)
        val hard = LoadCalculator.hrTrimp(durationSec = 3600, avgHr = 170, maxHr = 190, restingHr = 50)

        assertThat(hard).isGreaterThan(easy)
    }

    @Test
    fun `hr trimp falls back gracefully without resting hr`() {
        val load = LoadCalculator.hrTrimp(durationSec = 3600, avgHr = 150, maxHr = 190, restingHr = null)

        assertThat(load).isGreaterThan(0)
    }

    @Test
    fun `session rpe load scales with duration and rpe`() {
        val short = LoadCalculator.sessionRpeLoad(durationSec = 1800, rpe1To10 = 5)
        val long = LoadCalculator.sessionRpeLoad(durationSec = 3600, rpe1To10 = 5)
        val harder = LoadCalculator.sessionRpeLoad(durationSec = 1800, rpe1To10 = 8)

        assertThat(long).isGreaterThan(short)
        assertThat(harder).isGreaterThan(short)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rpe out of range throws`() {
        LoadCalculator.sessionRpeLoad(durationSec = 1800, rpe1To10 = 11)
    }
}
