package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.GeneratedWorkoutStep
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutStepType
import com.triathlonplanner.core.model.WorkoutType
import kotlin.math.roundToInt

/**
 * Warmup/[drill]/main-set/cooldown breakdown. THRESHOLD/VO2MAX/TEMPO get a real interval
 * structure (repeated work+recovery blocks); everything else is a single continuous main effort.
 * Interval shape uses FIXED, physiologically realistic per-rep durations (see [INTERVAL_PATTERNS])
 * with rep *count* scaling to fit the available main-set time - not the reverse, which is what
 * previously let a long session stretch a single VO2max rep to absurd lengths. Swim sessions get
 * two short technique-drill blocks between warmup and the main set.
 */
object WorkoutStepBuilder {

    private const val WARMUP_FRACTION = 0.10
    private const val COOLDOWN_FRACTION = 0.10

    // Swim-only: catch-up + single-arm drills, split evenly, taken out of what would otherwise be
    // main-set time. Skipped for very short or RECOVERY swim sessions where drills aren't worth it.
    private const val SWIM_DRILL_FRACTION = 0.15
    private const val MIN_SWIM_DRILL_ELIGIBLE_SEC = 20 * 60

    private data class IntervalPattern(val workSec: Int, val recoverySec: Int)

    private val INTERVAL_PATTERNS = mapOf(
        WorkoutType.THRESHOLD to IntervalPattern(workSec = 8 * 60, recoverySec = 2 * 60), // 8min on/2min off
        WorkoutType.VO2MAX to IntervalPattern(workSec = 4 * 60, recoverySec = 4 * 60), // 4min on/4min off
        WorkoutType.TEMPO to IntervalPattern(workSec = 12 * 60, recoverySec = 3 * 60), // 12min on/3min off, sweet-spot-style
    )

    fun build(
        durationSec: Int,
        zone: IntensityZone?,
        workoutType: WorkoutType,
        discipline: Discipline,
        profile: UserZoneProfile = UserZoneProfile(maxHr = 180, ftpWatts = 200, cssPaceSecPer100m = 90),
    ): List<GeneratedWorkoutStep> {
        if (workoutType == WorkoutType.STRENGTH_SESSION || workoutType == WorkoutType.REST) {
            return listOf(GeneratedWorkoutStep(stepOrder = 1, stepType = WorkoutStepType.MAIN, durationSec = durationSec, intensityZone = zone))
        }

        // The zone itself already falls back to heart rate at read-time when CSS/FTP is missing
        // (see ZoneResolver) - this only decides whether *cue text* should stop naming a pace/power
        // metric the profile doesn't actually have.
        val usesHeartRateFallback = when (discipline) {
            Discipline.SWIM -> profile.cssPaceSecPer100m == null
            Discipline.BIKE, Discipline.BRICK_BIKE -> profile.ftpWatts == null
            else -> false
        }

        val warmupSec = (durationSec * WARMUP_FRACTION).roundToInt()
        val cooldownSec = (durationSec * COOLDOWN_FRACTION).roundToInt()
        val easyZone = IntensityZone(1)

        val drillSteps = if (isSwimDrillEligible(discipline, workoutType, durationSec)) {
            buildSwimDrillSteps(durationSec)
        } else {
            emptyList()
        }
        val mainSec = durationSec - warmupSec - cooldownSec - drillSteps.sumOf { it.durationSec ?: 0 }

        val pattern = INTERVAL_PATTERNS[workoutType]
        val mainSteps = if (pattern != null) {
            buildIntervalSteps(mainSec, pattern, zone, easyZone, discipline, workoutType, usesHeartRateFallback)
        } else {
            listOf(
                GeneratedWorkoutStep(
                    stepOrder = 0,
                    stepType = WorkoutStepType.MAIN,
                    durationSec = mainSec,
                    intensityZone = zone,
                    cueText = cueFor(discipline, WorkoutStepType.MAIN, workoutType, usesHeartRateFallback),
                ),
            )
        }

        // Fixed-duration reps (via floor division) can leave a remainder of mainSec unused - fold
        // it into the cooldown rather than silently dropping it, so the workout's actual total
        // still honors the duration WeeklyTemplate scheduled.
        val usedMainSec = mainSteps.sumOf { (it.durationSec ?: 0) * (it.repeatCount ?: 1) }
        val leftoverSec = (mainSec - usedMainSec).coerceAtLeast(0)

        val steps = mutableListOf(
            GeneratedWorkoutStep(0, WorkoutStepType.WARMUP, warmupSec, intensityZone = easyZone, cueText = cueFor(discipline, WorkoutStepType.WARMUP, workoutType, usesHeartRateFallback)),
        )
        steps += drillSteps
        steps += mainSteps
        steps += GeneratedWorkoutStep(0, WorkoutStepType.COOLDOWN, cooldownSec + leftoverSec, intensityZone = easyZone, cueText = cueFor(discipline, WorkoutStepType.COOLDOWN, workoutType, usesHeartRateFallback))
        return steps.mapIndexed { i, step -> step.copy(stepOrder = i + 1) }
    }

