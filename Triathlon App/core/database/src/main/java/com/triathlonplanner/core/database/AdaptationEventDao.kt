package com.triathlonplanner.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdaptationEventDao {
    @Insert
    suspend fun insertAll(events: List<AdaptationEventEntity>)

    @Query("SELECT * FROM adaptation_event ORDER BY timestampEpochMilli DESC")
    fun observeAll(): Flow<List<AdaptationEventEntity>>
}
