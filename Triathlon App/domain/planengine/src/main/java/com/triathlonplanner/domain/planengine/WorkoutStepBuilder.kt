package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.GeneratedWorkoutStep
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.WorkoutStepType
import com.triathlonplanner.core.model.WorkoutType
import kotlin.math.roundToInt

/**
 * Warmup/main-set/cooldown breakdown. THRESHOLD/VO2MAX/TEMPO get a real interval structure
 * (repeated work+recovery blocks); everything else is a single continuous main effort. Interval
 * shape (rep count, work:recovery ratio) is fixed per type and the absolute durations scale to
 * fit the session's actual main-set time, so a 40-min and a 60-min Threshold session both read as
 * "4x hard with short recovery" rather than needing hardcoded absolute minutes.
 */
object WorkoutStepBuilder {

    private const val WARMUP_FRACTION = 0.10
    private const val COOLDOWN_FRACTION = 0.10
    private const val MIN_INTERVAL_WORK_SEC = 60 // below this, an interval structure stops making sense

    private data class IntervalPattern(val reps: Int, val workToRecoveryRatio: Double)

    private val INTERVAL_PATTERNS = mapOf(
        WorkoutType.THRESHOLD to IntervalPattern(reps = 4, workToRecoveryRatio = 4.0), // e.g. 8min on / 2min off
        WorkoutType.VO2MAX to IntervalPattern(reps = 6, workToRecoveryRatio = 1.0), // e.g. 3min on / 3min off
        WorkoutType.TEMPO to IntervalPattern(reps = 2, workToRecoveryRatio = 3.0), // e.g. 15min on / 5min off
    )

    fun build(durationSec: Int, zone: IntensityZone?, workoutType: WorkoutType): List<GeneratedWorkoutStep> {
        if (workoutType == WorkoutType.STRENGTH_SESSION || workoutType == WorkoutType.REST) {
            return listOf(GeneratedWorkoutStep(stepOrder = 1, stepType = WorkoutStepType.MAIN, durationSec = durationSec, intensityZone = zone))
        }

        val warmupSec = (durationSec * WARMUP_FRACTION).roundToInt()
        val cooldownSec = (durationSec * COOLDOWN_FRACTION).roundToInt()
        val mainSec = durationSec - warmupSec - cooldownSec
        val easyZone = IntensityZone(1)

        val pattern = INTERVAL_PATTERNS[workoutType]
        val mainSteps = if (pattern != null) {
            buildIntervalSteps(mainSec, pattern, zone, easyZone)
        } else {
            listOf(GeneratedWorkoutStep(stepOrder = 0, stepType = WorkoutStepType.MAIN, durationSec = mainSec, intensityZone = zone))
        }

        val steps = mutableListOf(GeneratedWorkoutStep(1, WorkoutStepType.WARMUP, warmupSec, intensityZone = easyZone))
        steps += mainSteps.mapIndexed { i, step -> step.copy(stepOrder = i + 2) }
        steps += GeneratedWorkoutStep(steps.size + 1, WorkoutStepType.COOLDOWN, cooldownSec, intensityZone = easyZone)
        return steps
    }

    private fun buildIntervalSteps(
        mainSec: Int,
        pattern: IntervalPattern,
        workZone: IntensityZone?,
        recoveryZone: IntensityZone,
    ): List<GeneratedWorkoutStep> {
        val perRepSec = mainSec / pattern.reps
        // work + recovery = perRepSec, work = recovery * ratio  =>  recovery = perRepSec / (ratio + 1)
        val recoverySec = (perRepSec / (pattern.workToRecoveryRatio + 1)).roundToInt()
        val workSec = perRepSec - recoverySec

        if (workSec < MIN_INTERVAL_WORK_SEC) {
            // Session too short for this many reps to make sense - fall back to one continuous block.
            return listOf(GeneratedWorkoutStep(stepOrder = 0, stepType = WorkoutStepType.MAIN, durationSec = mainSec, intensityZone = workZone))
        }

        return listOf(
            GeneratedWorkoutStep(0, WorkoutStepType.INTERVAL, workSec, intensityZone = workZone, repeatCount = pattern.reps),
            GeneratedWorkoutStep(0, WorkoutStepType.RECOVERY, recoverySec, intensityZone = recoveryZone, repeatCount = pattern.reps),
        )
    }
}
