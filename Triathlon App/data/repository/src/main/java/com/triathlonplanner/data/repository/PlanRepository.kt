package com.triathlonplanner.data.repository

import com.triathlonplanner.core.database.PlanWeekDao
import com.triathlonplanner.core.database.PlanWeekEntity
import com.triathlonplanner.core.database.PlannedWorkoutDao
import com.triathlonplanner.core.database.PlannedWorkoutEntity
import com.triathlonplanner.core.database.TrainingPlanDao
import com.triathlonplanner.core.database.TrainingPlanEntity
import com.triathlonplanner.core.database.WorkoutStepDao
import com.triathlonplanner.core.database.WorkoutStepEntity
import com.triathlonplanner.core.model.ActivePlanSummary
import com.triathlonplanner.core.model.PlanMutation
import com.triathlonplanner.core.model.PlanWeekSummary
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.RaceGoal
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.domain.planengine.PlanGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** Public API is domain-only (core:model types) - feature modules never see Room entities directly. */
class PlanRepository @Inject constructor(
    private val trainingPlanDao: TrainingPlanDao,
    private val planWeekDao: PlanWeekDao,
    private val plannedWorkoutDao: PlannedWorkoutDao,
    private val workoutStepDao: WorkoutStepDao,
    private val raceGoalRepository: RaceGoalRepository,
) {
    fun observeActivePlan(): Flow<ActivePlanSummary?> = trainingPlanDao.observeActive().map { it?.toDomain() }

    suspend fun getActivePlanOnce(): ActivePlanSummary? = trainingPlanDao.getActiveOnce()?.toDomain()

    fun observeWeeksForPlan(planId: Long): Flow<List<PlanWeekSummary>> =
        planWeekDao.observeForPlan(planId).map { weeks -> weeks.map { it.toDomain() } }

    fun observeWorkoutsForPlan(planId: Long): Flow<List<PlannedWorkoutSnapshot>> =
        plannedWorkoutDao.observeForPlan(planId).map { workouts -> workouts.map { it.toDomainSnapshot() } }

    fun observeWorkoutsForDate(date: LocalDate): Flow<List<PlannedWorkoutSnapshot>> =
        plannedWorkoutDao.observeForDate(date.toEpochDay()).map { workouts -> workouts.map { it.toSnapshot(weekIndex = 0) } }

    /** Generates a fresh plan via PlanGenerator and persists the full week/workout/step hierarchy. */
    suspend fun createPlanForGoal(raceGoal: RaceGoal, profile: UserZoneProfile, startDate: LocalDate): Long {
        val raceGoalId = raceGoalRepository.setActiveGoal(raceGoal)
        val generated = PlanGenerator.generate(raceGoal, profile, startDate)

        val planId = trainingPlanDao.insert(
            TrainingPlanEntity(
                raceGoalId = raceGoalId,
                startDateEpochDay = generated.startDate.toEpochDay(),
                totalWeeks = generated.totalWeeks,
                status = "ACTIVE",
                generatedAtEpochMilli = Instant.now().toEpochMilli(),
            ),
        )

        generated.weeks.forEach { week ->
            val weekId = planWeekDao.insertAll(
                listOf(
                    PlanWeekEntity(
                        planId = planId,
                        weekIndex = week.weekIndex,
                        startDateEpochDay = week.startDate.toEpochDay(),
                        phase = week.phase.name,
                        isRecoveryWeek = week.isRecoveryWeek,
                        plannedWeeklyLoad = week.plannedWeeklyLoad,
                    ),
                ),
            ).single()

            val workoutIds = plannedWorkoutDao.insertAll(
                week.workouts.map { workout ->
                    PlannedWorkoutEntity(
                        weekId = weekId,
                        dateEpochDay = workout.date.toEpochDay(),
                        discipline = workout.discipline.name,
                        workoutType = workout.workoutType.name,
                        title = workout.title,
                        plannedDurationSec = workout.plannedDurationSec,
                        plannedDistanceM = workout.plannedDistanceM,
                        plannedLoad = workout.plannedLoad,
                        status = "PLANNED",
                        zoneLevel = workout.zone?.level,
                        brickGroupId = workout.brickGroupId,
                        sortOrderInDay = workout.sortOrderInDay,
                    )
                },
            )

            week.workouts.forEachIndexed { index, workout ->
                if (workout.steps.isNotEmpty()) {
                    workoutStepDao.insertAll(
                        workout.steps.map { step ->
                            WorkoutStepEntity(
                                plannedWorkoutId = workoutIds[index],
                                stepOrder = step.stepOrder,
                                stepType = step.stepType.name,
                                durationSec = step.durationSec,
                                distanceM = step.distanceM,
                                intensityZoneLevel = step.intensityZone?.level,
                                repeatCount = step.repeatCount,
                            )
                        },
                    )
                }
            }
        }
        return planId
    }

    suspend fun getWorkoutSnapshotsForPlan(planId: Long): List<PlannedWorkoutSnapshot> =
        plannedWorkoutDao.getForPlanWithWeekIndex(planId).map { it.toDomainSnapshot() }

    /**
     * Applies one AdaptationEngine mutation. RequestReEntryBlock is simplified: rather than
     * literally inserting a new calendar week (which would require re-flowing every downstream
     * week and the race date), it steeply cuts the load of the week containing/following
     * [PlanMutation.RequestReEntryBlock.fromDate] - achieving the same safety goal (don't resume
     * at full intensity) without the bigger schedule-restructuring feature. ExtendBuildBlock is
     * currently unreachable (no AdaptationEngine rule emits it yet) so it's a documented no-op.
     */
    suspend fun applyMutation(mutation: PlanMutation) {
        when (mutation) {
            is PlanMutation.MarkMissed -> plannedWorkoutDao.updateStatus(mutation.workoutId, "MISSED")
            is PlanMutation.DropWorkout -> plannedWorkoutDao.updateStatus(mutation.workoutId, "SKIPPED_BY_ADAPTATION")
            is PlanMutation.DowngradeToRest -> plannedWorkoutDao.updateStatus(mutation.workoutId, "SKIPPED_BY_ADAPTATION")
            is PlanMutation.DowngradeToRecovery -> plannedWorkoutDao.updateWorkoutTypeAndZone(mutation.workoutId, "RECOVERY", 1)

            is PlanMutation.RescaleWeekLoad -> rescaleWeek(mutation.weekIndex, mutation.newPlannedLoad)
            is PlanMutation.SoftenCutback -> rescaleWeek(mutation.weekIndex, mutation.newPlannedLoad)

            is PlanMutation.RequestReEntryBlock -> {
                val plan = trainingPlanDao.getActiveOnce() ?: return
                val weeks = planWeekDao.observeForPlan(plan.id).first()
                val targetWeek = weeks.firstOrNull { it.startDateEpochDay >= mutation.fromDate.toEpochDay() }
                    ?: weeks.lastOrNull()
                if (targetWeek != null) {
                    planWeekDao.updatePlannedLoad(plan.id, targetWeek.weekIndex, (targetWeek.plannedWeeklyLoad * 0.5).roundToInt())
                }
            }

            is PlanMutation.ExtendBuildBlock -> Unit
        }
    }

    private suspend fun rescaleWeek(weekIndex: Int, newLoad: Int) {
        val plan = trainingPlanDao.getActiveOnce() ?: return
        planWeekDao.updatePlannedLoad(plan.id, weekIndex, newLoad)
    }
}
