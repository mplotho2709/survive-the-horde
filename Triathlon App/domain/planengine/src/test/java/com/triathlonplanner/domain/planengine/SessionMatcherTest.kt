package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.MatchStatus
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutType
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SessionMatcherTest {

    private val today = LocalDate.of(2026, 3, 2)

    private fun planned(
        id: Long,
        discipline: Discipline,
        sortOrder: Int = 0,
        status: WorkoutStatus = WorkoutStatus.PLANNED,
    ) = PlannedWorkoutSnapshot(
        id = id,
        weekIndex = 1,
        date = today,
        discipline = discipline,
        workoutType = WorkoutType.EASY,
        plannedDurationSec = 1800,
        plannedLoad = 50,
        status = status,
        sortOrderInDay = sortOrder,
    )

    private fun activity(discipline: Discipline, startEpochSec: Long) = CompletedActivity(
        healthConnectRecordId = "rec-$startEpochSec",
        discipline = discipline,
        startTime = Instant.ofEpochSecond(startEpochSec),
        endTime = Instant.ofEpochSecond(startEpochSec + 1800),
        calculatedLoad = 50,
    )

    @Test
    fun `matches a simple single-session day`() {
        val plan = listOf(planned(1, Discipline.RUN))
        val activities = listOf(activity(Discipline.RUN, 1000))

        val result = SessionMatcher.matchDay(activities, plan)

        assertThat(result.single().matchStatus).isEqualTo(MatchStatus.MATCHED)
        assertThat(result.single().matchedPlannedWorkoutId).isEqualTo(1L)
    }

    @Test
    fun `brick bike leg matches a plain bike activity`() {
        val plan = listOf(planned(1, Discipline.BRICK_BIKE, sortOrder = 0), planned(2, Discipline.BRICK_RUN, sortOrder = 1))
        val activities = listOf(activity(Discipline.BIKE, 1000), activity(Discipline.RUN, 5000))

        val result = SessionMatcher.matchDay(activities, plan)

        assertThat(result).hasSize(2)
        assertThat(result.all { it.matchStatus == MatchStatus.MATCHED }).isTrue()
        val bikeMatch = result.first { it.discipline == Discipline.BIKE }
        val runMatch = result.first { it.discipline == Discipline.RUN }
        assertThat(bikeMatch.matchedPlannedWorkoutId).isEqualTo(1L)
        assertThat(runMatch.matchedPlannedWorkoutId).isEqualTo(2L)
    }

    @Test
    fun `multiple same-discipline activities pair in chronological order with sortOrder`() {
        val plan = listOf(planned(1, Discipline.BIKE, sortOrder = 0), planned(2, Discipline.BIKE, sortOrder = 1))
        val activities = listOf(activity(Discipline.BIKE, 5000), activity(Discipline.BIKE, 1000))

        val result = SessionMatcher.matchDay(activities, plan)

        val earlier = result.first { it.startTime.epochSecond == 1000L }
        val later = result.first { it.startTime.epochSecond == 5000L }
        assertThat(earlier.matchedPlannedWorkoutId).isEqualTo(1L)
        assertThat(later.matchedPlannedWorkoutId).isEqualTo(2L)
    }

    @Test
    fun `extra unplanned activity is marked unmatched but still returned`() {
        val activities = listOf(activity(Discipline.SWIM, 1000))

        val result = SessionMatcher.matchDay(activities, emptyList())

        assertThat(result.single().matchStatus).isEqualTo(MatchStatus.UNMATCHED_EXTRA)
        assertThat(result.single().matchedPlannedWorkoutId).isNull()
    }

    @Test
    fun `already-completed planned workouts are not re-matched`() {
        val plan = listOf(planned(1, Discipline.RUN, status = WorkoutStatus.COMPLETED))
        val activities = listOf(activity(Discipline.RUN, 1000))

        val result = SessionMatcher.matchDay(activities, plan)

        assertThat(result.single().matchStatus).isEqualTo(MatchStatus.UNMATCHED_EXTRA)
    }

    @Test
    fun `a run instead of the planned bike is recognized as a substitution, not left unmatched`() {
        val plan = listOf(planned(1, Discipline.BIKE))
        val activities = listOf(activity(Discipline.RUN, 1000))

        val result = SessionMatcher.matchDay(activities, plan)

        assertThat(result.single().matchStatus).isEqualTo(MatchStatus.SUBSTITUTED)
        assertThat(result.single().matchedPlannedWorkoutId).isEqualTo(1L)
    }

    @Test
    fun `an extra activity with no other-discipline planned session left falls back to unmatched extra`() {
        val plan = listOf(planned(1, Discipline.RUN))
        val activities = listOf(activity(Discipline.RUN, 1000), activity(Discipline.SWIM, 5000))

        val result = SessionMatcher.matchDay(activities, plan)

        val runActivity = result.first { it.discipline == Discipline.RUN }
        val swimActivity = result.first { it.discipline == Discipline.SWIM }
        assertThat(runActivity.matchStatus).isEqualTo(MatchStatus.MATCHED)
        assertThat(swimActivity.matchStatus).isEqualTo(MatchStatus.UNMATCHED_EXTRA)
        assertThat(swimActivity.matchedPlannedWorkoutId).isNull()
    }
}
