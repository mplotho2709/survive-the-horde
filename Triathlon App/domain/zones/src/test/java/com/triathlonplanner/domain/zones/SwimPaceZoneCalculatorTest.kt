package com.triathlonplanner.domain.zones

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SwimPaceZoneCalculatorTest {

    @Test
    fun `zone 1 is slower than css and zone 5 is faster than css`() {
        val css = 100 // 1:40 / 100m
        val zones = SwimPaceZoneCalculator.calculateZones(cssPaceSecPer100m = css)

        assertThat(zones).hasSize(5)
        assertThat(zones[0].lowerBound).isGreaterThan(css) // zone 1 (easy) is slower than CSS
        assertThat(zones[4].upperBound).isLessThan(css) // zone 5 (sprint) is faster than CSS
    }

    @Test
    fun `css time trial formula matches critical speed definition`() {
        // 400m in 6:40 (400s), 200m in 3:00 (180s) -> (400-180)/(400-200) = 1.1 sec/m = 110 sec/100m
        val css = SwimPaceZoneCalculator.estimateCssFromTimeTrial(time400mSec = 400, time200mSec = 180)

        assertThat(css).isEqualTo(110)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `400m time not greater than 200m time throws`() {
        SwimPaceZoneCalculator.estimateCssFromTimeTrial(time400mSec = 150, time200mSec = 180)
    }
}
