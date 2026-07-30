package com.triathlonplanner.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutStepDao {
    @Insert
    suspend fun insertAll(steps: List<WorkoutStepEntity>): List<Long>

    @Query("DELETE FROM workout_step WHERE plannedWorkoutId = :workoutId")
    suspend fun deleteForWorkout(workoutId: Long)

    @Query("SELECT * FROM workout_step WHERE plannedWorkoutId = :workoutId ORDER BY stepOrder")
    fun observeForWorkout(workoutId: Long): Flow<List<WorkoutStepEntity>>
}
