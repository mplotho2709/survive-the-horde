package com.triathlonplanner.data.repository

import com.triathlonplanner.core.database.AdaptationEventDao
import com.triathlonplanner.core.database.CompletedActivityDao
import com.triathlonplanner.core.database.PlanWeekDao
import com.triathlonplanner.core.database.PlannedWorkoutDao
import com.triathlonplanner.core.database.RollingLoadStateDao
import com.triathlonplanner.core.database.TrainingPlanDao
import com.triathlonplanner.core.model.AdaptationAction
import com.triathlonplanner.core.model.AdaptationEvent
import com.triathlonplanner.core.model.AdaptationTriggerType
import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.MatchStatus
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.RollingLoadState
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.data.healthconnect.CompletedActivitySink
import com.triathlonplanner.data.healthconnect.HealthConnectDataSource
import com.triathlonplanner.domain.planengine.AdaptationEngine
import com.triathlonplanner.domain.planengine.SessionOutcome
import com.triathlonplanner.domain.planengine.SessionRebalancer
import com.triathlonplanner.domain.planengine.LoadCalculator
import com.triathlonplanner.domain.planengine.SessionMatcher
import com.triathlonplanner.domain.planengine.WeekLoadSummary
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * The Health Connect -> plan pipeline: recompute real load from the user's profile (the HC layer
 * can't do this - it doesn't have profile access), match against that day's planned workouts,
 * persist, then run AdaptationEngine and apply whatever it proposes. This is the one place all
 * three pieces (Room, healthconnect, planengine) actually meet.
 */
