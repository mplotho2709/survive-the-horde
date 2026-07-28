package com.triathlonplanner.core.model

enum class Discipline {
    SWIM,
    BIKE,
    RUN,
    STRENGTH,
    BRICK_BIKE,
    BRICK_RUN,
    REST,
}

enum class TrainingPhase {
    BASE,
    BUILD,
    PEAK,
    TAPER,
    RACE_WEEK,
}

enum class WorkoutType {
    EASY,
    TEMPO,
    THRESHOLD,
    VO2MAX,
    LONG,
    RACE_PACE,
    FTP_TEST,
    CSS_TEST,
    RECOVERY,
    STRENGTH_SESSION,
    REST,
}

enum class WorkoutStatus {
    PLANNED,
    COMPLETED,
    MISSED,
    MODIFIED,
    SKIPPED_BY_ADAPTATION,
}

enum class WorkoutStepType {
    WARMUP,
    MAIN,
    INTERVAL,
    RECOVERY,
    COOLDOWN,
}

enum class PlanStatus {
    ACTIVE,
    COMPLETED,
    ABANDONED,
}

enum class MatchStatus {
    MATCHED,
    UNMATCHED_EXTRA,
    MISSED_NO_ACTIVITY,
}

enum class FtpSource {
    MANUAL,
    FIELD_TEST_20MIN,
    RAMP_TEST,
}

enum class CssSource {
    MANUAL,
    FIELD_TEST,
}

enum class AdaptationTriggerType {
    ISOLATED_MISS,
    CONSECUTIVE_MISSES,
    EXTENDED_ABSENCE,
    OVERREACH,
    UNDERREACH,
}

enum class AdaptationAction {
    NO_ACTION,
    DROP_SESSION,
    REBASELINE_NEXT_WEEK,
    INSERT_RE_ENTRY_BLOCK,
    DOWNGRADE_TO_RECOVERY,
    DOWNGRADE_TO_REST,
    SOFTEN_CUTBACK,
    EXTEND_BUILD_BLOCK,
}

/**
 * Relative intensity zone, 1-indexed. Meaning is discipline-dependent: for BIKE it maps to the
 * 7-zone Coggan power model, for HR-based prescriptions it maps to a 5-zone model, and for SWIM
 * (pace/CSS-based) it maps to a 5-zone pace model. Kept generic here so PlannedWorkout/WorkoutStep
 * don't need three parallel zone types; ZoneCalculator resolves the concrete meaning.
 */
@JvmInline
value class IntensityZone(val level: Int) {
    init {
        require(level in 1..7) { "Intensity zone must be between 1 and 7, was $level" }
    }
}
