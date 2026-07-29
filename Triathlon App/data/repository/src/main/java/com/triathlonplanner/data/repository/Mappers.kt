package com.triathlonplanner.data.repository

import com.triathlonplanner.core.database.AdaptationEventEntity
import com.triathlonplanner.core.database.CompletedActivityEntity
import com.triathlonplanner.core.database.PlanWeekEntity
import com.triathlonplanner.core.database.PlannedWorkoutEntity
import com.triathlonplanner.core.database.PlannedWorkoutWithWeekIndex
import com.triathlonplanner.core.database.RaceGoalEntity
import com.triathlonplanner.core.database.RollingLoadStateEntity
import com.triathlonplanner.core.database.TrainingPlanEntity
import com.triathlonplanner.core.database.UserProfileEntity
import com.triathlonplanner.core.database.WorkoutStepEntity
import com.triathlonplanner.core.model.ActivePlanSummary
import com.triathlonplanner.core.model.AdaptationEvent
import com.triathlonplanner.core.model.AdaptationAction
import com.triathlonplanner.core.model.AdaptationTriggerType
import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.CssSource
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.FtpSource
import com.triathlonplanner.core.model.GeneratedWorkoutStep
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.MatchStatus
import com.triathlonplanner.core.model.PlanStatus
import com.triathlonplanner.core.model.PlanWeekSummary
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.RaceGoal
import com.triathlonplanner.core.model.RollingLoadState
import com.triathlonplanner.core.model.TrainingAvailability
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutStepType
import com.triathlonplanner.core.model.WorkoutType
import java.time.Instant
import java.time.LocalDate

fun UserProfileEntity.toDomain(): UserZoneProfile = UserZoneProfile(
    maxHr = maxHr,
    restingHr = restingHr,
    ftpWatts = ftpWatts,
    ftpSource = ftpSource?.let { FtpSource.valueOf(it) },
    cssPaceSecPer100m = cssPaceSecPer100m,
    cssSource = cssSource?.let { CssSource.valueOf(it) },
    weightKg = weightKg,
)

fun UserZoneProfile.toEntity(updatedAt: Instant): UserProfileEntity = UserProfileEntity(
    maxHr = maxHr,
    restingHr = restingHr,
    ftpWatts = ftpWatts,
    ftpSource = ftpSource?.name,
    cssPaceSecPer100m = cssPaceSecPer100m,
    cssSource = cssSource?.name,
    weightKg = weightKg,
    updatedAtEpochMilli = updatedAt.toEpochMilli(),
)

fun RaceGoalEntity.toDomain(): RaceGoal = RaceGoal(
    distance = Distance.valueOf(distance),
    raceDate = LocalDate.ofEpochDay(raceDateEpochDay),
    raceName = raceName,
    targetFinishTimeSec = targetFinishTimeSec,
    trainingAvailability = run {
        val hours = targetWeeklyHours
        val days = targetDaysPerWeek
        if (hours != null && days != null) TrainingAvailability(weeklyHoursTarget = hours, daysPerWeekTarget = days) else null
    },
    currentFitnessEstimateSec = currentFitnessEstimateSec,
)

fun RaceGoal.toEntity(isActive: Boolean, createdAt: Instant): RaceGoalEntity = RaceGoalEntity(
    distance = distance.name,
    raceDateEpochDay = raceDate.toEpochDay(),
    raceName = raceName,
    targetFinishTimeSec = targetFinishTimeSec,
    isActive = isActive,
    createdAtEpochMilli = createdAt.toEpochMilli(),
    targetWeeklyHours = trainingAvailability?.weeklyHoursTarget,
    targetDaysPerWeek = trainingAvailability?.daysPerWeekTarget,
    currentFitnessEstimateSec = currentFitnessEstimateSec,
)

fun PlannedWorkoutWithWeekIndex.toDomainSnapshot(): PlannedWorkoutSnapshot = PlannedWorkoutSnapshot(
    id = workout.id,
    weekIndex = weekIndex,
    date = LocalDate.ofEpochDay(workout.dateEpochDay),
    discipline = Discipline.valueOf(workout.discipline),
    workoutType = WorkoutType.valueOf(workout.workoutType),
    plannedDurationSec = workout.plannedDurationSec,
    plannedLoad = workout.plannedLoad,
    status = WorkoutStatus.valueOf(workout.status),
    zone = workout.zoneLevel?.let { IntensityZone(it) },
    brickGroupId = workout.brickGroupId,
    sortOrderInDay = workout.sortOrderInDay,
    substitutedWithDiscipline = workout.substitutedDiscipline?.let { Discipline.valueOf(it) },
)

