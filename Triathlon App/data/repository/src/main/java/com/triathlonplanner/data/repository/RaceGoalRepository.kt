package com.triathlonplanner.data.repository

import com.triathlonplanner.core.database.RaceGoalDao
import com.triathlonplanner.core.model.RaceGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RaceGoalRepository @Inject constructor(
    private val raceGoalDao: RaceGoalDao,
) {
    fun observeActive(): Flow<RaceGoal?> = raceGoalDao.observeActive().map { it?.toDomain() }

    suspend fun getActiveOnce(): RaceGoal? = raceGoalDao.getActiveOnce()?.toDomain()

    /** Deactivates any existing goal first - only one active race goal at a time in v1. */
    suspend fun setActiveGoal(goal: RaceGoal): Long {
        raceGoalDao.deactivateAll()
        return raceGoalDao.insert(goal.toEntity(isActive = true, createdAt = Instant.now()))
    }

    suspend fun deactivateActiveGoal() = raceGoalDao.deactivateAll()
}
