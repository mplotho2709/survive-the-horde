package com.triathlonplanner.data.repository

import com.triathlonplanner.core.database.AdaptationEventDao
import com.triathlonplanner.core.model.AdaptationEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AdaptationEventRepository @Inject constructor(
    private val adaptationEventDao: AdaptationEventDao,
) {
    fun observeAll(): Flow<List<AdaptationEvent>> = adaptationEventDao.observeAll().map { list -> list.map { it.toDomain() } }
}