fun CompletedActivityEntity.toDomain(): CompletedActivity = CompletedActivity(
    healthConnectRecordId = healthConnectRecordId,
    discipline = Discipline.valueOf(discipline),
    startTime = Instant.ofEpochMilli(startTimeEpochMilli),
    endTime = Instant.ofEpochMilli(endTimeEpochMilli),
    distanceM = distanceM,
    avgHr = avgHr,
    maxHr = maxHr,
    avgPowerW = avgPowerW,
    normalizedPowerW = normalizedPowerW,
    calculatedLoad = calculatedLoad,
    matchedPlannedWorkoutId = matchedPlannedWorkoutId,
    matchStatus = MatchStatus.valueOf(matchStatus),
)

fun CompletedActivity.toEntity(): CompletedActivityEntity = CompletedActivityEntity(
    healthConnectRecordId = healthConnectRecordId,
    discipline = discipline.name,
    startTimeEpochMilli = startTime.toEpochMilli(),
    endTimeEpochMilli = endTime.toEpochMilli(),
    distanceM = distanceM,
    avgHr = avgHr,
    maxHr = maxHr,
    avgPowerW = avgPowerW,
    normalizedPowerW = normalizedPowerW,
    calculatedLoad = calculatedLoad,
    matchedPlannedWorkoutId = matchedPlannedWorkoutId,
    matchStatus = matchStatus.name,
)

fun RollingLoadStateEntity.toDomain(): RollingLoadState = RollingLoadState(
    acuteLoad7d = acuteLoad7d,
    chronicLoad28d = chronicLoad28d,
    consecutiveMissedDays = consecutiveMissedDays,
    lastEvaluatedDate = lastEvaluatedDateEpochDay?.let { LocalDate.ofEpochDay(it) },
)

fun RollingLoadState.toEntity(planId: Long): RollingLoadStateEntity = RollingLoadStateEntity(
    planId = planId,
    acuteLoad7d = acuteLoad7d,
    chronicLoad28d = chronicLoad28d,
    consecutiveMissedDays = consecutiveMissedDays,
    lastEvaluatedDateEpochDay = lastEvaluatedDate?.toEpochDay(),
)

fun AdaptationEvent.toEntity(): AdaptationEventEntity = AdaptationEventEntity(
    timestampEpochMilli = timestamp.toEpochMilli(),
    triggerType = triggerType.name,
    actionTaken = actionTaken.name,
    description = description,
    relatedPlannedWorkoutId = relatedPlannedWorkoutId,
)

fun AdaptationEventEntity.toDomain(): AdaptationEvent = AdaptationEvent(
    timestamp = Instant.ofEpochMilli(timestampEpochMilli),
    triggerType = AdaptationTriggerType.valueOf(triggerType),
    actionTaken = AdaptationAction.valueOf(actionTaken),
    description = description,
    relatedPlannedWorkoutId = relatedPlannedWorkoutId,
)

fun TrainingPlanEntity.toDomain(): ActivePlanSummary = ActivePlanSummary(
    id = id,
    startDate = LocalDate.ofEpochDay(startDateEpochDay),
    totalWeeks = totalWeeks,
    status = PlanStatus.valueOf(status),
)

fun PlanWeekEntity.toDomain(): PlanWeekSummary = PlanWeekSummary(
    id = id,
    weekIndex = weekIndex,
    startDate = LocalDate.ofEpochDay(startDateEpochDay),
    phase = TrainingPhase.valueOf(phase),
    isRecoveryWeek = isRecoveryWeek,
    plannedWeeklyLoad = plannedWeeklyLoad,
)

fun PlannedWorkoutEntity.toSnapshot(weekIndex: Int): PlannedWorkoutSnapshot = PlannedWorkoutSnapshot(
    id = id,
    weekIndex = weekIndex,
    date = LocalDate.ofEpochDay(dateEpochDay),
    discipline = Discipline.valueOf(discipline),
    workoutType = WorkoutType.valueOf(workoutType),
    plannedDurationSec = plannedDurationSec,
    plannedLoad = plannedLoad,
    status = WorkoutStatus.valueOf(status),
    zone = zoneLevel?.let { IntensityZone(it) },
    brickGroupId = brickGroupId,
    sortOrderInDay = sortOrderInDay,
    substitutedWithDiscipline = substitutedDiscipline?.let { Discipline.valueOf(it) },
)

fun WorkoutStepEntity.toDomain(): GeneratedWorkoutStep = GeneratedWorkoutStep(
    stepOrder = stepOrder,
    stepType = WorkoutStepType.valueOf(stepType),
    durationSec = durationSec,
    distanceM = distanceM,
    intensityZone = intensityZoneLevel?.let { IntensityZone(it) },
    repeatCount = repeatCount,
    cueText = cueText,
)
