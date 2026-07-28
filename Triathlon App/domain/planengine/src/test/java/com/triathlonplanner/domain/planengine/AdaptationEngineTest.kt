package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.AdaptationTriggerType
import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.PlanMutation
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.RollingLoadState
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutType
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

class AdaptationEngineTest {

    private val today = LocalDate.of(2026, 3, 10)
    private val now = Instant.parse("2026-03-10T08:00:00Z")

    private fun workout(
        id: Long,
        date: LocalDate,
        discipline: Discipline = Discipline.RUN,
        workoutType: WorkoutType = WorkoutType.EASY,
        status: WorkoutStatus = WorkoutStatus.PLANNED,
        plannedLoad: Int = 50,
        weekIndex: Int = 1,
    ) = PlannedWorkoutSnapshot(id, weekIndex, date, discipline, workoutType, 1800, plannedLoad, status)

    @Test
    fun `isolated miss takes no structural action`() {
        val missedDay = today.minusDays(2)
        // A realistic week has ~7-8 sessions, so one miss stays well under the 40% fraction
        // threshold; a fixture with only 1-2 total sessions would make a single miss look like
        // 50-100% of the week and wrongly trip the consecutive-miss rule instead.
        val planned = listOf(
            workout(1, missedDay), // PLANNED -> becomes MISSED
            workout(2, missedDay.minusDays(1), status = WorkoutStatus.COMPLETED), // breaks the streak
            workout(3, today, status = WorkoutStatus.COMPLETED),
            workout(4, today.minusDays(3), status = WorkoutStatus.COMPLETED),
            workout(5, today.minusDays(4), status = WorkoutStatus.COMPLETED),
            workout(6, today.minusDays(5), status = WorkoutStatus.COMPLETED),
            workout(7, today.minusDays(6), status = WorkoutStatus.COMPLETED),
            workout(8, today.minusDays(7), status = WorkoutStatus.COMPLETED),
        )

        val result = AdaptationEngine.evaluate(
            today, now, planned, emptyList(), listOf(WeekLoadSummary(1, 300, 100)), RollingLoadState(),
        )

        assertThat(result.mutations).containsExactly(PlanMutation.MarkMissed(1))
        assertThat(result.events).isEmpty()
    }

    @Test
    fun `consecutive misses drop lowest priority session and rebaseline`() {
        val missCutoff = today.minusDays(2)
        val planned = listOf(
            workout(1, missCutoff),
            workout(2, missCutoff.minusDays(1)),
            workout(3, missCutoff.minusDays(2)),
            workout(10, today.plusDays(1), discipline = Discipline.STRENGTH, workoutType = WorkoutType.STRENGTH_SESSION),
            workout(11, today.plusDays(2), workoutType = WorkoutType.LONG),
        )

        val result = AdaptationEngine.evaluate(
            today, now, planned, emptyList(), listOf(WeekLoadSummary(1, 300, 150)), RollingLoadState(),
        )

        assertThat(result.events.single().triggerType).isEqualTo(AdaptationTriggerType.CONSECUTIVE_MISSES)
        assertThat(result.mutations).contains(PlanMutation.DropWorkout(10)) // strength dropped, not the long session
        assertThat(result.mutations.filterIsInstance<PlanMutation.DropWorkout>().map { it.workoutId }).doesNotContain(11L)
        assertThat(result.mutations.filterIsInstance<PlanMutation.RescaleWeekLoad>()).isNotEmpty()
    }

    @Test
    fun `extended absence requests a re-entry block`() {
        val missCutoff = today.minusDays(2)
        val planned = (0 until 7).map { i -> workout(i.toLong(), missCutoff.minusDays(i.toLong())) }

        val result = AdaptationEngine.evaluate(
            today, now, planned, emptyList(), listOf(WeekLoadSummary(1, 300, 0)), RollingLoadState(),
        )

        assertThat(result.events.single().triggerType).isEqualTo(AdaptationTriggerType.EXTENDED_ABSENCE)
        assertThat(result.mutations).contains(PlanMutation.RequestReEntryBlock(today))
    }

