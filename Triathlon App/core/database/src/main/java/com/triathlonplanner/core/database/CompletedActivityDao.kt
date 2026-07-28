package com.triathlonplanner.core.database

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletedActivityDao {
    /** REPLACE on the unique healthConnectRecordId index is the sync dedupe mechanism. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(activities: List<CompletedActivityEntity>)

    @Query("SELECT * FROM completed_activity WHERE startTimeEpochMilli >= :sinceEpochMilli ORDER BY startTimeEpochMilli")
    fun observeSince(sinceEpochMilli: Long): Flow<List<CompletedActivityEntity>>

    @Query("SELECT * FROM completed_activity WHERE startTimeEpochMilli >= :sinceEpochMilli ORDER BY startTimeEpochMilli")
    suspend fun getSinceOnce(sinceEpochMilli: Long): List<CompletedActivityEntity>

    @Query("UPDATE completed_activity SET matchedPlannedWorkoutId = :workoutId, matchStatus = :matchStatus WHERE id = :id")
    suspend fun updateMatch(id: Long, workoutId: Long?, matchStatus: String)

    @Query(
        "SELECT COALESCE(SUM(calculatedLoad), 0) FROM completed_activity " +
            "WHERE startTimeEpochMilli >= :startEpochMilli AND startTimeEpochMilli < :endEpochMilli",
    )
    suspend fun sumLoadInRange(startEpochMilli: Long, endEpochMilli: Long): Int

    @Query("SELECT * FROM completed_activity WHERE startTimeEpochMilli >= :startEpochMilli AND startTimeEpochMilli < :endEpochMilli")
    suspend fun getInRange(startEpochMilli: Long, endEpochMilli: Long): List<CompletedActivityEntity>
}