    private fun isSwimDrillEligible(discipline: Discipline, workoutType: WorkoutType, durationSec: Int): Boolean =
        discipline == Discipline.SWIM && workoutType != WorkoutType.RECOVERY && durationSec >= MIN_SWIM_DRILL_ELIGIBLE_SEC

    private fun buildSwimDrillSteps(totalDurationSec: Int): List<GeneratedWorkoutStep> {
        val eachDrillSec = ((totalDurationSec * SWIM_DRILL_FRACTION) / 2).roundToInt()
        return listOf(
            GeneratedWorkoutStep(
                stepOrder = 0,
                stepType = WorkoutStepType.DRILL,
                durationSec = eachDrillSec,
                intensityZone = IntensityZone(1),
                cueText = "Catch-up drill - reach long and let one hand touch the other before the next stroke begins.",
            ),
            GeneratedWorkoutStep(
                stepOrder = 0,
                stepType = WorkoutStepType.DRILL,
                durationSec = eachDrillSec,
                intensityZone = IntensityZone(1),
                cueText = "Single-arm drill - isolate one arm at a time to groove a clean, high-elbow catch.",
            ),
        )
    }

    private fun buildIntervalSteps(
        mainSec: Int,
        pattern: IntervalPattern,
        workZone: IntensityZone?,
        recoveryZone: IntensityZone,
        discipline: Discipline,
        workoutType: WorkoutType,
        usesHeartRateFallback: Boolean,
    ): List<GeneratedWorkoutStep> {
        val reps = mainSec / (pattern.workSec + pattern.recoverySec)

        if (reps < 1) {
            // Not enough main-set time for even one full-length rep - fall back to one continuous
            // block rather than an unrealistically short/tiny interval.
            return listOf(
                GeneratedWorkoutStep(
                    stepOrder = 0,
                    stepType = WorkoutStepType.MAIN,
                    durationSec = mainSec,
                    intensityZone = workZone,
                    cueText = cueFor(discipline, WorkoutStepType.MAIN, workoutType, usesHeartRateFallback),
                ),
            )
        }

        return listOf(
            GeneratedWorkoutStep(
                stepOrder = 0,
                stepType = WorkoutStepType.INTERVAL,
                durationSec = pattern.workSec,
                intensityZone = workZone,
                repeatCount = reps,
                cueText = cueFor(discipline, WorkoutStepType.INTERVAL, workoutType, usesHeartRateFallback),
            ),
            GeneratedWorkoutStep(
                stepOrder = 0,
                stepType = WorkoutStepType.RECOVERY,
                durationSec = pattern.recoverySec,
                intensityZone = recoveryZone,
                repeatCount = reps,
                cueText = cueFor(discipline, WorkoutStepType.RECOVERY, workoutType, usesHeartRateFallback),
            ),
        )
    }

