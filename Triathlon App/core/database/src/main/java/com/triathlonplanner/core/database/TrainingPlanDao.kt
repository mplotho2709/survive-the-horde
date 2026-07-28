package com.triathlonplanner.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingPlanDao {
    @Query("SELECT * FROM training_plan WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<TrainingPlanEntity?>

    @Query("SELECT * FROM training_plan WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveOnce(): TrainingPlanEntity?

    @Insert
    suspend fun insert(plan: TrainingPlanEntity): Long

    @Query("UPDATE training_plan SET status = :status WHERE id = :planId")
    suspend fun updateStatus(planId: Long, status: String)
}
