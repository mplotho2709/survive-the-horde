package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Distance
import org.junit.Test

class TargetRealismCalculatorTest {

    @Test
    fun `no warning when either time is missing`() {
        assertThat(TargetRealismCalculator.checkRealism(Distance.OLYMPIC, 16, null, 5000)).isNull()
        assertThat(TargetRealismCalculator.checkRealism(Distance.OLYMPIC, 16, 5000, null)).isNull()
    }

    @Test
    fun `no warning when target is already achievable at current fitness`() {
        assertThat(TargetRealismCalculator.checkRealism(Distance.OLYMPIC, 16, 5000, 5500)).isNull()
        assertThat(TargetRealismCalculator.checkRealism(Distance.OLYMPIC, 16, 5000, 5000)).isNull()
    }

    @Test
    fun `always warns above 15 percent required improvement regardless of timeline`() {
        // 20% faster than current estimate, plenty of weeks (idealWeeks=16) - still a warning.
        val warning = TargetRealismCalculator.checkRealism(Distance.OLYMPIC, totalWeeks = 20, currentEstimateSec = 10_000, targetFinishTimeSec = 8_000)

        assertThat(warning).isNotNull()
        assertThat(warning).contains("20%")
    }

    @Test
    fun `warns on a moderate gap only when the timeline is also short`() {
        // 10% required improvement: warns with a short runway, not with a full ideal runway.
        val shortRunway = TargetRealismCalculator.checkRealism(Distance.OLYMPIC, totalWeeks = 10, currentEstimateSec = 10_000, targetFinishTimeSec = 9_000)
        val fullRunway = TargetRealismCalculator.checkRealism(Distance.OLYMPIC, totalWeeks = Distance.OLYMPIC.idealWeeks, currentEstimateSec = 10_000, targetFinishTimeSec = 9_000)

        assertThat(shortRunway).isNotNull()
        assertThat(fullRunway).isNull()
    }

    @Test
    fun `no warning for a small gap regardless of timeline`() {
        // 3% required improvement - well under both thresholds.
        val warning = TargetRealismCalculator.checkRealism(Distance.OLYMPIC, totalWeeks = 8, currentEstimateSec = 10_000, targetFinishTimeSec = 9_700)

        assertThat(warning).isNull()
    }
}
