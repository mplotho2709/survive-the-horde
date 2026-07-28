package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.WorkoutType

/** A single session, positioned within its week but not yet dated (PlanGenerator assigns dates). */
data class WorkoutSpec(
    val dayOfWeek: Int, // 1=Monday .. 7=Sunday, per java.time.DayOfWeek
    val discipline: Discipline,
    val workoutType: WorkoutType,
    val durationSec: Int,
    val distanceM: Int? = null,
    val zone: IntensityZone? = null,
    val isBrickLeg: Boolean = false,
    val sortOrderInDay: Int = 0,
)

/**
 * Per-distance, per-phase session templates. Session *counts* are the same representative
 * numbers across all four distances (2 swim / 3 bike / 3 run / 1 strength, +1 brick in
 * Build/Peak) - only durations scale by distance and phase. This is a deliberate simplification:
 * a fully precise plan would vary counts per distance too, but the count template captures the
 * polarized-intensity and periodization structure that matters most for correctness.
 */
object WeeklyTemplate {

    private data class LongDurations(val bikeMin: Int, val runMin: Int, val swimMin: Int)

    // Peak-week long-session durations per distance; Base/Build are scaled fractions of these.
    private val PEAK_LONG_DURATIONS = mapOf(
        Distance.SPRINT to LongDurations(bikeMin = 75, runMin = 50, swimMin = 40),
        Distance.OLYMPIC to LongDurations(bikeMin = 120, runMin = 70, swimMin = 45),
        Distance.HALF_IRON to LongDurations(bikeMin = 210, runMin = 110, swimMin = 60),
        Distance.FULL_IRON to LongDurations(bikeMin = 300, runMin = 150, swimMin = 75),
    )

    private val PHASE_SCALE = mapOf(
        TrainingPhase.BASE to 0.55,
        TrainingPhase.BUILD to 0.80,
        TrainingPhase.PEAK to 1.00,
        TrainingPhase.TAPER to 0.45,
        TrainingPhase.RACE_WEEK to 0.20,
    )

    fun sessionsFor(distance: Distance, phase: TrainingPhase): List<WorkoutSpec> {
        val peak = PEAK_LONG_DURATIONS.getValue(distance)
        val scale = PHASE_SCALE.getValue(phase)
        val longBikeSec = (peak.bikeMin * scale * 60).toInt()
        val longRunSec = (peak.runMin * scale * 60).toInt()
        val longSwimSec = (peak.swimMin * scale * 60).toInt()
        val otherBikeSec = (longBikeSec * 0.45).toInt().coerceAtLeast(20 * 60)
        val otherRunSec = (longRunSec * 0.55).toInt().coerceAtLeast(20 * 60)
        val otherSwimSec = (longSwimSec * 0.70).toInt().coerceAtLeast(20 * 60)

        return when (phase) {
            TrainingPhase.RACE_WEEK -> raceWeekSessions(otherSwimSec, otherBikeSec, otherRunSec)
            TrainingPhase.TAPER -> taperSessions(otherSwimSec, otherBikeSec, otherRunSec, longRunSec)
            TrainingPhase.BASE -> baseSessions(otherSwimSec, otherBikeSec, otherRunSec, longBikeSec, longRunSec)
            TrainingPhase.BUILD -> buildSessions(distance, otherSwimSec, otherBikeSec, otherRunSec, longBikeSec, longRunSec)
            TrainingPhase.PEAK -> peakSessions(distance, otherSwimSec, otherBikeSec, otherRunSec, longBikeSec, longRunSec)
        }
    }

    private fun baseSessions(
        swimSec: Int,
        bikeSec: Int,
        runSec: Int,
        longBikeSec: Int,
        longRunSec: Int,
    ): List<WorkoutSpec> = listOf(
        WorkoutSpec(1, Discipline.SWIM, WorkoutType.EASY, swimSec, zone = IntensityZone(1)),
        WorkoutSpec(3, Discipline.SWIM, WorkoutType.EASY, swimSec, zone = IntensityZone(2)),
        WorkoutSpec(2, Discipline.BIKE, WorkoutType.EASY, bikeSec, zone = IntensityZone(2)),
        WorkoutSpec(4, Discipline.STRENGTH, WorkoutType.STRENGTH_SESSION, 40 * 60),
        WorkoutSpec(5, Discipline.RUN, WorkoutType.EASY, runSec, zone = IntensityZone(1)),
        WorkoutSpec(6, Discipline.BIKE, WorkoutType.LONG, longBikeSec, zone = IntensityZone(2)),
        WorkoutSpec(7, Discipline.RUN, WorkoutType.LONG, longRunSec, zone = IntensityZone(1)),
    )