    /** Short, discipline/step-aware coaching cues. [Discipline.BRICK_RUN] always gets the same
     * run-off-the-bike cue regardless of step type - the point of that leg is the transition, not
     * the specific interval structure. [usesHeartRateFallback] is true when the profile lacks the
     * discipline's own metric (CSS for swim, FTP for bike) - only the swim threshold cue currently
     * names a metric directly, so that's the only branch it changes. */
    private fun cueFor(discipline: Discipline, stepType: WorkoutStepType, workoutType: WorkoutType, usesHeartRateFallback: Boolean): String? {
        if (discipline == Discipline.BRICK_RUN) {
            return "Quick, light turnover - let your legs adapt to running off the bike."
        }
        return when (stepType) {
            WorkoutStepType.WARMUP -> when (discipline) {
                Discipline.BIKE, Discipline.BRICK_BIKE -> "Gradual build, spin up to 90+ RPM by the end."
                Discipline.RUN -> "Easy jog, include 3-4 relaxed strides to open up the legs."
                Discipline.SWIM -> "Easy freestyle, focus on smooth, relaxed technique."
                else -> null
            }
            WorkoutStepType.DRILL -> null // set explicitly per-drill in buildSwimDrillSteps
            WorkoutStepType.INTERVAL -> cueForIntervalWork(discipline, workoutType, usesHeartRateFallback)
            WorkoutStepType.RECOVERY -> "Easy effort - let your heart rate come down before the next rep."
            WorkoutStepType.COOLDOWN -> "Easy effort, gradually reduce to a full stop."
            WorkoutStepType.MAIN -> when (workoutType) {
                WorkoutType.RACE_PACE -> "Settle into your target race effort."
                WorkoutType.EASY -> cueForEasy(discipline)
                WorkoutType.LONG -> cueForLong(discipline)
                WorkoutType.RECOVERY -> "Very easy effort - this is about recovery, not fitness gains."
                WorkoutType.FTP_TEST -> "Pace evenly across the full test - a slight negative split beats blowing up early."
                WorkoutType.CSS_TEST -> "Best sustainable effort for the full distance - stay smooth, don't sprint the start."
                // Session was too short to fit even one full interval rep (see buildIntervalSteps'
                // fallback) - still the same target effort, just continuous instead of broken into reps.
                WorkoutType.THRESHOLD, WorkoutType.VO2MAX, WorkoutType.TEMPO -> cueForIntervalWork(discipline, workoutType, usesHeartRateFallback)
                else -> null
            }
        }
    }

    private fun cueForEasy(discipline: Discipline): String = when (discipline) {
        Discipline.SWIM -> "Smooth, relaxed pace - focus on consistent technique, not speed."
        Discipline.BIKE, Discipline.BRICK_BIKE -> "Easy, conversational pace - you should be able to chat."
        Discipline.RUN -> "Easy, conversational pace - resist the urge to push the effort."
        else -> "Comfortable, conversational effort."
    }

    private fun cueForLong(discipline: Discipline): String = when (discipline) {
        Discipline.SWIM -> "Steady aerobic pace - settle in and keep your stroke consistent for the distance."
        Discipline.BIKE, Discipline.BRICK_BIKE -> "Steady aerobic effort - settle in, fuel and hydrate regularly."
        Discipline.RUN -> "Steady aerobic effort - keep it comfortable, this builds endurance, not speed."
        else -> "Steady aerobic effort - keep it comfortable for the full distance."
    }

    private fun cueForIntervalWork(discipline: Discipline, workoutType: WorkoutType, usesHeartRateFallback: Boolean): String? = when (workoutType) {
        WorkoutType.VO2MAX -> when (discipline) {
            Discipline.BIKE, Discipline.BRICK_BIKE -> "Hold 90+ RPM, stay seated and controlled."
            Discipline.RUN -> "Hard but controlled - focus on quick, light turnover."
            else -> "Hard, controlled effort."
        }
        WorkoutType.THRESHOLD -> when (discipline) {
            Discipline.SWIM -> if (usesHeartRateFallback) {
                "Hold a hard, sustainable heart-rate effort - focus on a strong, high-elbow catch."
            } else {
                "Hold your CSS pace, focus on a strong, high-elbow catch."
            }
            Discipline.BIKE, Discipline.BRICK_BIKE -> "Steady, sustainable hard effort - like a 40-60min race pace."
            Discipline.RUN -> "Comfortably hard - you could speak in short sentences, not full ones."
            else -> null
        }
        WorkoutType.TEMPO -> "Strong, sustainable effort - controlled, not maxed out."
        else -> null
    }
}
