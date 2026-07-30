package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.GeneratedWeek
import com.triathlonplanner.core.model.GeneratedWorkout
import com.triathlonplanner.core.model.PlanMutation
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.RaceGoal
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutType
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Adaptation must not quietly undo the training principles the plan was generated under.
 *
 * The rebalancer changes session durations, and duration is what time-in-zone is measured from - so
 * without a guard it could erode the polarized distribution a little at a time, every time an
 * athlete missed a session. These tests apply real rebalancing to a real generated plan and then
 * re-run the same audit [PlanGenerator] is held to.
 */
class RebalancedPlanIntegrityTest {

    private val profile = UserZoneProfile(maxHr = 185, restingHr = 55, ftpWatts = 240, cssPaceSecPer100m = 95)
    private val startDate = LocalDate.of(2026, 1, 5)
    private val now = Instant.parse("2026-01-05T20:00:00Z")

    private fun planFor(distance: Distance) =
        PlanGenerator.generate(RaceGoal(distance, startDate.plusWeeks(distance.idealWeeks.toLong())), profile, startDate)

    private fun GeneratedWorkout.toSnapshot(weekIndex: Int, id: Long) = PlannedWorkoutSnapshot(
        id = id,
        weekIndex = weekIndex,
        date = date,
        discipline = discipline,
        workoutType = workoutType,
        plannedDurationSec = plannedDurationSec,
        plannedLoad = plannedLoad,
        status = WorkoutStatus.PLANNED,
        zone = zone,
        brickGroupId = brickGroupId,
        sortOrderInDay = sortOrderInDay,
    )

    /** Applies duration changes and rebuilds each session's steps, exactly as the repository does. */
    private fun applyToWeeks(
        weeks: List<GeneratedWeek>,
        snapshotIds: Map<Long, GeneratedWorkout>,
        mutations: List<PlanMutation.AdjustSessionLoad>,
    ): List<GeneratedWeek> {
        val newDurationByWorkout = mutations.associate { snapshotIds.getValue(it.workoutId) to it.newDurationSec }
        return weeks.map { week ->
            week.copy(
                workouts = week.workouts.map { workout ->
                    val newDuration = newDurationByWorkout[workout] ?: return@map workout
                    workout.copy(
                        plannedDurationSec = newDuration,
                        steps = WorkoutStepBuilder.build(
                            newDuration, workout.zone, workout.workoutType, workout.discipline, profile,
                        ),
                    )
                },
            )
        }
    }

    @Test
    fun `a plan still passes its intensity audit after a missed session is compensated`() {
        Distance.entries.forEach { distance ->
            val plan = planFor(distance)
            val protectedWeeks = plan.weeks
                .filter { it.isRecoveryWeek || it.phase == TrainingPhase.TAPER || it.phase == TrainingPhase.RACE_WEEK }
                .map { it.weekIndex }
                .toSet()

            var id = 1L
            val idToWorkout = mutableMapOf<Long, GeneratedWorkout>()
            val snapshots = plan.weeks.flatMap { week ->
                week.workouts.map { workout ->
                    val assigned = id++
                    idToWorkout[assigned] = workout
                    workout.toSnapshot(week.weekIndex, assigned)
                }
            }

            // Miss the single biggest session in the plan - the worst realistic case.
            val worstMiss = snapshots.maxBy { it.plannedLoad }
            val today = worstMiss.date

            val result = SessionRebalancer.rebalance(
                today = today,
                now = now,
                outcomes = listOf(SessionOutcome(worstMiss, completedLoad = null)),
                upcoming = snapshots,
                protectedWeekIndices = protectedWeeks,
            )

            val adjusted = applyToWeeks(
                plan.weeks, idToWorkout, result.mutations.filterIsInstance<PlanMutation.AdjustSessionLoad>(),
            )

            assertThat(IntensityDistributionAuditor.auditPlan(adjusted)).isEmpty()
        }
    }

    @Test
    fun `compensation never lowers the easy share of the affected week`() {
        val plan = planFor(Distance.OLYMPIC)
        var id = 1L
        val idToWorkout = mutableMapOf<Long, GeneratedWorkout>()
        val snapshots = plan.weeks.flatMap { week ->
            week.workouts.map { workout ->
                val assigned = id++
                idToWorkout[assigned] = workout
                workout.toSnapshot(week.weekIndex, assigned)
            }
        }

        val missed = snapshots.first { it.workoutType == WorkoutType.THRESHOLD }
        val before = IntensityDistributionAuditor.distributionFor(plan.weeks)

        val result = SessionRebalancer.rebalance(
            today = missed.date,
            now = now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = snapshots,
        )
        val adjusted = applyToWeeks(
            plan.weeks, idToWorkout, result.mutations.filterIsInstance<PlanMutation.AdjustSessionLoad>(),
        )
        val after = IntensityDistributionAuditor.distributionFor(adjusted)

        // Repaying a missed session in easy volume can only push the split further toward easy.
        assertThat(after.easyFraction).isAtLeast(before.easyFraction - 0.001)
    }

