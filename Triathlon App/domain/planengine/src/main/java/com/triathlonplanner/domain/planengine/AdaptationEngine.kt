package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.AdaptationAction
import com.triathlonplanner.core.model.AdaptationEvent
import com.triathlonplanner.core.model.AdaptationResult
import com.triathlonplanner.core.model.AdaptationTriggerType
import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.PlanMutation
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.RollingLoadState
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutType
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** A week's planned vs. completed load, as already known to the caller from persisted plan data. */
data class WeekLoadSummary(val weekIndex: Int, val plannedLoad: Int, val completedLoad: Int)

/**
 * Rule-based (not ML) plan adaptation. Evaluates missed/harder/easier-than-planned sessions and
 * proposes mutations to *future* PLANNED rows only - history is immutable. Every mutation is
 * paired with an [AdaptationEvent] for the "why did my plan change?" timeline. See class-level
 * rule comments below for the exact conditions; they mirror the plan doc's Rules A-D.
 */
object AdaptationEngine {

    /** Global safety invariant: next week's load never exceeds 10% above the recent peak. */
    const val MAX_WEEK_OVER_WEEK_GROWTH = 1.10

    private const val MISS_GRACE_DAYS = 1L
    private const val EXTENDED_ABSENCE_DAYS = 7
    private const val CONSECUTIVE_MISS_DAYS = 3
    private const val CONSECUTIVE_MISS_WEEK_FRACTION = 0.40
    private const val OVERREACH_SESSION_RATIO = 1.30
    private const val OVERREACH_WEEK_RATIO = 1.15
    private const val SEVERE_ACWR = 1.5
    private const val UNDERREACH_WEEK_RATIO = 0.75

    private val KEY_WORKOUT_TYPES = setOf(WorkoutType.LONG, WorkoutType.RACE_PACE, WorkoutType.THRESHOLD, WorkoutType.VO2MAX)

    /** Ceiling-only clamp used wherever a new week load is proposed; exposed for direct fuzz testing. */
    fun clampWeekLoad(proposedLoad: Int, lastActualLoad: Int, lastPlannedLoad: Int): Int {
        val ceiling = (maxOf(lastActualLoad, lastPlannedLoad) * MAX_WEEK_OVER_WEEK_GROWTH).roundToInt()
        return minOf(proposedLoad, ceiling)
    }

