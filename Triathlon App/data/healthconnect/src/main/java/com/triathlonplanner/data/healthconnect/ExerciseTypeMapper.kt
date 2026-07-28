package com.triathlonplanner.data.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.triathlonplanner.core.model.Discipline

/** Maps Health Connect's exercise type constants to our simplified Discipline set. Returns null for types we don't train (yoga, etc.) - those are simply not synced. */
fun mapExerciseTypeToDiscipline(exerciseType: Int): Discipline? = when (exerciseType) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    -> Discipline.RUN

    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    -> Discipline.BIKE

    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    -> Discipline.SWIM

    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
    -> Discipline.STRENGTH

    else -> null
}