    @Test
    fun `only aerobic sessions are ever lengthened`() {
        val plan = planFor(Distance.HALF_IRON)
        var id = 1L
        val idToWorkout = mutableMapOf<Long, GeneratedWorkout>()
        val snapshots = plan.weeks.flatMap { week ->
            week.workouts.map { workout ->
                val assigned = id++
                idToWorkout[assigned] = workout
                workout.toSnapshot(week.weekIndex, assigned)
            }
        }

        val missed = snapshots.maxBy { it.plannedLoad }
        val result = SessionRebalancer.rebalance(
            today = missed.date,
            now = now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = snapshots,
        )

        result.mutations.filterIsInstance<PlanMutation.AdjustSessionLoad>().forEach { mutation ->
            val original = idToWorkout.getValue(mutation.workoutId)
            if (mutation.newDurationSec > original.plannedDurationSec) {
                assertThat(original.workoutType).isIn(setOf(WorkoutType.EASY, WorkoutType.LONG))
            }
        }
    }

    @Test
    fun `session types and zones are never rewritten by rebalancing`() {
        val plan = planFor(Distance.OLYMPIC)
        var id = 1L
        val idToWorkout = mutableMapOf<Long, GeneratedWorkout>()
        val snapshots = plan.weeks.flatMap { week ->
            week.workouts.map { workout ->
                val assigned = id++
                idToWorkout[assigned] = workout
                workout.toSnapshot(week.weekIndex, assigned)
            }
        }

        val missed = snapshots.maxBy { it.plannedLoad }
        val result = SessionRebalancer.rebalance(
            today = missed.date,
            now = now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = snapshots,
        )
        val adjusted = applyToWeeks(
            plan.weeks, idToWorkout, result.mutations.filterIsInstance<PlanMutation.AdjustSessionLoad>(),
        )

        val originalTypes = plan.weeks.flatMap { w -> w.workouts.map { it.workoutType to it.zone } }
        val adjustedTypes = adjusted.flatMap { w -> w.workouts.map { it.workoutType to it.zone } }
        assertThat(adjustedTypes).isEqualTo(originalTypes)
    }

    @Test
    fun `the weekly structure survives - no session is dropped, added or moved`() {
        val plan = planFor(Distance.FULL_IRON)
        var id = 1L
        val idToWorkout = mutableMapOf<Long, GeneratedWorkout>()
        val snapshots = plan.weeks.flatMap { week ->
            week.workouts.map { workout ->
                val assigned = id++
                idToWorkout[assigned] = workout
                workout.toSnapshot(week.weekIndex, assigned)
            }
        }

        val missed = snapshots.maxBy { it.plannedLoad }
        val result = SessionRebalancer.rebalance(
            today = missed.date,
            now = now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = snapshots,
        )
        val adjusted = applyToWeeks(
            plan.weeks, idToWorkout, result.mutations.filterIsInstance<PlanMutation.AdjustSessionLoad>(),
        )

        assertThat(adjusted.map { it.workouts.size }).isEqualTo(plan.weeks.map { it.workouts.size })
        assertThat(adjusted.flatMap { w -> w.workouts.map { it.date } })
            .isEqualTo(plan.weeks.flatMap { w -> w.workouts.map { it.date } })
    }

    @Test
    fun `brick pairings stay intact through rebalancing`() {
        val plan = planFor(Distance.OLYMPIC)
        var id = 1L
        val idToWorkout = mutableMapOf<Long, GeneratedWorkout>()
        val snapshots = plan.weeks.flatMap { week ->
            week.workouts.map { workout ->
                val assigned = id++
                idToWorkout[assigned] = workout
                workout.toSnapshot(week.weekIndex, assigned)
            }
        }

        val missed = snapshots.maxBy { it.plannedLoad }
        val result = SessionRebalancer.rebalance(
            today = missed.date,
            now = now,
            outcomes = listOf(SessionOutcome(missed, completedLoad = null)),
            upcoming = snapshots,
        )
        val adjusted = applyToWeeks(
            plan.weeks, idToWorkout, result.mutations.filterIsInstance<PlanMutation.AdjustSessionLoad>(),
        )

        adjusted.forEach { week ->
            week.workouts.filter { it.brickGroupId != null }
                .groupBy { it.brickGroupId }
                .forEach { (_, legs) ->
                    assertThat(legs).hasSize(2)
                    assertThat(legs.map { it.date }.distinct()).hasSize(1)
                    assertThat(legs.map { it.discipline })
                        .containsExactly(Discipline.BRICK_BIKE, Discipline.BRICK_RUN)
                }
        }
    }
}
