package com.triathlonplanner.domain.planengine

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutStepType
import com.triathlonplanner.core.model.WorkoutType
import org.junit.Test

class WorkoutStepBuilderTest {

    @Test
    fun `threshold workout produces fixed-duration interval and recovery reps`() {
        val steps = WorkoutStepBuilder.build(durationSec = 3600, zone = IntensityZone(4), workoutType = WorkoutType.THRESHOLD, discipline = Discipline.RUN)

        assertThat(steps.map { it.stepType }).containsExactly(
            WorkoutStepType.WARMUP, WorkoutStepType.INTERVAL, WorkoutStepType.RECOVERY, WorkoutStepType.COOLDOWN,
        ).inOrder()
        val interval = steps.single { it.stepType == WorkoutStepType.INTERVAL }
        val recovery = steps.single { it.stepType == WorkoutStepType.RECOVERY }
        // Fixed 8min work / 2min recovery pattern, regardless of overall session length.
        assertThat(interval.durationSec).isEqualTo(8 * 60)
        assertThat(recovery.durationSec).isEqualTo(2 * 60)
        assertThat(interval.repeatCount).isEqualTo(recovery.repeatCount)
        assertThat(interval.intensityZone).isEqualTo(IntensityZone(4))
    }

    @Test
    fun `vo2max workout uses fixed 4-minute on-off reps regardless of overall duration`() {
        val short = WorkoutStepBuilder.build(durationSec = 1800, zone = IntensityZone(5), workoutType = WorkoutType.VO2MAX, discipline = Discipline.BIKE)
        val long = WorkoutStepBuilder.build(durationSec = 3600, zone = IntensityZone(5), workoutType = WorkoutType.VO2MAX, discipline = Discipline.BIKE)

        val shortInterval = short.single { it.stepType == WorkoutStepType.INTERVAL }
        val longInterval = long.single { it.stepType == WorkoutStepType.INTERVAL }
        // A longer session should add MORE reps, not LONGER reps - this is the core fix for the
        // previously-reported 103-minute-VO2max bug (a longer total stretched each rep instead).
        assertThat(shortInterval.durationSec).isEqualTo(4 * 60)
        assertThat(longInterval.durationSec).isEqualTo(4 * 60)
        assertThat(longInterval.repeatCount!!).isGreaterThan(shortInterval.repeatCount!!)
    }

    @Test
    fun `a previously-impossible 103-minute session now produces a bounded, sane total`() {
        // This exact scenario (Olympic distance, high weekly-hours target) previously produced a
        // 103-minute continuous VO2max block - now WeeklyTemplate bounds it well before it reaches
        // WorkoutStepBuilder, but this pins the builder's own behavior for an unrealistically long
        // input too, as a regression guard on the fix itself.
        val steps = WorkoutStepBuilder.build(durationSec = 103 * 60, zone = IntensityZone(5), workoutType = WorkoutType.VO2MAX, discipline = Discipline.BIKE)

        val interval = steps.single { it.stepType == WorkoutStepType.INTERVAL }
        assertThat(interval.durationSec).isEqualTo(4 * 60)
        // However long the input, individual work reps never stretch beyond the fixed pattern.
        assertThat(interval.durationSec!!).isAtMost(5 * 60)
    }

    @Test
    fun `easy workout has a single continuous main block, no intervals`() {
        val steps = WorkoutStepBuilder.build(durationSec = 1800, zone = IntensityZone(2), workoutType = WorkoutType.EASY, discipline = Discipline.RUN)

        assertThat(steps.map { it.stepType }).containsExactly(
            WorkoutStepType.WARMUP, WorkoutStepType.MAIN, WorkoutStepType.COOLDOWN,
        ).inOrder()
    }

    @Test
    fun `easy, long, and recovery main blocks all get a cue, not just interval-type sessions`() {
        // EASY/LONG/RECOVERY are the bulk of all training (polarized 80 percent) - a MAIN block
        // with no cue at all left the user with just "Main set - 21 min" and no guidance.
        val easy = WorkoutStepBuilder.build(durationSec = 30 * 60, zone = IntensityZone(2), workoutType = WorkoutType.EASY, discipline = Discipline.SWIM)
        val long = WorkoutStepBuilder.build(durationSec = 60 * 60, zone = IntensityZone(1), workoutType = WorkoutType.LONG, discipline = Discipline.RUN)
        val recovery = WorkoutStepBuilder.build(durationSec = 20 * 60, zone = IntensityZone(1), workoutType = WorkoutType.RECOVERY, discipline = Discipline.BIKE)

        assertThat(easy.single { it.stepType == WorkoutStepType.MAIN }.cueText).isNotNull()
        assertThat(long.single { it.stepType == WorkoutStepType.MAIN }.cueText).isNotNull()
        assertThat(recovery.single { it.stepType == WorkoutStepType.MAIN }.cueText).isNotNull()
    }

    @Test
    fun `strength session is a single block with no warmup-cooldown split`() {
        val steps = WorkoutStepBuilder.build(durationSec = 2400, zone = null, workoutType = WorkoutType.STRENGTH_SESSION, discipline = Discipline.STRENGTH)

        assertThat(steps).hasSize(1)
        assertThat(steps.single().stepType).isEqualTo(WorkoutStepType.MAIN)
        assertThat(steps.single().durationSec).isEqualTo(2400)
    }