    fun evaluate(
        today: LocalDate,
        now: Instant,
        plannedWorkouts: List<PlannedWorkoutSnapshot>,
        completedActivities: List<CompletedActivity>,
        recentWeeks: List<WeekLoadSummary>,
        rollingLoadState: RollingLoadState,
    ): AdaptationResult {
        val mutations = mutableListOf<PlanMutation>()
        val events = mutableListOf<AdaptationEvent>()

        // --- Rule: nightly miss-check with 1-day grace ---
        val missCutoff = today.minusDays(MISS_GRACE_DAYS + 1)
        val newlyMissedIds = plannedWorkouts
            .filter { it.status == WorkoutStatus.PLANNED && it.discipline != Discipline.REST && it.date <= missCutoff }
            .onEach { mutations += PlanMutation.MarkMissed(it.id) }
            .map { it.id }
            .toSet()

        fun effectiveStatus(w: PlannedWorkoutSnapshot): WorkoutStatus =
            if (w.id in newlyMissedIds) WorkoutStatus.MISSED else w.status

        // --- consecutive missed *training* days, walking backward from missCutoff ---
        val plannedByDate = plannedWorkouts.filter { it.discipline != Discipline.REST }.groupBy { it.date }
        var consecutiveMissedDays = 0
        var probeDate = missCutoff
        while (true) {
            val dayWorkouts = plannedByDate[probeDate].orEmpty()
            if (dayWorkouts.isEmpty()) break
            val allMissedOrSkipped = dayWorkouts.all {
                effectiveStatus(it) == WorkoutStatus.MISSED || effectiveStatus(it) == WorkoutStatus.SKIPPED_BY_ADAPTATION
            }
            if (!allMissedOrSkipped) break
            consecutiveMissedDays++
            probeDate = probeDate.minusDays(1)
        }

        // --- session-level load ratios for matched activities ---
        val plannedById = plannedWorkouts.associateBy { it.id }
        val sessionRatios = completedActivities.mapNotNull { activity ->
            val workout = activity.matchedPlannedWorkoutId?.let(plannedById::get) ?: return@mapNotNull null
            if (workout.plannedLoad <= 0) return@mapNotNull null
            workout to activity.calculatedLoad.toDouble() / workout.plannedLoad
        }
        val overreachSessionWorkout = sessionRatios
            .filter { (workout, ratio) -> workout.workoutType in KEY_WORKOUT_TYPES && ratio > OVERREACH_SESSION_RATIO }
            .maxByOrNull { (_, ratio) -> ratio }
            ?.first

        val currentWeek = recentWeeks.lastOrNull()
        val priorWeek = recentWeeks.getOrNull(recentWeeks.size - 2)
        val currentWeekWorkouts = plannedWorkouts.filter { it.weekIndex == currentWeek?.weekIndex && it.discipline != Discipline.REST }
        val missedFraction = currentWeekWorkouts.takeIf { it.isNotEmpty() }?.let { list ->
            list.count { effectiveStatus(it) == WorkoutStatus.MISSED }.toDouble() / list.size
        } ?: 0.0

        val weekOverreach = currentWeek != null && currentWeek.plannedLoad > 0 &&
            currentWeek.completedLoad > currentWeek.plannedLoad * OVERREACH_WEEK_RATIO
        fun isUnderreachWeek(w: WeekLoadSummary) = w.plannedLoad > 0 && w.completedLoad < w.plannedLoad * UNDERREACH_WEEK_RATIO
        val sustainedUnderreach = currentWeek != null && priorWeek != null &&
            isUnderreachWeek(currentWeek) && isUnderreachWeek(priorWeek)

        val futurePlanned = plannedWorkouts.filter { it.date > today && effectiveStatus(it) == WorkoutStatus.PLANNED }

        when {
            consecutiveMissedDays >= EXTENDED_ABSENCE_DAYS -> {
                mutations += PlanMutation.RequestReEntryBlock(fromDate = today)
                events += AdaptationEvent(
                    timestamp = now,
                    triggerType = AdaptationTriggerType.EXTENDED_ABSENCE,
                    actionTaken = AdaptationAction.INSERT_RE_ENTRY_BLOCK,
                    description = "You've missed $consecutiveMissedDays days in a row. " +
                        "Inserting a re-entry week before resuming progression.",
                )
            }

            consecutiveMissedDays >= CONSECUTIVE_MISS_DAYS || missedFraction >= CONSECUTIVE_MISS_WEEK_FRACTION -> {
                val toDrop = futurePlanned.minByOrNull(::dropPriorityRank)
                if (toDrop != null) {
                    mutations += PlanMutation.DropWorkout(toDrop.id)
                }
                if (currentWeek != null) {
                    val rebaselined = clampWeekLoad(currentWeek.completedLoad, currentWeek.completedLoad, currentWeek.plannedLoad)
                    mutations += PlanMutation.RescaleWeekLoad(currentWeek.weekIndex + 1, rebaselined)
                }
                events += AdaptationEvent(
                    timestamp = now,
                    triggerType = AdaptationTriggerType.CONSECUTIVE_MISSES,
                    actionTaken = AdaptationAction.REBASELINE_NEXT_WEEK,
                    description = "Several sessions were missed this week. Dropping a lower-priority " +
                        "session and re-baselining next week's target from what you actually completed.",
                )
            }

            overreachSessionWorkout != null || weekOverreach -> {
                val nextQuality = futurePlanned
                    .filter { it.workoutType in KEY_WORKOUT_TYPES }
                    .minByOrNull { it.date }
                if (nextQuality != null) {
                    mutations += PlanMutation.DowngradeToRecovery(nextQuality.id)
                }
                if (rollingLoadState.acwr > SEVERE_ACWR) {
                    val nextLowPriority = futurePlanned
                        .filter { it.id != nextQuality?.id }
                        .minByOrNull(::dropPriorityRank)
                    if (nextLowPriority != null) {
                        mutations += PlanMutation.DowngradeToRest(nextLowPriority.id)
                    }
                }
                if (currentWeek != null) {
                    val clamped = clampWeekLoad(currentWeek.plannedLoad, currentWeek.completedLoad, currentWeek.plannedLoad)
                    mutations += PlanMutation.RescaleWeekLoad(currentWeek.weekIndex + 1, clamped)
                }
                events += AdaptationEvent(
                    timestamp = now,
                    triggerType = AdaptationTriggerType.OVERREACH,
                    actionTaken = AdaptationAction.DOWNGRADE_TO_RECOVERY,
                    description = "Recent training came in harder than planned. Downgrading the next " +
                        "quality session to easy/recovery so fatigue doesn't compound.",
                )
            }

            sustainedUnderreach -> {
                val week = checkNotNull(currentWeek) { "sustainedUnderreach implies currentWeek is non-null" }
                mutations += PlanMutation.SoftenCutback(week.weekIndex + 1, week.plannedLoad)
                events += AdaptationEvent(
                    timestamp = now,
                    triggerType = AdaptationTriggerType.UNDERREACH,
                    actionTaken = AdaptationAction.SOFTEN_CUTBACK,
                    description = "Completed load has been well below plan for two weeks running. " +
                        "Softening the next cutback slightly rather than piling on more volume.",
                )
            }

            else -> {
                // Isolated miss or a single easy session below target: no structural action, per
                // the "never cram a missed session back in" / "don't overreact to one easy day" rules.
            }
        }

        val updatedRollingLoadState = updateRollingLoadState(now, completedActivities, consecutiveMissedDays, today, rollingLoadState)

        return AdaptationResult(mutations, events, updatedRollingLoadState)
    }

