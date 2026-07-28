package com.triathlonplanner.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RollingLoadStateDao {
    @Query("SELECT * FROM rolling_load_state WHERE planId = :planId LIMIT 1")
    suspend fun getForPlan(planId: Long): RollingLoadStateEntity?

    @Upsert
    suspend fun upsert(state: RollingLoadStateEntity)
}