    @Test
    fun `overreach on a key session downgrades the next quality workout`() {
        val hardWorkout = workout(1, today.minusDays(1), workoutType = WorkoutType.THRESHOLD, plannedLoad = 100)
        val nextQuality = workout(2, today.plusDays(2), workoutType = WorkoutType.VO2MAX)
        val planned = listOf(hardWorkout, nextQuality)
        val activity = CompletedActivity(
            healthConnectRecordId = "a1",
            discipline = Discipline.RUN,
            startTime = now.minusSeconds(3600),
            endTime = now,
            calculatedLoad = 140, // ratio 1.4 > 1.3 threshold
            matchedPlannedWorkoutId = 1,
        )

        val result = AdaptationEngine.evaluate(
            today, now, planned, listOf(activity), listOf(WeekLoadSummary(1, 300, 300)), RollingLoadState(),
        )

        assertThat(result.events.single().triggerType).isEqualTo(AdaptationTriggerType.OVERREACH)
        assertThat(result.mutations).contains(PlanMutation.DowngradeToRecovery(2))
    }

    @Test
    fun `severe acwr additionally downgrades a lower priority session to rest`() {
        val hardWorkout = workout(1, today.minusDays(1), workoutType = WorkoutType.THRESHOLD, plannedLoad = 100)
        val nextQuality = workout(2, today.plusDays(2), workoutType = WorkoutType.VO2MAX)
        val strengthSession = workout(3, today.plusDays(1), discipline = Discipline.STRENGTH, workoutType = WorkoutType.STRENGTH_SESSION)
        val planned = listOf(hardWorkout, nextQuality, strengthSession)
        val activity = CompletedActivity(
            healthConnectRecordId = "a1",
            discipline = Discipline.RUN,
            startTime = now.minusSeconds(3600),
            endTime = now,
            calculatedLoad = 140,
            matchedPlannedWorkoutId = 1,
        )
        val severeLoadState = RollingLoadState(acuteLoad7d = 200.0, chronicLoad28d = 100.0) // ACWR = 2.0

        val result = AdaptationEngine.evaluate(
            today, now, planned, listOf(activity), listOf(WeekLoadSummary(1, 300, 300)), severeLoadState,
        )

        assertThat(result.mutations).contains(PlanMutation.DowngradeToRest(3))
    }

    @Test
    fun `sustained two-week underreach softens the next cutback`() {
        val result = AdaptationEngine.evaluate(
            today, now, emptyList(), emptyList(),
            listOf(WeekLoadSummary(1, 300, 200), WeekLoadSummary(2, 300, 200)),
            RollingLoadState(),
        )

        assertThat(result.events.single().triggerType).isEqualTo(AdaptationTriggerType.UNDERREACH)
        assertThat(result.mutations).hasSize(1)
        assertThat(result.mutations.single()).isInstanceOf(PlanMutation.SoftenCutback::class.java)
    }

    @Test
    fun `a single easy underreach session alone triggers no action`() {
        val result = AdaptationEngine.evaluate(
            today, now, emptyList(), emptyList(),
            listOf(WeekLoadSummary(1, 300, 250)), // above 75% threshold, only one week of data anyway
            RollingLoadState(),
        )

        assertThat(result.mutations).isEmpty()
        assertThat(result.events).isEmpty()
    }

    @Test
    fun `clampWeekLoad never exceeds 110 percent of the recent peak - fuzz test`() {
        val random = Random(42)
        repeat(2000) {
            val proposed = random.nextInt(0, 2000)
            val actual = random.nextInt(0, 1000)
            val planned = random.nextInt(0, 1000)

            val result = AdaptationEngine.clampWeekLoad(proposed, actual, planned)

            val ceiling = maxOf(actual, planned) * AdaptationEngine.MAX_WEEK_OVER_WEEK_GROWTH
            assertThat(result.toDouble()).isAtMost(ceiling + 1.0) // +1 tolerance for rounding
            assertThat(result).isAtMost(proposed)
        }
    }
}
