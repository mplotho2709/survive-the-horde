package com.triathlonplanner.data.repository

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.RaceGoal
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.core.model.WorkoutType
import org.junit.Test
import java.time.LocalDate

class RacePaceResolverTest {

    private val profile = UserZoneProfile(
        maxHr = 185,
        restingHr = 55,
        ftpWatts = 250,
        cssPaceSecPer100m = 95,
        thresholdRunPaceSecPerKm = 270,
    )

    private fun goal(distance: Distance, targetFinishTimeSec: Int? = null) = RaceGoal(
        distance = distance,
        raceDate = LocalDate.of(2026, 9, 12),
        targetFinishTimeSec = targetFinishTimeSec,
    )

    @Test
    fun `bike race pace is shown in watts, not as a zone`() {
        val prescription = RacePaceResolver.resolve(
            Discipline.BIKE,
            WorkoutType.RACE_PACE,
            goal(Distance.FULL_IRON),
            profile,
        )

        // 0.68-0.76 of a 250 W FTP - the number the plan engine already knew and the UI never said.
        assertThat(prescription?.value).isEqualTo("170-190")
        assertThat(prescription?.unit).isEqualTo("W")
        assertThat(prescription?.caption).contains("68-76% of your 250 W FTP")
        assertThat(prescription?.shortLabel).isEqualTo("Race target 170-190 W")
    }

    @Test
    fun `brick bike legs get the same target as a standalone bike leg`() {
        val standalone = RacePaceResolver.resolve(Discipline.BIKE, WorkoutType.RACE_PACE, goal(Distance.OLYMPIC), profile)
        val brick = RacePaceResolver.resolve(Discipline.BRICK_BIKE, WorkoutType.RACE_PACE, goal(Distance.OLYMPIC), profile)

        assertThat(brick).isEqualTo(standalone)
    }

    @Test
    fun `swim race pace is expressed per 100m and related to CSS`() {
        val prescription = RacePaceResolver.resolve(
            Discipline.SWIM,
            WorkoutType.RACE_PACE,
            goal(Distance.HALF_IRON),
            profile,
        )!!

        assertThat(prescription.unit).isEqualTo("/100m")
        assertThat(prescription.caption).contains("1:35 CSS")
        assertThat(prescription.caption).contains("1900 m swim")
    }

    @Test
    fun `a stated goal time makes the run caption cite the goal rather than threshold pace`() {
        val withGoal = RacePaceResolver.resolve(
            Discipline.RUN,
            WorkoutType.RACE_PACE,
            goal(Distance.OLYMPIC, targetFinishTimeSec = 150 * 60),
            profile,
        )!!
        val withoutGoal = RacePaceResolver.resolve(
            Discipline.RUN,
            WorkoutType.RACE_PACE,
            goal(Distance.OLYMPIC),
            profile,
        )!!

        assertThat(withGoal.caption).contains("2:30")
        assertThat(withoutGoal.caption).contains("threshold pace")
        assertThat(withGoal.value).isNotEqualTo(withoutGoal.value)
    }

    @Test
    fun `only the brick run leg is described as run off the bike`() {
        val standalone = RacePaceResolver.resolve(Discipline.RUN, WorkoutType.RACE_PACE, goal(Distance.OLYMPIC), profile)!!
        val brick = RacePaceResolver.resolve(Discipline.BRICK_RUN, WorkoutType.RACE_PACE, goal(Distance.OLYMPIC), profile)!!

        assertThat(standalone.caption).doesNotContain("off the bike")
        assertThat(brick.caption).contains("off the bike")
        // Same prescribed pace either way - only the wording differs.
        assertThat(brick.value).isEqualTo(standalone.value)
    }

    @Test
    fun `run pace ranges read fast-to-slow`() {
        val prescription = RacePaceResolver.resolve(
            Discipline.BRICK_RUN,
            WorkoutType.RACE_PACE,
            goal(Distance.SPRINT),
            profile,
        )!!

        assertThat(prescription.unit).isEqualTo("/km")
        val (fast, slow) = prescription.value.split("-")
        assertThat(paceToSeconds(fast)).isLessThan(paceToSeconds(slow))
    }

    @Test
    fun `only race-pace sessions get a target`() {
        for (type in WorkoutType.entries.filter { it != WorkoutType.RACE_PACE }) {
            assertThat(RacePaceResolver.resolve(Discipline.BIKE, type, goal(Distance.OLYMPIC), profile)).isNull()
        }
    }

    @Test
    fun `missing inputs yield no prescription rather than an invented one`() {
        val bare = UserZoneProfile(maxHr = 185)

        assertThat(RacePaceResolver.resolve(Discipline.BIKE, WorkoutType.RACE_PACE, goal(Distance.OLYMPIC), null)).isNull()
        assertThat(RacePaceResolver.resolve(Discipline.BIKE, WorkoutType.RACE_PACE, null, profile)).isNull()
        assertThat(RacePaceResolver.resolve(Discipline.BIKE, WorkoutType.RACE_PACE, goal(Distance.OLYMPIC), bare)).isNull()
        assertThat(RacePaceResolver.resolve(Discipline.SWIM, WorkoutType.RACE_PACE, goal(Distance.OLYMPIC), bare)).isNull()
    }

    @Test
    fun `an unpaceable discipline has no race target`() {
        assertThat(RacePaceResolver.resolve(Discipline.STRENGTH, WorkoutType.RACE_PACE, goal(Distance.OLYMPIC), profile)).isNull()
        assertThat(RacePaceResolver.resolve(Discipline.REST, WorkoutType.RACE_PACE, goal(Distance.OLYMPIC), profile)).isNull()
    }

    @Test
    fun `an impossible goal time falls back to the threshold band instead of vanishing`() {
        // 30 minutes for an Olympic race leaves negative time for the run; the athlete should still
        // see a runnable target rather than an empty card.
        val prescription = RacePaceResolver.resolve(
            Discipline.RUN,
            WorkoutType.RACE_PACE,
            goal(Distance.OLYMPIC, targetFinishTimeSec = 30 * 60),
            profile,
        )!!

        assertThat(prescription.caption).contains("threshold pace")
        assertThat(prescription.caption).contains("doesn't pin down a raceable run pace")
    }

    @Test
    fun `a goal too loose to constrain the run never prescribes an unrunnable pace`() {
        // 2h30 for a sprint: the run leg has hours of slack, so the goal-implied pace is slower
        // than walking. The card must not read "hold 19:26/km".
        val prescription = RacePaceResolver.resolve(
            Discipline.RUN,
            WorkoutType.RACE_PACE,
            goal(Distance.SPRINT, targetFinishTimeSec = 150 * 60),
            profile,
        )!!

        val slowest = prescription.value.split("-").last()
        assertThat(paceToSeconds(slowest)).isLessThan(6 * 60)
        assertThat(prescription.caption).contains("threshold pace")
    }

    private fun paceToSeconds(pace: String): Int {
        val (minutes, seconds) = pace.split(":")
        return minutes.toInt() * 60 + seconds.toInt()
    }
}
