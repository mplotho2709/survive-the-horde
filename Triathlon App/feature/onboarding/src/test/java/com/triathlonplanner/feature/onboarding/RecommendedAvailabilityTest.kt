package com.triathlonplanner.feature.onboarding

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Distance
import org.junit.Test

class RecommendedAvailabilityTest {

    @Test
    fun `zero improvement reproduces the plain recommended default exactly`() {
        val plain = recommendedFor(Distance.OLYMPIC)
        val adjusted = adjustForFitnessGap(Distance.OLYMPIC, requiredImprovementPercent = 0.0)

        assertThat(adjusted).isEqualTo(plain)
    }

    @Test
    fun `a large fitness gap bumps hours up but never above the absolute ceiling`() {
        val plain = recommendedFor(Distance.FULL_IRON)
        val adjusted = adjustForFitnessGap(Distance.FULL_IRON, requiredImprovementPercent = 1000.0)

        assertThat(adjusted.weeklyHoursTarget).isGreaterThan(plain.weeklyHoursTarget)
        assertThat(adjusted.weeklyHoursTarget).isAtMost(14.0)
    }

    @Test
    fun `days per week target is never touched by the fitness-gap adjustment`() {
        val plain = recommendedFor(Distance.SPRINT)
        val adjusted = adjustForFitnessGap(Distance.SPRINT, requiredImprovementPercent = 25.0)

        assertThat(adjusted.daysPerWeekTarget).isEqualTo(plain.daysPerWeekTarget)
    }
}
