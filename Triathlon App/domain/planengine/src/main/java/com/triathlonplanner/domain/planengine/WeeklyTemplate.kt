package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.TrainingAvailability
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.WorkoutType
import kotlin.math.roundToInt

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

    // Peak-week volume (hours) implied by PEAK_LONG_DURATIONS + the other-session fractions below at
    // hoursScale=1.0, hand-traced from sessionsFor's own output - used only to turn a user's target
    // weekly hours into a scale factor relative to what the template already produces.
    private val PEAK_BASELINE_HOURS = mapOf(
        Distance.SPRINT to 4.3,
        Distance.OLYMPIC to 5.2,
        Distance.HALF_IRON to 10.4,
        Distance.FULL_IRON to 14.5,
    )

    // Every generated session is at least this long - a sub-30-minute session isn't useful training.
    private const val MIN_SESSION_DURATION_SEC = 30 * 60

    // Total session length (incl. warmup/cooldown) for standalone quality sessions - THRESHOLD,
    // VO2MAX, TEMPO(sweet-spot), and non-brick RACE_PACE. Unlike aerobic volume, intensity dosing
    // doesn't scale linearly with weekly hours - roughly 20-24min of accumulated near-threshold
    // work or 15-20min of true VO2max work per session is already a full, honest dose for an
    // age-group triathlete. Landed at/above MIN_SESSION_DURATION_SEC so that floor is a
    // non-issue here rather than a silent override.
    private val HIGH_INTENSITY_TOTAL_MINUTES = mapOf(
        TrainingPhase.BUILD to 35,
        TrainingPhase.PEAK to 32,
        TrainingPhase.TAPER to 30,
    )

    // Brick run-off leg specifically - shorter, and physiologically distinct from a standalone
    // quality session (neuromuscular adaptation to running on fatigued legs, not volume).
    private val BRICK_RUN_OFF_TOTAL_MINUTES = mapOf(
        TrainingPhase.BUILD to 30,
        TrainingPhase.PEAK to 35,
    )

    /** Mildly nudged by hoursScale (a much fitter/higher-volume athlete can absorb somewhat more)
     * but clamped to a narrow band so intensity dosing never approaches the aerobic sessions'
     * much wider hoursScale range. */
    private fun boundedHighIntensitySec(baseMinutes: Int, hoursScale: Double): Int =
        (baseMinutes * hoursScale.coerceIn(0.85, 1.25) * 60).roundToInt()

    fun sessionsFor(distance: Distance, phase: TrainingPhase, availability: TrainingAvailability? = null): List<WorkoutSpec> {
        val peak = PEAK_LONG_DURATIONS.getValue(distance)
        val scale = PHASE_SCALE.getValue(phase)
        // Capped generously (not tightly at 1.0x) so a genuinely higher weekly-hours target is
        // actually reachable at peak week rather than being silently truncated; still bounded to
        // keep individual session lengths sane for wildly unrealistic inputs.
        val hoursScale = availability?.let {
            (it.weeklyHoursTarget / PEAK_BASELINE_HOURS.getValue(distance)).coerceIn(0.4, 3.0)
        } ?: 1.0
        val longBikeSec = (peak.bikeMin * scale * hoursScale * 60).toInt()
        val longRunSec = (peak.runMin * scale * hoursScale * 60).toInt()
        val longSwimSec = (peak.swimMin * scale * hoursScale * 60).toInt()
        val otherBikeSec = (longBikeSec * 0.45).toInt().coerceAtLeast(MIN_SESSION_DURATION_SEC)
        val otherRunSec = (longRunSec * 0.55).toInt().coerceAtLeast(MIN_SESSION_DURATION_SEC)
        val otherSwimSec = (longSwimSec * 0.70).toInt().coerceAtLeast(MIN_SESSION_DURATION_SEC)
        val hardSec = HIGH_INTENSITY_TOTAL_MINUTES[phase]?.let { boundedHighIntensitySec(it, hoursScale) } ?: 0
        val brickRunOffSec = BRICK_RUN_OFF_TOTAL_MINUTES[phase]?.let { boundedHighIntensitySec(it, hoursScale) } ?: 0

        val sessions = when (phase) {
            TrainingPhase.RACE_WEEK -> raceWeekSessions(otherSwimSec, otherBikeSec, otherRunSec)
            TrainingPhase.TAPER -> taperSessions(distance, otherSwimSec, otherBikeSec, longRunSec, hardSec)
            TrainingPhase.BASE -> baseSessions(otherSwimSec, otherBikeSec, otherRunSec, longBikeSec, longRunSec)
            TrainingPhase.BUILD -> buildSessions(distance, otherSwimSec, otherBikeSec, longBikeSec, longRunSec, hardSec, brickRunOffSec)
            TrainingPhase.PEAK -> peakSessions(distance, otherSwimSec, longBikeSec, longRunSec, hardSec, brickRunOffSec)
        }.map { it.copy(durationSec = it.durationSec.coerceAtLeast(MIN_SESSION_DURATION_SEC)) }
        return applyDayCap(sessions, availability?.daysPerWeekTarget)
    }

    /** Drops whole days (not individual sessions) when the template uses more distinct days than the
     * user asked for - starting from the least-important day, where a day's importance is its *most*
     * protected session, so a day containing the long ride/brick is never cut. */
    private fun applyDayCap(sessions: List<WorkoutSpec>, daysPerWeekTarget: Int?): List<WorkoutSpec> {
        if (daysPerWeekTarget == null) return sessions
        val dayGroups = sessions.groupBy { it.dayOfWeek }
        val daysToDrop = dayGroups.size - daysPerWeekTarget
        if (daysToDrop <= 0) return sessions
        val dayImportance = dayGroups.mapValues { (_, specs) -> specs.maxOf { DropPriority.rank(it.discipline, it.workoutType) } }
        val dropDays = dayGroups.keys.sortedBy { dayImportance.getValue(it) }.take(daysToDrop).toSet()
        return sessions.filterNot { it.dayOfWeek in dropDays }
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
        longBikeSec: Int,
        longRunSec: Int,
        hardSec: Int,
        brickRunOffSec: Int,
    ): List<WorkoutSpec> = listOf(
        WorkoutSpec(1, Discipline.SWIM, WorkoutType.EASY, swimSec, zone = IntensityZone(2)),
        // Zone 4 = "Threshold" per this app's own 5-zone labels - a session literally named
        // THRESHOLD belongs there, not in Zone 3 ("Tempo"), which was inflating the grey zone.
        WorkoutSpec(3, Discipline.SWIM, WorkoutType.THRESHOLD, hardSec, zone = IntensityZone(4)),
        WorkoutSpec(2, Discipline.BIKE, WorkoutType.EASY, bikeSec, zone = IntensityZone(2)),
        WorkoutSpec(4, Discipline.STRENGTH, WorkoutType.STRENGTH_SESSION, 40 * 60),
        WorkoutSpec(5, Discipline.RUN, WorkoutType.THRESHOLD, hardSec, zone = IntensityZone(4)),
        // The long ride *is* the brick's bike leg. Two reasons this isn't the Zone 3 tempo block it
        // used to be: (1) longBikeSec was previously accepted and never used, so the Build phase
        // shipped with no long ride at all - the single most important aerobic session in
        // triathlon; (2) running off a long aerobic ride is the specific adaptation a brick exists
        // to train, and doing it off a grey-zone tempo effort buys fatigue instead.
        WorkoutSpec(6, Discipline.BRICK_BIKE, WorkoutType.LONG, longBikeSec, zone = IntensityZone(2), isBrickLeg = true, sortOrderInDay = 0),
        WorkoutSpec(6, Discipline.BRICK_RUN, WorkoutType.RACE_PACE, brickRunOffSec, zone = raceZoneFor(distance), isBrickLeg = true, sortOrderInDay = 1),
        WorkoutSpec(7, Discipline.RUN, WorkoutType.LONG, longRunSec, zone = IntensityZone(1)),
    )

    private fun peakSessions(
        distance: Distance,
        swimSec: Int,
        longBikeSec: Int,
        longRunSec: Int,
        hardSec: Int,
        brickRunOffSec: Int,
    ): List<WorkoutSpec> {
        val raceZone = raceZoneFor(distance)
        return listOf(
            WorkoutSpec(1, Discipline.SWIM, WorkoutType.EASY, swimSec, zone = IntensityZone(2)),
            // Swim race pace tracks the actual race zone like every other discipline; it was
            // previously pinned to Zone 3 regardless of distance, which both contradicted the
            // run/brick legs of the same race and parked volume in the grey zone.
            WorkoutSpec(3, Discipline.SWIM, WorkoutType.RACE_PACE, hardSec, zone = raceZone),
            WorkoutSpec(2, Discipline.BIKE, WorkoutType.VO2MAX, hardSec, zone = IntensityZone(5)),
            WorkoutSpec(6, Discipline.BRICK_BIKE, WorkoutType.RACE_PACE, hardSec, zone = raceZone, isBrickLeg = true, sortOrderInDay = 0),
            WorkoutSpec(6, Discipline.BRICK_RUN, WorkoutType.RACE_PACE, brickRunOffSec, zone = raceZone, isBrickLeg = true, sortOrderInDay = 1),
            WorkoutSpec(7, Discipline.BIKE, WorkoutType.LONG, longBikeSec, zone = IntensityZone(2)),
            // Restores the long run, which Peak previously dropped entirely - leaving the phase
            // with a long ride but no long run, and five separate quality sessions in one week.
            // The standalone race-pace run it replaces is already covered by the brick's run leg.
            WorkoutSpec(5, Discipline.RUN, WorkoutType.LONG, longRunSec, zone = IntensityZone(1)),
        )
    }

    private fun taperSessions(distance: Distance, swimSec: Int, bikeSec: Int, longRunSec: Int, hardSec: Int): List<WorkoutSpec> = listOf(
        WorkoutSpec(1, Discipline.SWIM, WorkoutType.EASY, (swimSec * 0.6).toInt(), zone = IntensityZone(1)),
        WorkoutSpec(3, Discipline.BIKE, WorkoutType.EASY, (bikeSec * 0.6).toInt(), zone = IntensityZone(2)),
        // Taper's job is to hold race feel while shedding fatigue, so this session must sit at the
        // athlete's *actual* race intensity. Pinning it to Zone 3 regardless of distance parked
        // taper volume in the grey zone - the one place a polarized plan least wants it.
        WorkoutSpec(4, Discipline.RUN, WorkoutType.RACE_PACE, hardSec, zone = raceZoneFor(distance)),
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
