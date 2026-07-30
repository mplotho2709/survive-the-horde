package com.triathlonplanner.core.model

import java.time.DayOfWeek

/**
 * Which days the athlete can train each discipline, and which days can absorb a long session.
 *
 * A long-session day is a separate axis from discipline availability on purpose: being *able* to
 * swim on a Tuesday says nothing about having three spare hours for a long ride. Keeping them
 * independent is what lets the scheduler put quality midweek and volume at the weekend, which is
 * how most age-group weeks actually work.
 *
 * An empty set means "no constraint for that discipline" rather than "never" - an athlete who
 * leaves the swim row untouched should still get swim sessions.
 */
data class DayPreferences(
    val swimDays: Set<DayOfWeek> = emptySet(),
    val bikeDays: Set<DayOfWeek> = emptySet(),
    val runDays: Set<DayOfWeek> = emptySet(),
    val longSessionDays: Set<DayOfWeek> = emptySet(),
) {
    /** Days available for [discipline]; brick legs resolve to their underlying sport. */
    fun daysFor(discipline: Discipline): Set<DayOfWeek> = when (discipline) {
        Discipline.SWIM -> swimDays
        Discipline.BIKE, Discipline.BRICK_BIKE -> bikeDays
        Discipline.RUN, Discipline.BRICK_RUN -> runDays
        // Strength and rest aren't constrained by sport-specific access (a pool, a bike) so they
        // may land on any day the athlete trains at all.
        Discipline.STRENGTH, Discipline.REST -> allTrainingDays
    }

    /** Union of every day the athlete said they can train something. */
    val allTrainingDays: Set<DayOfWeek>
        get() = swimDays + bikeDays + runDays

    /** True when nothing was specified, in which case the scheduler leaves the template alone. */
    val isEmpty: Boolean
        get() = swimDays.isEmpty() && bikeDays.isEmpty() && runDays.isEmpty() && longSessionDays.isEmpty()

    companion object {
        /** Sensible starting point: train any day, long sessions at the weekend. */
        val Default = DayPreferences(
            swimDays = DayOfWeek.entries.toSet(),
            bikeDays = DayOfWeek.entries.toSet(),
            runDays = DayOfWeek.entries.toSet(),
            longSessionDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        )
    }
}

data class TrainingAvailability(
    val weeklyHoursTarget: Double,
    val daysPerWeekTarget: Int,
    /** Null when the athlete never expressed day preferences - the template's own layout is used. */
    val dayPreferences: DayPreferences? = null,
)
