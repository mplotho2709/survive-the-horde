package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.TrainingAvailability
import com.triathlonplanner.core.model.TrainingPhase
import org.junit.Test

class WeeklyTemplateTest {

    @Test
    fun `null availability reproduces the default output exactly`() {
        val withoutAvailability = WeeklyTemplate.sessionsFor(Distance.OLYMPIC, TrainingPhase.PEAK)
        val withExplicitNull = WeeklyTemplate.sessionsFor(Distance.OLYMPIC, TrainingPhase.PEAK, availability = null)

        assertThat(withExplicitNull).isEqualTo(withoutAvailability)
    }

    @Test
    fun `higher weekly hours target scales long sessions up`() {
        val low = WeeklyTemplate.sessionsFor(
            Distance.OLYMPIC,
            TrainingPhase.PEAK,
            TrainingAvailability(weeklyHoursTarget = 4.0, daysPerWeekTarget = 5),
        )
        val high = WeeklyTemplate.sessionsFor(
            Distance.OLYMPIC,
            TrainingPhase.PEAK,
            TrainingAvailability(weeklyHoursTarget = 8.0, daysPerWeekTarget = 5),
        )

        val lowLongBike = low.first { it.workoutType.name == "LONG" && it.discipline.name == "BIKE" }
        val highLongBike = high.first { it.workoutType.name == "LONG" && it.discipline.name == "BIKE" }

        assertThat(highLongBike.durationSec).isGreaterThan(lowLongBike.durationSec)
    }

    @Test
    fun `hours scale is coerced within bounds so extreme inputs don't blow up durations`() {
        // Both targets are far past the 1.6x cap with enough margin to avoid floating-point
        // boundary flakiness - both should coerce to the identical capped scale factor.
        val extremeLow = WeeklyTemplate.sessionsFor(
            Distance.SPRINT,
            TrainingPhase.PEAK,
            TrainingAvailability(weeklyHoursTarget = 50.0, daysPerWeekTarget = 5),
        )
        val extremeHigh = WeeklyTemplate.sessionsFor(
            Distance.SPRINT,
            TrainingPhase.PEAK,
            TrainingAvailability(weeklyHoursTarget = 1000.0, daysPerWeekTarget = 5),
        )

        assertThat(extremeHigh).isEqualTo(extremeLow)
    }

    @Test
    fun `days per week cap drops whole days not individual sessions`() {
        val uncapped = WeeklyTemplate.sessionsFor(Distance.OLYMPIC, TrainingPhase.BASE)
        val distinctDaysUncapped = uncapped.map { it.dayOfWeek }.distinct().size

        // weeklyHoursTarget matches OLYMPIC's own baseline so hoursScale == 1.0, isolating the
        // day-cap behavior from the hours-scaling behavior tested elsewhere in this file.
        val capped = WeeklyTemplate.sessionsFor(
            Distance.OLYMPIC,
            TrainingPhase.BASE,
            TrainingAvailability(weeklyHoursTarget = 5.2, daysPerWeekTarget = distinctDaysUncapped - 2),
        )

        val distinctDaysCapped = capped.map { it.dayOfWeek }.distinct().size
        assertThat(distinctDaysCapped).isEqualTo(distinctDaysUncapped - 2)
        // every remaining day still has all of its original sessions - no partial-day cuts
        capped.groupBy { it.dayOfWeek }.forEach { (day, sessions) ->
            assertThat(sessions).isEqualTo(uncapped.filter { it.dayOfWeek == day })
        }
    }

    @Test
    fun `days per week cap never drops the day containing the long bike or brick`() {
        val capped = WeeklyTemplate.sessionsFor(
            Distance.OLYMPIC,
            TrainingPhase.BUILD,
            TrainingAvailability(weeklyHoursTarget = 7.0, daysPerWeekTarget = 2),
        )

        val hasProtectedSession = capped.any { it.isBrickLeg || it.workoutType.name == "LONG" }
        assertThat(hasProtectedSession).isTrue()
    }
}
