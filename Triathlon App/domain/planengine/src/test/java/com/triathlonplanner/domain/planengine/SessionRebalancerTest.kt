package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.PlanMutation
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutType
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SessionRebalancerTest {

    private val today = LocalDate.of(2026, 3, 10)
    private val now = Instant.parse("2026-03-10T20:00:00Z")

    private var nextId = 1L

    private fun workout(
        discipline: Discipline = Discipline.RUN,
        type: WorkoutType = WorkoutType.EASY,
        daysFromToday: Long = 1,
        plannedLoad: Int = 100,
        durationSec: Int = 60 * 60,
        weekIndex: Int = 1,
        status: WorkoutStatus = WorkoutStatus.PLANNED,
    ) = PlannedWorkoutSnapshot(
        id = nextId++,
        weekIndex = weekIndex,
        date = today.plusDays(daysFromToday),
        discipline = discipline,
        workoutType = type,
        plannedDurationSec = durationSec,
        plannedLoad = plannedLoad,
        status = status,
        zone = IntensityZone(2),
    )

    private fun adjustments(result: RebalanceResult) =
        result.mutations.filterIsInstance<PlanMutation.AdjustSessionLoad>()

    @Test
    fun `a missed session adds volume to nearby sessions`() {
        val missed = workout(daysFromToday = -1, plannedLoad = 120)
        val upcoming = listOf(workout(daysFromToday = 2), workout(daysFromToday = 4))

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = upcoming,
        )

        val adjusted = adjustments(result)
        assertThat(adjusted).isNotEmpty()
        adjusted.forEach { assertThat(it.newPlannedLoad).isGreaterThan(100) }
        assertThat(result.events).isNotEmpty()
    }

    @Test
    fun `only part of a shortfall is chased - missed training is not fully banked`() {
        val missed = workout(daysFromToday = -1, plannedLoad = 200)
        val upcoming = List(4) { workout(daysFromToday = it + 1L) }

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = upcoming,
        )

        val addedLoad = adjustments(result).sumOf { it.newPlannedLoad - 100 }
        assertThat(addedLoad).isAtMost((200 * SessionRebalancer.DEFICIT_RECOVERY_FRACTION).toInt())
        assertThat(addedLoad).isGreaterThan(0)
    }

    @Test
    fun `over-effort trims upcoming sessions`() {
        val overdone = workout(daysFromToday = -1, plannedLoad = 100)
        val upcoming = listOf(workout(daysFromToday = 1), workout(daysFromToday = 3))

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(SessionOutcome(overdone, completedLoad = 180)),
            upcoming = upcoming,
        )

        val adjusted = adjustments(result)
        assertThat(adjusted).isNotEmpty()
        adjusted.forEach { assertThat(it.newPlannedLoad).isLessThan(100) }
    }

    @Test
    fun `a session done close to plan changes nothing`() {
        val onPlan = workout(daysFromToday = -1, plannedLoad = 100)
        val upcoming = listOf(workout(daysFromToday = 2))

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(SessionOutcome(onPlan, completedLoad = 95)),
            upcoming = upcoming,
        )

        assertThat(result.mutations).isEmpty()
        assertThat(result.events).isEmpty()
    }

    @Test
    fun `recovery sessions are never touched in either direction`() {
        val missed = workout(daysFromToday = -1, plannedLoad = 200)
        val recovery = workout(type = WorkoutType.RECOVERY, daysFromToday = 2)

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = listOf(recovery),
        )

        assertThat(result.mutations).isEmpty()
    }

    @Test
    fun `protected weeks - cutback, taper, race week - are never topped up`() {
        val missed = workout(daysFromToday = -1, plannedLoad = 200)
        val inCutbackWeek = workout(daysFromToday = 2, weekIndex = 4)

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = listOf(inCutbackWeek),
            protectedWeekIndices = setOf(4),
        )

        assertThat(result.mutations).isEmpty()
    }

    @Test
    fun `no single session is stretched beyond its cap`() {
        val missed = workout(daysFromToday = -1, plannedLoad = 1000)
        val lone = workout(daysFromToday = 1, plannedLoad = 100, durationSec = 60 * 60)

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = listOf(lone),
        )

        val adjusted = adjustments(result).single()
        val maxLoad = 100 + (100 * SessionRebalancer.MAX_SESSION_ADJUST_FRACTION)
        assertThat(adjusted.newPlannedLoad.toDouble()).isAtMost(maxLoad)
        // Duration is bounded by the same cap, so nothing becomes an unrecognisable session.
        assertThat(adjusted.newDurationSec).isAtMost((60 * 60 * 1.21).toInt())
    }

    @Test
    fun `intensity is never changed - only duration and load move`() {
        val missed = workout(daysFromToday = -1, plannedLoad = 150)
        val upcoming = listOf(workout(daysFromToday = 2), workout(daysFromToday = 3))

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = upcoming,
        )

        // AdjustSessionLoad carries no zone or workout type by construction; assert the rebalancer
        // emits nothing that could change how hard a session is.
        assertThat(result.mutations.all { it is PlanMutation.AdjustSessionLoad }).isTrue()
    }

    @Test
    fun `sessions beyond the vicinity window are left alone`() {
        val missed = workout(daysFromToday = -1, plannedLoad = 200)
        val faraway = workout(daysFromToday = SessionRebalancer.VICINITY_DAYS + 5)

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = listOf(faraway),
        )

        assertThat(result.mutations).isEmpty()
    }

    @Test
    fun `the wrong sport transfers volume from the sport done to the sport missed`() {
        val plannedSwim = workout(discipline = Discipline.SWIM, daysFromToday = -1, plannedLoad = 100)
        val nextSwim = workout(discipline = Discipline.SWIM, daysFromToday = 2)
        val nextRun = workout(discipline = Discipline.RUN, daysFromToday = 3)

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(
                SessionOutcome(plannedSwim, completedLoad = 100, actualDiscipline = Discipline.RUN),
            ),
            upcoming = listOf(nextSwim, nextRun),
        )

        val byId = adjustments(result).associateBy { it.workoutId }
        assertThat(byId[nextSwim.id]!!.newPlannedLoad).isGreaterThan(100)
        assertThat(byId[nextRun.id]!!.newPlannedLoad).isLessThan(100)
        assertThat(result.events.map { it.triggerType })
            .contains(com.triathlonplanner.core.model.AdaptationTriggerType.SESSION_SUBSTITUTED)
    }

    @Test
    fun `already-completed upcoming rows are not retuned`() {
        val missed = workout(daysFromToday = -1, plannedLoad = 200)
        val done = workout(daysFromToday = 2, status = WorkoutStatus.COMPLETED)

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = listOf(done),
        )

        assertThat(result.mutations).isEmpty()
    }

    @Test
    fun `each session receives at most one adjustment`() {
        val missedSwim = workout(discipline = Discipline.SWIM, daysFromToday = -1, plannedLoad = 120)
        val upcoming = listOf(
            workout(discipline = Discipline.SWIM, daysFromToday = 2),
            workout(discipline = Discipline.RUN, daysFromToday = 3),
        )

        val result = SessionRebalancer.rebalance(
            today, now,
            outcomes = listOf(
                SessionOutcome(missedSwim, completedLoad = 40, actualDiscipline = Discipline.RUN),
            ),
            upcoming = upcoming,
        )

        val ids = adjustments(result).map { it.workoutId }
        assertThat(ids).containsNoDuplicates()
    }
}
