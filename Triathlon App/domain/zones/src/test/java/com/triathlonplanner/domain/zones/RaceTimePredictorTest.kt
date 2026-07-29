package com.triathlonplanner.domain.zones

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Distance
import org.junit.Test

class RaceTimePredictorTest {

    @Test
    fun `riegel formula matches the known T2 = T1 times (D2 over D1) to the 1_06 formula`() {
        // 10K in 50:00 (3000s) -> predicted marathon time, hand-computed via T2 = T1*(D2/D1)^1.06.
        val predicted = RaceTimePredictor.riegelPredictedTimeSec(knownDistanceM = 10_000.0, knownTimeSec = 3000, targetDistanceM = 42_195.0)

        assertThat(predicted).isWithin(5).of(13798)
    }

    @Test
    fun `riegel formula is identity when target distance equals known distance`() {
        val predicted = RaceTimePredictor.riegelPredictedTimeSec(knownDistanceM = 10_000.0, knownTimeSec = 2500, targetDistanceM = 10_000.0)

        assertThat(predicted).isEqualTo(2500)
    }

    @Test
    fun `swim leg fade increases with race distance`() {
        val css = 100 // 1:40/100m
        val sprint = RaceTimePredictor.estimateSwimLegTimeSec(css, Distance.SPRINT.swimMeters, Distance.SPRINT)
        val olympic = RaceTimePredictor.estimateSwimLegTimeSec(css, Distance.OLYMPIC.swimMeters, Distance.OLYMPIC)
        val half = RaceTimePredictor.estimateSwimLegTimeSec(css, Distance.HALF_IRON.swimMeters, Distance.HALF_IRON)
        val full = RaceTimePredictor.estimateSwimLegTimeSec(css, Distance.FULL_IRON.swimMeters, Distance.FULL_IRON)

        // Hand-computed: no fade for Sprint (100 * 7.5 = 750), then increasing fade fractions.
        assertThat(sprint).isEqualTo(750)
        assertThat(olympic).isEqualTo(1530) // 102 * 15
        assertThat(half).isEqualTo(1995) // 105 * 19
        assertThat(full).isEqualTo(4104) // 108 * 38
    }

    @Test
    fun `estimateCurrentFinishTimeSec sums swim and run legs only`() {
        val estimate = RaceTimePredictor.estimateCurrentFinishTimeSec(
            distance = Distance.OLYMPIC,
            cssPaceSecPer100m = 100,
            runPbDistanceM = 5_000.0,
            runPbTimeSec = 1200, // 20:00 5K
        )

        val expectedSwim = 1530 // matches the fade test above
        val expectedRun = RaceTimePredictor.riegelPredictedTimeSec(5_000.0, 1200, Distance.OLYMPIC.runMeters.toDouble())
        assertThat(estimate).isEqualTo(expectedSwim + expectedRun)
    }

    @Test
    fun `required improvement is positive when target is faster than current fitness`() {
        assertThat(RaceTimePredictor.requiredImprovementPercent(currentEstimateSec = 10_000, targetFinishTimeSec = 9_000)).isGreaterThan(0.0)
    }

    @Test
    fun `required improvement is zero or negative when target is already achievable`() {
        assertThat(RaceTimePredictor.requiredImprovementPercent(currentEstimateSec = 10_000, targetFinishTimeSec = 10_000)).isEqualTo(0.0)
        assertThat(RaceTimePredictor.requiredImprovementPercent(currentEstimateSec = 10_000, targetFinishTimeSec = 11_000)).isLessThan(0.0)
    }

    @Test
    fun `hours bump fraction floors at zero and caps at 0_35 regardless of how large the gap is`() {
        assertThat(RaceTimePredictor.recommendedHoursBumpFraction(0.0)).isEqualTo(0.0)
        assertThat(RaceTimePredictor.recommendedHoursBumpFraction(-10.0)).isEqualTo(0.0)
        assertThat(RaceTimePredictor.recommendedHoursBumpFraction(10.0)).isWithin(0.0001).of(0.15)
        assertThat(RaceTimePredictor.recommendedHoursBumpFraction(1000.0)).isEqualTo(0.35)
    }
}
