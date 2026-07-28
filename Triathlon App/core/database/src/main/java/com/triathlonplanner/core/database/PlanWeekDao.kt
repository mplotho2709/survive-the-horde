package com.triathlonplanner.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanWeekDao {
    @Insert
    suspend fun insertAll(weeks: List<PlanWeekEntity>): List<Long>

    @Query("SELECT * FROM plan_week WHERE planId = :planId ORDER BY weekIndex")
    fun observeForPlan(planId: Long): Flow<List<PlanWeekEntity>>

    @Query("SELECT * FROM plan_week WHERE planId = :planId AND weekIndex = :weekIndex LIMIT 1")
    suspend fun getByIndex(planId: Long, weekIndex: Int): PlanWeekEntity?

    @Query("UPDATE plan_week SET plannedWeeklyLoad = :newLoad WHERE planId = :planId AND weekIndex = :weekIndex")
    suspend fun updatePlannedLoad(planId: Long, weekIndex: Int, newLoad: Int)
}
