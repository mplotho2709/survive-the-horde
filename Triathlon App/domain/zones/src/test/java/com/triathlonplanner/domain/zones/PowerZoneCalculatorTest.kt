package com.triathlonplanner.domain.zones

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.roundToInt

class PowerZoneCalculatorTest {

    @Test
    fun `produces seven zones scaled from ftp`() {
        val zones = PowerZoneCalculator.calculateZones(ftpWatts = 250)

        assertThat(zones).hasSize(7)
        // Zone 4 (threshold) = 91-105% of FTP
        assertThat(zones[3].lowerBound).isEqualTo((250 * 0.91).roundToInt())
        assertThat(zones[3].upperBound).isEqualTo((250 * 1.05).roundToInt())
    }

    @Test
    fun `zones scale linearly with ftp`() {
        val zones200 = PowerZoneCalculator.calculateZones(ftpWatts = 200)
        val zones400 = PowerZoneCalculator.calculateZones(ftpWatts = 400)

        assertThat(zones400[3].lowerBound).isEqualTo(zones200[3].lowerBound * 2)
    }

    @Test
    fun `20 minute test estimate is 95 percent of average power`() {
        val ftp = PowerZoneCalculator.estimateFtpFrom20MinTest(avgPowerWatts20Min = 300)

        assertThat(ftp).isEqualTo(285)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive ftp throws`() {
        PowerZoneCalculator.calculateZones(ftpWatts = 0)
    }
}
