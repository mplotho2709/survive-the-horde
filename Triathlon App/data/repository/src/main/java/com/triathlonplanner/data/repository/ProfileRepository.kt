package com.triathlonplanner.data.repository

import com.triathlonplanner.core.database.UserProfileDao
import com.triathlonplanner.core.model.UserZoneProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class ProfileRepository @Inject constructor(
    private val userProfileDao: UserProfileDao,
) {
    fun observeProfile(): Flow<UserZoneProfile?> = userProfileDao.observe().map { it?.toDomain() }

    suspend fun getProfileOnce(): UserZoneProfile? = userProfileDao.getOnce()?.toDomain()

    suspend fun saveProfile(profile: UserZoneProfile) {
        userProfileDao.upsert(profile.toEntity(Instant.now()))
    }
}
