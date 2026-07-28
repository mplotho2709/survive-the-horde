package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.domain.planengine.NormalizedPowerCalculator.PowerSample
import org.junit.Test
import java.time.Instant

class NormalizedPowerCalculatorTest {

    @Test
    fun `constant power yields normalized power equal to that power`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val samples = (0 until 120).map { PowerSample(start.plusSeconds(it.toLong()), 200) }

        val np = NormalizedPowerCalculator.calculate(samples)

        assertThat(np).isEqualTo(200)
    }

    @Test
    fun `variable power yields a normalized power higher than the simple average`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        // Alternate very hard and very easy efforts - NP should punish the variability.
        val samples = (0 until 120).map { i ->
            PowerSample(start.plusSeconds(i.toLong()), if (i % 10 < 5) 350 else 50)
        }
        val simpleAverage = samples.map { it.watts }.average()

        val np = NormalizedPowerCalculator.calculate(samples)!!

        assertThat(np.toDouble()).isGreaterThan(simpleAverage)
    }

    @Test
    fun `empty samples returns null`() {
        assertThat(NormalizedPowerCalculator.calculate(emptyList())).isNull()
    }

    @Test
    fun `short duration falls back to a plain average`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val samples = listOf(PowerSample(start, 100), PowerSample(start.plusSeconds(5), 200))

        val np = NormalizedPowerCalculator.calculate(samples)

        assertThat(np).isEqualTo(150)
    }
}