    private fun updateRollingLoadState(
        now: Instant,
        completedActivities: List<CompletedActivity>,
        consecutiveMissedDays: Int,
        today: LocalDate,
        previous: RollingLoadState,
    ): RollingLoadState {
        val sevenDaysAgo = now.minus(7, ChronoUnit.DAYS)
        val twentyEightDaysAgo = now.minus(28, ChronoUnit.DAYS)
        val load7d = completedActivities.filter { it.startTime.isAfter(sevenDaysAgo) }.sumOf { it.calculatedLoad }
        val load28d = completedActivities.filter { it.startTime.isAfter(twentyEightDaysAgo) }.sumOf { it.calculatedLoad }
        return previous.copy(
            acuteLoad7d = load7d / 7.0,
            chronicLoad28d = load28d / 28.0,
            consecutiveMissedDays = consecutiveMissedDays,
            lastEvaluatedDate = today,
        )
    }

    /** Lower rank drops first: Strength > secondary quality > long/key session > brick legs. */
    private fun dropPriorityRank(w: PlannedWorkoutSnapshot): Int = when {
        w.discipline == Discipline.BRICK_BIKE || w.discipline == Discipline.BRICK_RUN -> 5
        w.workoutType == WorkoutType.LONG || w.workoutType == WorkoutType.RACE_PACE -> 4
        w.workoutType in setOf(WorkoutType.TEMPO, WorkoutType.THRESHOLD, WorkoutType.VO2MAX, WorkoutType.CSS_TEST, WorkoutType.FTP_TEST) -> 3
        w.discipline == Discipline.STRENGTH -> 1
        else -> 2
    }
}