    @Test
    fun `very short session falls back to a continuous block instead of a tiny interval`() {
        // Main-set time here (4min) is shorter than even one VO2max work+recovery cycle (8min).
        val steps = WorkoutStepBuilder.build(durationSec = 300, zone = IntensityZone(5), workoutType = WorkoutType.VO2MAX, discipline = Discipline.BIKE)

        assertThat(steps.map { it.stepType }).containsExactly(
            WorkoutStepType.WARMUP, WorkoutStepType.MAIN, WorkoutStepType.COOLDOWN,
        ).inOrder()
    }

    @Test
    fun `step durations sum to the total requested duration, remainder folded into cooldown`() {
        val total = 3600
        val steps = WorkoutStepBuilder.build(durationSec = total, zone = IntensityZone(4), workoutType = WorkoutType.THRESHOLD, discipline = Discipline.RUN)

        val sum = steps.sumOf { (it.durationSec ?: 0) * (it.repeatCount ?: 1) }
        assertThat(sum).isEqualTo(total)
    }

    @Test
    fun `swim sessions get catch-up and single-arm drill blocks before the main set`() {
        val steps = WorkoutStepBuilder.build(durationSec = 40 * 60, zone = IntensityZone(2), workoutType = WorkoutType.EASY, discipline = Discipline.SWIM)

        val types = steps.map { it.stepType }
        assertThat(types.count { it == WorkoutStepType.DRILL }).isEqualTo(2)
        assertThat(types.indexOf(WorkoutStepType.WARMUP)).isLessThan(types.indexOf(WorkoutStepType.DRILL))
        assertThat(types.indexOfFirst { it == WorkoutStepType.DRILL }).isLessThan(types.indexOf(WorkoutStepType.MAIN))
        assertThat(steps.filter { it.stepType == WorkoutStepType.DRILL }.all { it.cueText != null }).isTrue()
    }

    @Test
    fun `swim recovery sessions and very short swim sessions skip drills`() {
        val recovery = WorkoutStepBuilder.build(durationSec = 20 * 60, zone = IntensityZone(1), workoutType = WorkoutType.RECOVERY, discipline = Discipline.SWIM)
        val veryShort = WorkoutStepBuilder.build(durationSec = 15 * 60, zone = IntensityZone(2), workoutType = WorkoutType.EASY, discipline = Discipline.SWIM)

        assertThat(recovery.none { it.stepType == WorkoutStepType.DRILL }).isTrue()
        assertThat(veryShort.none { it.stepType == WorkoutStepType.DRILL }).isTrue()
    }

    @Test
    fun `non-swim sessions never get drill blocks`() {
        val steps = WorkoutStepBuilder.build(durationSec = 40 * 60, zone = IntensityZone(2), workoutType = WorkoutType.EASY, discipline = Discipline.RUN)

        assertThat(steps.none { it.stepType == WorkoutStepType.DRILL }).isTrue()
    }

    @Test
    fun `brick run leg always gets the run-off-the-bike cue regardless of step type`() {
        val steps = WorkoutStepBuilder.build(durationSec = 30 * 60, zone = IntensityZone(4), workoutType = WorkoutType.RACE_PACE, discipline = Discipline.BRICK_RUN)

        assertThat(steps.all { it.cueText != null }).isTrue()
        assertThat(steps.map { it.cueText }.distinct()).hasSize(1)
    }

    @Test
    fun `swim threshold cue names CSS pace only when the profile actually has one`() {
        val withCss = UserZoneProfile(maxHr = 185, cssPaceSecPer100m = 90)
        val withoutCss = UserZoneProfile(maxHr = 185)

        val stepsWithCss = WorkoutStepBuilder.build(
            durationSec = 40 * 60, zone = IntensityZone(4), workoutType = WorkoutType.THRESHOLD, discipline = Discipline.SWIM, profile = withCss,
        )
        val stepsWithoutCss = WorkoutStepBuilder.build(
            durationSec = 40 * 60, zone = IntensityZone(4), workoutType = WorkoutType.THRESHOLD, discipline = Discipline.SWIM, profile = withoutCss,
        )

        val cueWithCss = stepsWithCss.single { it.stepType == WorkoutStepType.INTERVAL }.cueText
        val cueWithoutCss = stepsWithoutCss.single { it.stepType == WorkoutStepType.INTERVAL }.cueText
        assertThat(cueWithCss).contains("CSS")
        assertThat(cueWithoutCss).doesNotContain("CSS")
        assertThat(cueWithoutCss).contains("heart-rate")
    }

    @Test
    fun `bike threshold cue is unaffected by a missing FTP - it never named power to begin with`() {
        val withFtp = UserZoneProfile(maxHr = 185, ftpWatts = 220)
        val withoutFtp = UserZoneProfile(maxHr = 185)

        val stepsWithFtp = WorkoutStepBuilder.build(
            durationSec = 40 * 60, zone = IntensityZone(4), workoutType = WorkoutType.THRESHOLD, discipline = Discipline.BIKE, profile = withFtp,
        )
        val stepsWithoutFtp = WorkoutStepBuilder.build(
            durationSec = 40 * 60, zone = IntensityZone(4), workoutType = WorkoutType.THRESHOLD, discipline = Discipline.BIKE, profile = withoutFtp,
        )

        val cueWithFtp = stepsWithFtp.single { it.stepType == WorkoutStepType.INTERVAL }.cueText
        val cueWithoutFtp = stepsWithoutFtp.single { it.stepType == WorkoutStepType.INTERVAL }.cueText
        assertThat(cueWithFtp).isEqualTo(cueWithoutFtp)
    }
}
