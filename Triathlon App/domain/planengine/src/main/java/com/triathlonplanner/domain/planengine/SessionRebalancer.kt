package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.AdaptationAction
import com.triathlonplanner.core.model.AdaptationEvent
import com.triathlonplanner.core.model.AdaptationTriggerType
import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.PlanMutation
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutType
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** What actually happened to one planned session, relative to what it asked for. */
data class SessionOutcome(
    val workout: PlannedWorkoutSnapshot,
    /** Null when nothing was recorded against this session at all. */
    val completedLoad: Int?,
    /** Set when the athlete trained a different sport than the one prescribed. */
    val actualDiscipline: Discipline? = null,
)

data class RebalanceResult(
    val mutations: List<PlanMutation>,
    val events: List<AdaptationEvent>,
)

/**
 * Nudges the sessions *around* a disrupted one so the block still delivers roughly the training
 * stress it was designed to, instead of letting one bad day silently become a permanent hole.
 *
 * Handles four disruptions: a session missed entirely, done easier than prescribed, done harder
 * than prescribed, or done as a different sport.
 *
 * **It only ever changes duration, never intensity or zone.** That single decision is what keeps
 * this safe. Volume scales smoothly and predictably; intensity does not, and raising a session's
 * zone to "catch up" would both spike injury risk and quietly wreck the polarized 80/20
 * distribution the plan is built around (see [IntensityDistributionAuditor]). So a missed threshold
 * session is never repaid by making the next one harder - it is repaid, partially, with a little
 * more easy volume.
 *
 * **The rails that make this "within reason":**
 * - Recovery sessions and rest days are never touched, in either direction. Their whole job is to
 *   let adaptation happen, and spending them on catch-up defeats the training they'd be repaying.
 * - Recovery weeks, taper and race week are off limits (the caller marks these), for the same
 *   reason at week scale - a cutback week that gets topped up is not a cutback week.
 * - No single session moves by more than [MAX_SESSION_ADJUST_FRACTION] of its own planned duration,
 *   so nothing becomes unrecognisably hard, and nothing shrinks to pointlessness.
 * - Only [DEFICIT_RECOVERY_FRACTION] of lost training is ever chased. Missed work is mostly gone;
 *   pretending otherwise is how athletes dig holes. Excess fatigue, by contrast, is unwound in
 *   full, because reducing load is always the safe direction.
 * - Adjustments spread across several nearby sessions rather than landing on one.
 */
object SessionRebalancer {

    /** How far ahead "in the vicinity" reaches. Beyond this the plan has moved on. */
    const val VICINITY_DAYS = 10L

    /** Under this fraction of planned load, a session counts as done too easily. */
    const val UNDER_EFFORT_RATIO = 0.75

    /** Over this fraction, it counts as done too hard. */
    const val OVER_EFFORT_RATIO = 1.30

    /** Cap on how much any one session may be stretched or trimmed. */
    const val MAX_SESSION_ADJUST_FRACTION = 0.20

    /** Share of a shortfall that is chased at all. The rest is written off deliberately. */
    const val DEFICIT_RECOVERY_FRACTION = 0.50

    /** Below this, the difference is noise and the plan is left alone. */
    private const val MIN_MEANINGFUL_LOAD_DELTA = 15

    private const val MIN_SESSION_DURATION_SEC = 20 * 60

    private val PROTECTED_TYPES = setOf(WorkoutType.RECOVERY, WorkoutType.REST)

    /**
     * The only session types allowed to *grow*.
     *
     * This is the rule that keeps the polarized 80/20 split intact under adaptation. Lengthening an
     * easy session raises the easy share; lengthening a threshold session raises the hard share and
     * would erode the distribution every time an athlete missed a workout. So shortfalls are always
     * repaid in easy volume - which is also what the polarized model would prescribe anyway.
     */
    private val GROWABLE_TYPES = setOf(WorkoutType.EASY, WorkoutType.LONG)

