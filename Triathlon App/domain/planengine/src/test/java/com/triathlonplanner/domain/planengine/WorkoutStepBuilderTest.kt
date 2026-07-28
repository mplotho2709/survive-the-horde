package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.WorkoutStepType
import com.triathlonplanner.core.model.WorkoutType
import org.junit.Test

class WorkoutStepBuilderTest {

    @Test
    fun `threshold workout produces an interval and recovery block`() {
        val steps = WorkoutStepBuilder.build(durationSec = 3600, zone = IntensityZone(4), workoutType = WorkoutType.THRESHOLD)

        assertThat(steps.map { it.stepType }).containsExactly(
            WorkoutStepType.WARMUP, WorkoutStepType.INTERVAL, WorkoutStepType.RECOVERY, WorkoutStepType.COOLDOWN,
        ).inOrder()
        val interval = steps.single { it.stepType == WorkoutStepType.INTERVAL }
        val recovery = steps.single { it.stepType == WorkoutStepType.RECOVERY }
        assertThat(interval.repeatCount).isEqualTo(4)
        assertThat(recovery.repeatCount).isEqualTo(4)
        assertThat(interval.intensityZone).isEqualTo(IntensityZone(4))
        // Threshold pattern is work:recovery = 4:1, so work should be clearly longer than recovery.
        assertThat(interval.durationSec).isGreaterThan(recovery.durationSec!! * 2)
    }

    @Test
    fun `vo2max workout uses roughly equal work and recovery`() {
        val steps = WorkoutStepBuilder.build(durationSec = 3600, zone = IntensityZone(5), workoutType = WorkoutType.VO2MAX)

        val interval = steps.single { it.stepType == WorkoutStepType.INTERVAL }
        val recovery = steps.single { it.stepType == WorkoutStepType.RECOVERY }
        assertThat(interval.repeatCount).isEqualTo(6)
        // 1:1 ratio - work and recovery should be within a couple seconds of each other (rounding).
        assertThat(kotlin.math.abs(interval.durationSec!! - recovery.durationSec!!)).isAtMost(2)
    }

    @Test
    fun `easy workout has a single continuous main block, no intervals`() {
        val steps = WorkoutStepBuilder.build(durationSec = 1800, zone = IntensityZone(2), workoutType = WorkoutType.EASY)

        assertThat(steps.map { it.stepType }).containsExactly(
            WorkoutStepType.WARMUP, WorkoutStepType.MAIN, WorkoutStepType.COOLDOWN,
        ).inOrder()
    }

    @Test
    fun `strength session is a single block with no warmup-cooldown split`() {
        val steps = WorkoutStepBuilder.build(durationSec = 2400, zone = null, workoutType = WorkoutType.STRENGTH_SESSION)

        assertThat(steps).hasSize(1)
        assertThat(steps.single().stepType).isEqualTo(WorkoutStepType.MAIN)
        assertThat(steps.single().durationSec).isEqualTo(2400)
    }

    @Test
    fun `very short session falls back to a continuous block instead of tiny intervals`() {
        // 6 reps of VO2max over a very short total duration would produce sub-minute work intervals.
        val steps = WorkoutStepBuilder.build(durationSec = 300, zone = IntensityZone(5), workoutType = WorkoutType.VO2MAX)

        assertThat(steps.map { it.stepType }).containsExactly(
            WorkoutStepType.WARMUP, WorkoutStepType.MAIN, WorkoutStepType.COOLDOWN,
        ).inOrder()
    }

    @Test
    fun `step durations sum to the total requested duration`() {
        val total = 3600
        val steps = WorkoutStepBuilder.build(durationSec = total, zone = IntensityZone(4), workoutType = WorkoutType.THRESHOLD)

        val intervalSteps = steps.filter { it.stepType == WorkoutStepType.INTERVAL || it.stepType == WorkoutStepType.RECOVERY }
        val warmupCooldown = steps.filter { it.stepType == WorkoutStepType.WARMUP || it.stepType == WorkoutStepType.COOLDOWN }
            .sumOf { it.durationSec ?: 0 }
        val mainSum = intervalSteps.sumOf { step -> (step.durationSec ?: 0) * (step.repeatCount ?: 1) }

        assertThat(warmupCooldown + mainSum).isWithin(total / 20).of(total)
    }
}
