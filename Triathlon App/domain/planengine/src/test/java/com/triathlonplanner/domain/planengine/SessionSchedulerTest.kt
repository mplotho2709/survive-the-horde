package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.DayPreferences
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.WorkoutType
import org.junit.Test
import java.time.DayOfWeek

class SessionSchedulerTest {

    private fun spec(
        discipline: Discipline,
        type: WorkoutType,
        day: Int = 1,
        isBrickLeg: Boolean = false,
        sortOrderInDay: Int = 0,
    ) = WorkoutSpec(
        dayOfWeek = day,
        discipline = discipline,
        workoutType = type,
        durationSec = 45 * 60,
        zone = IntensityZone(2),
        isBrickLeg = isBrickLeg,
        sortOrderInDay = sortOrderInDay,
    )

    private val allDays = DayOfWeek.entries.toSet()

    @Test
    fun `no preferences leaves the template layout untouched`() {
        val specs = listOf(spec(Discipline.SWIM, WorkoutType.EASY, day = 3))

        val result = SessionScheduler.schedule(specs, preferences = null, weekIndex = 1)

        assertThat(result.specs).isEqualTo(specs)
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `empty preferences leave the template layout untouched`() {
        val specs = listOf(spec(Discipline.BIKE, WorkoutType.EASY, day = 2))

        val result = SessionScheduler.schedule(specs, DayPreferences(), weekIndex = 1)

        assertThat(result.specs).isEqualTo(specs)
    }

    @Test
    fun `every session lands on a day the athlete said they can train that discipline`() {
        val preferences = DayPreferences(
            swimDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            bikeDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY),
            runDays = setOf(DayOfWeek.THURSDAY, DayOfWeek.SUNDAY),
            longSessionDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        )
        val specs = listOf(
            spec(Discipline.SWIM, WorkoutType.EASY),
            spec(Discipline.SWIM, WorkoutType.THRESHOLD),
            spec(Discipline.BIKE, WorkoutType.EASY),
            spec(Discipline.BIKE, WorkoutType.LONG),
            spec(Discipline.RUN, WorkoutType.THRESHOLD),
            spec(Discipline.RUN, WorkoutType.LONG),
        )

        val result = SessionScheduler.schedule(specs, preferences, weekIndex = 1)

        result.specs.forEach { placed ->
            val allowed = preferences.daysFor(placed.discipline)
            assertThat(allowed).contains(DayOfWeek.of(placed.dayOfWeek))
        }
    }

