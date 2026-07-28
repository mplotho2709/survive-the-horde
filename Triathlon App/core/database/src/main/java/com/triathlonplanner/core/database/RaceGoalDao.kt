package com.triathlonplanner.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RaceGoalDao {
    @Query("SELECT * FROM race_goal WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<RaceGoalEntity?>

    @Query("SELECT * FROM race_goal WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveOnce(): RaceGoalEntity?

    @Insert
    suspend fun insert(raceGoal: RaceGoalEntity): Long

    @Query("UPDATE race_goal SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAll()
}