    /**
     * @param protectedWeekIndices weeks whose load must not be topped up - recovery weeks, taper
     *   and race week. The caller owns periodization, so it names them.
     */
    fun rebalance(
        today: LocalDate,
        now: Instant,
        outcomes: List<SessionOutcome>,
        upcoming: List<PlannedWorkoutSnapshot>,
        protectedWeekIndices: Set<Int> = emptySet(),
    ): RebalanceResult {
        val adjustable = upcoming
            .filter { it.status == WorkoutStatus.PLANNED }
            .filter { it.date > today && it.date <= today.plusDays(VICINITY_DAYS) }
            .filter { it.workoutType !in PROTECTED_TYPES }
            .filter { it.weekIndex !in protectedWeekIndices }
            .filter { it.discipline != Discipline.REST }
            .sortedBy { it.date }

        if (adjustable.isEmpty()) return RebalanceResult(emptyList(), emptyList())

        val netDelta = outcomes.sumOf { signedDeltaFor(it) }
        val mutations = mutableListOf<PlanMutation>()
        val events = mutableListOf<AdaptationEvent>()

        // --- Wrong sport: the load happened, the specific stimulus didn't ------------------------
        // Swapping a swim for a run leaves the week's load roughly intact but the swim untrained
        // and the legs over-run, so the fix is a transfer between disciplines rather than a change
        // in total volume.
        outcomes.filter { it.actualDiscipline != null && it.actualDiscipline != it.workout.discipline }
            .forEach { outcome ->
                val missedSport = outcome.workout.discipline
                val doneInstead = outcome.actualDiscipline ?: return@forEach
                val topUp = (outcome.workout.plannedLoad * DEFICIT_RECOVERY_FRACTION).roundToInt()

                val added = distribute(
                    topUp,
                    adjustable.filter { sameSport(it.discipline, missedSport) && it.workoutType in GROWABLE_TYPES },
                    mutations,
                )
                distribute(-topUp, adjustable.filter { sameSport(it.discipline, doneInstead) }, mutations)

                if (added > 0) {
                    events += AdaptationEvent(
                        timestamp = now,
                        triggerType = AdaptationTriggerType.SESSION_SUBSTITUTED,
                        actionTaken = AdaptationAction.RECORD_SUBSTITUTION,
                        description = "You did a ${doneInstead.readable()} instead of the planned " +
                            "${missedSport.readable()}. Adding a little ${missedSport.readable()} volume back " +
                            "over the coming days and easing the next ${doneInstead.readable()} to keep the balance.",
                        relatedPlannedWorkoutId = outcome.workout.id,
                    )
                }
            }

        // --- Too little / too much overall load --------------------------------------------------
        if (abs(netDelta) >= MIN_MEANINGFUL_LOAD_DELTA) {
            if (netDelta > 0) {
                // Shortfall: chase only part of it, and only through aerobic sessions.
                val chase = (netDelta * DEFICIT_RECOVERY_FRACTION).roundToInt()
                val applied = distribute(chase, adjustable.filter { it.workoutType in GROWABLE_TYPES }, mutations)
                if (applied > 0) {
                    events += AdaptationEvent(
                        timestamp = now,
                        triggerType = AdaptationTriggerType.UNDERREACH,
                        actionTaken = AdaptationAction.REBASELINE_NEXT_WEEK,
                        description = "Recent sessions came in below plan. Adding a little length to the " +
                            "next few easy sessions to recover part of the missed work - not all of it, " +
                            "and without making anything harder.",
                    )
                }
            } else {
                // Surplus: unwind it fully. Reducing load is always the safe direction.
                val applied = distribute(netDelta, adjustable, mutations)
                if (applied < 0) {
                    events += AdaptationEvent(
                        timestamp = now,
                        triggerType = AdaptationTriggerType.OVERREACH,
                        actionTaken = AdaptationAction.DOWNGRADE_TO_RECOVERY,
                        description = "Recent training ran harder than planned. Trimming the next few " +
                            "sessions so the extra fatigue doesn't carry into the rest of the block.",
                    )
                }
            }
        }

        return RebalanceResult(mutations.mergedById(), events)
    }

    /** Positive = the athlete owes training; negative = they banked extra. */
    private fun signedDeltaFor(outcome: SessionOutcome): Int {
        val planned = outcome.workout.plannedLoad
        if (planned <= 0) return 0
        val completed = outcome.completedLoad ?: return planned // missed entirely
        val ratio = completed.toDouble() / planned
        return when {
            ratio < UNDER_EFFORT_RATIO -> planned - completed
            ratio > OVER_EFFORT_RATIO -> planned - completed // negative: a surplus
            else -> 0
        }
    }

    /**
     * Spreads [loadDelta] across [candidates] *in proportion to each session's own load*, each
     * capped at its own [MAX_SESSION_ADJUST_FRACTION].
     *
     * Proportional rather than equal-share for a specific reason: an equal absolute change would
     * hit a 30-minute session far harder than a two-hour one, skewing the week's balance between
     * sessions. Scaling everything by the same percentage leaves the week's shape - and so its
     * time-in-zone ratios - as the plan designed them.
     *
     * Returns the load actually placed, which may be less than asked for. The caps are the point,
     * so falling short is a correct outcome rather than a failure to work around.
     */
    private fun distribute(
        loadDelta: Int,
        candidates: List<PlannedWorkoutSnapshot>,
        into: MutableList<PlanMutation>,
    ): Int {
        if (loadDelta == 0 || candidates.isEmpty()) return 0

        var remaining = loadDelta
        val totalLoad = candidates.sumOf { it.plannedLoad }.takeIf { it > 0 } ?: return 0

        candidates.forEach { workout ->
            if (remaining == 0) return@forEach
            val cap = (workout.plannedLoad * MAX_SESSION_ADJUST_FRACTION).roundToInt()
            val proportional = loadDelta * (workout.plannedLoad.toDouble() / totalLoad)
            val wanted = proportional.roundToInt().coerceIn(-cap, cap)
            val share = if (loadDelta > 0) wanted.coerceAtMost(remaining) else wanted.coerceAtLeast(remaining)
            if (share == 0) return@forEach

            val newLoad = (workout.plannedLoad + share).coerceAtLeast(1)
            // Duration moves in step with load so the session stays internally coherent; zone is
            // untouched, which is what stops any of this raising intensity.
            val scale = newLoad.toDouble() / workout.plannedLoad
            val newDuration = (workout.plannedDurationSec * scale).roundToInt()
                .coerceAtLeast(MIN_SESSION_DURATION_SEC)

            if (newDuration != workout.plannedDurationSec) {
                into += PlanMutation.AdjustSessionLoad(workout.id, newDuration, newLoad)
                remaining -= share
            }
        }
        return loadDelta - remaining
    }

    /** A session can be touched by both the sport transfer and the load pass; keep the last word. */
    private fun List<PlanMutation>.mergedById(): List<PlanMutation> {
        val byId = LinkedHashMap<Long, PlanMutation>()
        forEach { mutation ->
            if (mutation is PlanMutation.AdjustSessionLoad) byId[mutation.workoutId] = mutation
        }
        return byId.values.toList()
    }

    private fun sameSport(a: Discipline, b: Discipline): Boolean = base(a) == base(b)

    private fun base(discipline: Discipline): Discipline = when (discipline) {
        Discipline.BRICK_BIKE -> Discipline.BIKE
        Discipline.BRICK_RUN -> Discipline.RUN
        else -> discipline
    }

    private fun Discipline.readable(): String = base(this).name.lowercase()
}