class ActivitySyncRepository @Inject constructor(
    private val completedActivityDao: CompletedActivityDao,
    private val plannedWorkoutDao: PlannedWorkoutDao,
    private val trainingPlanDao: TrainingPlanDao,
    private val planWeekDao: PlanWeekDao,
    private val adaptationEventDao: AdaptationEventDao,
    private val rollingLoadStateDao: RollingLoadStateDao,
    private val profileRepository: ProfileRepository,
    private val planRepository: PlanRepository,
    private val healthConnectDataSource: HealthConnectDataSource,
) : CompletedActivitySink {

    /** Pulls recent Health Connect history directly (not the incremental Changes API) so training
     * done before a plan existed - or before Health Connect was even connected - still feeds the
     * adaptive engine's load baseline. Safe to call every time a plan is (re)created: activities
     * are upserted by their Health Connect record id, so re-covering the same window is a no-op. */
    suspend fun backfillHistory(days: Long = HISTORY_BACKFILL_DAYS) {
        if (!healthConnectDataSource.hasAllPermissions()) return
        val activities = healthConnectDataSource.getCompletedActivitiesSince(Instant.now().minus(days, ChronoUnit.DAYS))
        if (activities.isNotEmpty()) onActivitiesSynced(activities)
    }

    override suspend fun onActivitiesSynced(activities: List<CompletedActivity>) {
        if (activities.isEmpty()) return
        val plan = trainingPlanDao.getActiveOnce() ?: return
        val profile = profileRepository.getProfileOnce()

        val activitiesWithLoad = activities.map { it.copy(calculatedLoad = computeLoad(it, profile)) }

        val allSnapshotsForPlan = planRepository.getWorkoutSnapshotsForPlan(plan.id)
        val matchedActivities = activitiesWithLoad
            .groupBy { it.startTime.atZone(ZoneId.systemDefault()).toLocalDate() }
            .flatMap { (date, dayActivities) ->
                val plannedForDay = allSnapshotsForPlan.filter { it.date == date }
                SessionMatcher.matchDay(dayActivities, plannedForDay)
            }

        completedActivityDao.upsertAll(matchedActivities.map { it.toEntity() })

        matchedActivities
            .filter { it.matchStatus == MatchStatus.MATCHED }
            .mapNotNull { it.matchedPlannedWorkoutId }
            .forEach { plannedWorkoutDao.updateStatus(it, "COMPLETED") }

        val substitutionEvents = matchedActivities
            .filter { it.matchStatus == MatchStatus.SUBSTITUTED }
            .mapNotNull { activity ->
                val plannedId = activity.matchedPlannedWorkoutId ?: return@mapNotNull null
                val plannedWorkout = plannedWorkoutDao.getById(plannedId) ?: return@mapNotNull null
                plannedWorkoutDao.updateStatusWithSubstitution(plannedId, "SUBSTITUTED", activity.discipline.name)
                AdaptationEvent(
                    timestamp = Instant.now(),
                    triggerType = AdaptationTriggerType.SESSION_SUBSTITUTED,
                    actionTaken = AdaptationAction.RECORD_SUBSTITUTION,
                    description = "Did a ${activity.discipline.name.lowercase()} instead of the planned " +
                        "${plannedWorkout.discipline.lowercase()} - counted as a substitution, not a miss.",
                )
            }
        if (substitutionEvents.isNotEmpty()) {
            adaptationEventDao.insertAll(substitutionEvents.map { it.toEntity() })
        }

        runAdaptation(plan.id)
    }

    private fun computeLoad(activity: CompletedActivity, profile: UserZoneProfile?): Int {
        val normalizedPowerW = activity.normalizedPowerW
        val ftpWatts = profile?.ftpWatts
        val avgHr = activity.avgHr
        val maxHr = profile?.maxHr

        return when {
            activity.discipline == Discipline.BIKE && normalizedPowerW != null && ftpWatts != null ->
                LoadCalculator.cyclingTss(activity.durationSec, normalizedPowerW, ftpWatts)

            avgHr != null && maxHr != null ->
                LoadCalculator.hrTrimp(activity.durationSec, avgHr, maxHr, profile.restingHr)

            // No HR or power data at all (e.g. a manually-logged swim) - assume a moderate effort.
            else -> LoadCalculator.sessionRpeLoad(activity.durationSec, rpe1To10 = 5)
        }
    }

    private suspend fun runAdaptation(planId: Long) {
        val today = LocalDate.now()
        val now = Instant.now()

        val snapshots = planRepository.getWorkoutSnapshotsForPlan(planId)
        val recentActivities = completedActivityDao
            .getSinceOnce(now.minus(35, ChronoUnit.DAYS).toEpochMilli())
            .map { it.toDomain() }

        val weeks = planWeekDao.observeForPlan(planId).first()
        val recentWeeks = weeks
            .filter { it.startDateEpochDay <= today.toEpochDay() }
            .sortedBy { it.weekIndex }
            .takeLast(2)
            .map { week ->
                val weekStartMilli = LocalDate.ofEpochDay(week.startDateEpochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val weekEndMilli = Instant.ofEpochMilli(weekStartMilli).plus(7, ChronoUnit.DAYS).toEpochMilli()
                val completedLoad = completedActivityDao.sumLoadInRange(weekStartMilli, weekEndMilli)
                WeekLoadSummary(week.weekIndex, week.plannedWeeklyLoad, completedLoad)
            }

        val rollingState = rollingLoadStateDao.getForPlan(planId)?.toDomain() ?: RollingLoadState()

        val result = AdaptationEngine.evaluate(today, now, snapshots, recentActivities, recentWeeks, rollingState)

        // Local rebalancing runs alongside the structural rules above: those decide whether a
        // session should be dropped or downgraded, this retunes the volume of what remains so a
        // disrupted week still lands near its intended stress.
        val rebalance = SessionRebalancer.rebalance(
            today = today,
            now = now,
            outcomes = recentOutcomes(today, snapshots, recentActivities),
            upcoming = snapshots,
            // Cutback, taper and race weeks exist to reduce load; topping them up would defeat them.
            protectedWeekIndices = weeks
                .filter { it.isRecoveryWeek || it.phase == TrainingPhase.TAPER.name || it.phase == TrainingPhase.RACE_WEEK.name }
                .map { it.weekIndex }
                .toSet(),
        )

        (result.mutations + rebalance.mutations).forEach { planRepository.applyMutation(it) }
        val allEvents = result.events + rebalance.events
        if (allEvents.isNotEmpty()) {
            adaptationEventDao.insertAll(allEvents.map { it.toEntity() })
        }
        rollingLoadStateDao.upsert(result.updatedRollingLoadState.toEntity(planId))
    }

    /**
     * Recent sessions paired with what actually happened to them: the load recorded against each,
     * or null when nothing was. A matched activity of a different sport marks the swap, which is
     * what lets the rebalancer transfer volume between disciplines rather than just totals.
     */
    private fun recentOutcomes(
        today: LocalDate,
        snapshots: List<PlannedWorkoutSnapshot>,
        activities: List<CompletedActivity>,
    ): List<SessionOutcome> {
        val byWorkoutId = activities.filter { it.matchedPlannedWorkoutId != null }
            .groupBy { it.matchedPlannedWorkoutId }
        val windowStart = today.minusDays(SessionRebalancer.VICINITY_DAYS)

        return snapshots
            .filter { it.date in windowStart..today }
            .filter { it.discipline != Discipline.REST }
            .mapNotNull { workout ->
                val matched = byWorkoutId[workout.id].orEmpty()
                when {
                    matched.isNotEmpty() -> SessionOutcome(
                        workout = workout,
                        completedLoad = matched.sumOf { it.calculatedLoad },
                        actualDiscipline = matched.first().discipline.takeIf { it != workout.discipline },
                    )
                    // Only count a no-show once it has actually been marked missed, so today's
                    // not-yet-done session isn't treated as a shortfall.
                    workout.status == WorkoutStatus.MISSED -> SessionOutcome(workout, completedLoad = null)
                    else -> null
                }
            }
    }

    private companion object {
        const val HISTORY_BACKFILL_DAYS = 30L
    }
}