    @Test
    fun `long sessions land on designated long days`() {
        val preferences = DayPreferences(
            swimDays = allDays,
            bikeDays = allDays,
            runDays = allDays,
            longSessionDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        )
        val specs = listOf(
            spec(Discipline.BIKE, WorkoutType.LONG),
            spec(Discipline.RUN, WorkoutType.LONG),
            spec(Discipline.SWIM, WorkoutType.EASY),
        )

        val result = SessionScheduler.schedule(specs, preferences, weekIndex = 3)

        result.specs.filter { it.workoutType == WorkoutType.LONG }.forEach {
            assertThat(DayOfWeek.of(it.dayOfWeek)).isIn(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        }
    }

    @Test
    fun `the long ride is never on the same day as the long run, and comes first`() {
        val preferences = DayPreferences(
            swimDays = allDays,
            bikeDays = allDays,
            runDays = allDays,
            longSessionDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        )
        val specs = listOf(
            spec(Discipline.RUN, WorkoutType.LONG),
            spec(Discipline.BIKE, WorkoutType.LONG),
        )

        val result = SessionScheduler.schedule(specs, preferences, weekIndex = 0)

        val longRide = result.specs.single { it.discipline == Discipline.BIKE }
        val longRun = result.specs.single { it.discipline == Discipline.RUN }
        assertThat(longRide.dayOfWeek).isNotEqualTo(longRun.dayOfWeek)
        assertThat(longRide.dayOfWeek).isLessThan(longRun.dayOfWeek)
    }

    @Test
    fun `brick legs stay on one day with the bike leg first`() {
        val preferences = DayPreferences(
            swimDays = allDays,
            bikeDays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
            runDays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
            longSessionDays = setOf(DayOfWeek.SATURDAY),
        )
        val specs = listOf(
            spec(Discipline.BRICK_BIKE, WorkoutType.LONG, day = 6, isBrickLeg = true, sortOrderInDay = 0),
            spec(Discipline.BRICK_RUN, WorkoutType.RACE_PACE, day = 6, isBrickLeg = true, sortOrderInDay = 1),
        )

        val result = SessionScheduler.schedule(specs, preferences, weekIndex = 5)

        val legs = result.specs.filter { it.isBrickLeg }
        assertThat(legs.map { it.dayOfWeek }.distinct()).hasSize(1)
        assertThat(legs.first().discipline).isEqualTo(Discipline.BRICK_BIKE)
        assertThat(legs.first().sortOrderInDay).isLessThan(legs.last().sortOrderInDay)
    }

    @Test
    fun `two hard sessions never share a day when spare days exist`() {
        val preferences = DayPreferences(
            swimDays = allDays,
            bikeDays = allDays,
            runDays = allDays,
            longSessionDays = setOf(DayOfWeek.SUNDAY),
        )
        val specs = listOf(
            spec(Discipline.RUN, WorkoutType.THRESHOLD),
            spec(Discipline.BIKE, WorkoutType.VO2MAX),
            spec(Discipline.SWIM, WorkoutType.THRESHOLD),
        )

        val result = SessionScheduler.schedule(specs, preferences, weekIndex = 2)

        val hardDays = result.specs.map { it.dayOfWeek }
        assertThat(hardDays).containsNoDuplicates()
    }

    @Test
    fun `a quality session is not scheduled the day after a long session`() {
        val preferences = DayPreferences(
            swimDays = allDays,
            bikeDays = allDays,
            runDays = allDays,
            longSessionDays = setOf(DayOfWeek.SATURDAY),
        )
        val specs = listOf(
            spec(Discipline.BIKE, WorkoutType.LONG),
            spec(Discipline.RUN, WorkoutType.THRESHOLD),
        )

        val result = SessionScheduler.schedule(specs, preferences, weekIndex = 0)

        val longDay = result.specs.single { it.workoutType == WorkoutType.LONG }.dayOfWeek
        val hardDay = result.specs.single { it.workoutType == WorkoutType.THRESHOLD }.dayOfWeek
        assertThat(hardDay).isNotEqualTo(DayOfWeek.of(longDay).plus(1).value)
    }

    @Test
    fun `repeat sessions of one discipline are spread rather than stacked on one day`() {
        val preferences = DayPreferences(
            swimDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            bikeDays = allDays,
            runDays = allDays,
        )
        val specs = listOf(
            spec(Discipline.SWIM, WorkoutType.EASY),
            spec(Discipline.SWIM, WorkoutType.EASY),
        )

        val result = SessionScheduler.schedule(specs, preferences, weekIndex = 1)

        assertThat(result.specs.map { it.dayOfWeek }).containsNoDuplicates()
    }

    @Test
    fun `consecutive weeks differ when the athlete has spare days to rotate through`() {
        val preferences = DayPreferences(
            swimDays = allDays,
            bikeDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            runDays = allDays,
        )
        val specs = listOf(spec(Discipline.BIKE, WorkoutType.EASY))

        val layouts = (1..6).map { week ->
            SessionScheduler.schedule(specs, preferences, weekIndex = week).specs.single().dayOfWeek
        }

        // The point of the feature: not every week identical.
        assertThat(layouts.distinct().size).isAtLeast(2)
    }

    @Test
    fun `scheduling is deterministic - the same week always produces the same layout`() {
        val preferences = DayPreferences(
            swimDays = allDays,
            bikeDays = allDays,
            runDays = allDays,
            longSessionDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        )
        val specs = listOf(
            spec(Discipline.SWIM, WorkoutType.EASY),
            spec(Discipline.BIKE, WorkoutType.LONG),
            spec(Discipline.RUN, WorkoutType.THRESHOLD),
        )

        val first = SessionScheduler.schedule(specs, preferences, weekIndex = 7).specs
        val second = SessionScheduler.schedule(specs, preferences, weekIndex = 7).specs

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `a brick warns when bike and run days share no day, rather than silently splitting`() {
        val preferences = DayPreferences(
            swimDays = allDays,
            bikeDays = setOf(DayOfWeek.TUESDAY),
            runDays = setOf(DayOfWeek.THURSDAY),
            longSessionDays = setOf(DayOfWeek.SATURDAY),
        )
        val specs = listOf(
            spec(Discipline.BRICK_BIKE, WorkoutType.LONG, day = 6, isBrickLeg = true, sortOrderInDay = 0),
            spec(Discipline.BRICK_RUN, WorkoutType.RACE_PACE, day = 6, isBrickLeg = true, sortOrderInDay = 1),
        )

        val result = SessionScheduler.schedule(specs, preferences, weekIndex = 1)

        assertThat(result.warnings).isNotEmpty()
        // Still kept together - a split brick is not a brick.
        assertThat(result.specs.map { it.dayOfWeek }.distinct()).hasSize(1)
    }

    @Test
    fun `the day budget stops the plan opening more training days than requested`() {
        val preferences = DayPreferences(swimDays = allDays, bikeDays = allDays, runDays = allDays)
        val specs = List(6) { spec(Discipline.RUN, WorkoutType.EASY) }

        val result = SessionScheduler.schedule(specs, preferences, weekIndex = 1, maxTrainingDays = 3)

        assertThat(result.specs.map { it.dayOfWeek }.distinct().size).isAtMost(3)
    }

    @Test
    fun `end to end - a real plan honours preferences and still varies week to week`() {
        // Bike and run share Saturday, so the Build phase's brick is actually placeable. Without an
        // overlap a brick is impossible by construction - that case is covered separately below.
        val preferences = DayPreferences(
            swimDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            bikeDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
            runDays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            longSessionDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        )
        val availability = com.triathlonplanner.core.model.TrainingAvailability(
            weeklyHoursTarget = 9.0,
            daysPerWeekTarget = 6,
            dayPreferences = preferences,
        )

        val results = (1..8).map { week ->
            SessionScheduler.schedule(
                WeeklyTemplate.sessionsFor(Distance.OLYMPIC, TrainingPhase.BUILD, availability),
                preferences,
                weekIndex = week,
                maxTrainingDays = 6,
            )
        }

        results.forEach { result ->
            assertThat(result.warnings).isEmpty()
            result.specs.forEach { placed ->
                if (placed.discipline != Discipline.STRENGTH && placed.discipline != Discipline.REST) {
                    assertThat(preferences.daysFor(placed.discipline)).contains(DayOfWeek.of(placed.dayOfWeek))
                }
            }
        }

        val layouts = results.map { result -> result.specs.map { "${it.dayOfWeek}:${it.discipline}" } }
        assertThat(layouts.distinct().size).isAtLeast(2)
    }

    @Test
    fun `disjoint bike and run days keep the brick together and say so`() {
        val preferences = DayPreferences(
            swimDays = setOf(DayOfWeek.MONDAY),
            bikeDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            runDays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY),
            longSessionDays = setOf(DayOfWeek.SUNDAY),
        )
        val availability = com.triathlonplanner.core.model.TrainingAvailability(
            weeklyHoursTarget = 8.0,
            daysPerWeekTarget = 6,
            dayPreferences = preferences,
        )

        val result = SessionScheduler.schedule(
            WeeklyTemplate.sessionsFor(Distance.OLYMPIC, TrainingPhase.BUILD, availability),
            preferences,
            weekIndex = 1,
            maxTrainingDays = 6,
        )

        assertThat(result.warnings).isNotEmpty()
        val brickDays = result.specs.filter { it.isBrickLeg }.map { it.dayOfWeek }.distinct()
        assertThat(brickDays).hasSize(1)
    }
}