    private fun buildSessions(
        distance: Distance,
        swimSec: Int,
        bikeSec: Int,
        runSec: Int,
        longBikeSec: Int,
        longRunSec: Int,
    ): List<WorkoutSpec> {
        val brickBikeSec = (longBikeSec * 0.5).toInt()
        val brickRunSec = (longRunSec * 0.4).toInt().coerceAtLeast(15 * 60)
        return listOf(
            WorkoutSpec(1, Discipline.SWIM, WorkoutType.EASY, swimSec, zone = IntensityZone(2)),
            WorkoutSpec(3, Discipline.SWIM, WorkoutType.THRESHOLD, swimSec, zone = IntensityZone(3)),
            WorkoutSpec(2, Discipline.BIKE, WorkoutType.EASY, bikeSec, zone = IntensityZone(2)),
            WorkoutSpec(4, Discipline.STRENGTH, WorkoutType.STRENGTH_SESSION, 40 * 60),
            WorkoutSpec(5, Discipline.RUN, WorkoutType.THRESHOLD, runSec, zone = IntensityZone(4)),
            WorkoutSpec(6, Discipline.BRICK_BIKE, WorkoutType.TEMPO, brickBikeSec, zone = IntensityZone(3), isBrickLeg = true, sortOrderInDay = 0),
            WorkoutSpec(6, Discipline.BRICK_RUN, WorkoutType.RACE_PACE, brickRunSec, zone = raceZoneFor(distance), isBrickLeg = true, sortOrderInDay = 1),
            WorkoutSpec(7, Discipline.RUN, WorkoutType.LONG, longRunSec, zone = IntensityZone(1)),
        )
    }

    private fun peakSessions(
        distance: Distance,
        swimSec: Int,
        bikeSec: Int,
        runSec: Int,
        longBikeSec: Int,
        longRunSec: Int,
    ): List<WorkoutSpec> {
        val brickBikeSec = (longBikeSec * 0.6).toInt()
        val brickRunSec = (longRunSec * 0.45).toInt().coerceAtLeast(15 * 60)
        val raceZone = raceZoneFor(distance)
        return listOf(
            WorkoutSpec(1, Discipline.SWIM, WorkoutType.EASY, swimSec, zone = IntensityZone(2)),
            WorkoutSpec(3, Discipline.SWIM, WorkoutType.RACE_PACE, swimSec, zone = IntensityZone(3)),
            WorkoutSpec(2, Discipline.BIKE, WorkoutType.VO2MAX, bikeSec, zone = IntensityZone(5)),
            WorkoutSpec(5, Discipline.RUN, WorkoutType.RACE_PACE, runSec, zone = raceZone),
            WorkoutSpec(6, Discipline.BRICK_BIKE, WorkoutType.RACE_PACE, brickBikeSec, zone = raceZone, isBrickLeg = true, sortOrderInDay = 0),
            WorkoutSpec(6, Discipline.BRICK_RUN, WorkoutType.RACE_PACE, brickRunSec, zone = raceZone, isBrickLeg = true, sortOrderInDay = 1),
            WorkoutSpec(7, Discipline.BIKE, WorkoutType.LONG, longBikeSec, zone = IntensityZone(2)),
        )
    }

    private fun taperSessions(swimSec: Int, bikeSec: Int, runSec: Int, longRunSec: Int): List<WorkoutSpec> = listOf(
        WorkoutSpec(1, Discipline.SWIM, WorkoutType.EASY, (swimSec * 0.6).toInt(), zone = IntensityZone(1)),
        WorkoutSpec(3, Discipline.BIKE, WorkoutType.EASY, (bikeSec * 0.6).toInt(), zone = IntensityZone(2)),
        WorkoutSpec(4, Discipline.RUN, WorkoutType.RACE_PACE, (runSec * 0.4).toInt(), zone = IntensityZone(3)),
        WorkoutSpec(6, Discipline.BIKE, WorkoutType.EASY, (bikeSec * 0.5).toInt(), zone = IntensityZone(1)),
        WorkoutSpec(7, Discipline.RUN, WorkoutType.EASY, (longRunSec * 0.35).toInt(), zone = IntensityZone(1)),
    )

    private fun raceWeekSessions(swimSec: Int, bikeSec: Int, runSec: Int): List<WorkoutSpec> = listOf(
        WorkoutSpec(1, Discipline.SWIM, WorkoutType.RECOVERY, (swimSec * 0.4).toInt(), zone = IntensityZone(1)),
        WorkoutSpec(3, Discipline.BIKE, WorkoutType.RECOVERY, (bikeSec * 0.35).toInt(), zone = IntensityZone(1)),
        WorkoutSpec(4, Discipline.RUN, WorkoutType.RECOVERY, (runSec * 0.3).toInt(), zone = IntensityZone(1)),
    )

    /** Peak/race-pace zone: genuinely hard (Z4) for short course, aerobic endurance (Z2) for long course. */
    private fun raceZoneFor(distance: Distance): IntensityZone = when (distance) {
        Distance.SPRINT, Distance.OLYMPIC -> IntensityZone(4)
        Distance.HALF_IRON, Distance.FULL_IRON -> IntensityZone(2)
    }
}
