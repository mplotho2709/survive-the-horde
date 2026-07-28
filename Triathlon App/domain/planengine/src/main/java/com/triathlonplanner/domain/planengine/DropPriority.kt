package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.WorkoutType

/** Shared drop-priority ordering used both by [AdaptationEngine]'s "drop a lower-priority session"
 * rule and by [WeeklyTemplate]'s days-per-week cap. Lower rank drops first: Strength > secondary
 * quality > long/key session > brick legs. */
object DropPriority {
    fun rank(discipline: Discipline, workoutType: WorkoutType): Int = when {
        discipline == Discipline.BRICK_BIKE || discipline == Discipline.BRICK_RUN -> 5
        workoutType == WorkoutType.LONG || workoutType == WorkoutType.RACE_PACE -> 4
        workoutType in setOf(WorkoutType.TEMPO, WorkoutType.THRESHOLD, WorkoutType.VO2MAX, WorkoutType.CSS_TEST, WorkoutType.FTP_TEST) -> 3
        discipline == Discipline.STRENGTH -> 1
        else -> 2
    }

    fun rank(w: PlannedWorkoutSnapshot): Int = rank(w.discipline, w.workoutType)
}
