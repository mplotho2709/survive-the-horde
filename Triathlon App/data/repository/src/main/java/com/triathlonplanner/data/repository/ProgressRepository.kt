package com.triathlonplanner.data.repository

import com.triathlonplanner.core.database.CompletedActivityDao
import com.triathlonplanner.core.model.WeeklyLoadPoint
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class ProgressRepository @Inject constructor(
    private val planRepository: PlanRepository,
    private val completedActivityDao: CompletedActivityDao,
) {
    suspend fun getWeeklyLoadHistory(planId: Long): List<WeeklyLoadPoint> {
        val weeks = planRepository.observeWeeksForPlan(planId).first()
        return weeks.sortedBy { it.weekIndex }.map { week ->
            val weekStartMilli = week.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val weekEndMilli = Instant.ofEpochMilli(weekStartMilli).plus(7, ChronoUnit.DAYS).toEpochMilli()
            val completedLoad = completedActivityDao.sumLoadInRange(weekStartMilli, weekEndMilli)
            WeeklyLoadPoint(week.weekIndex, week.plannedWeeklyLoad, completedLoad)
        }
    }
}
