package com.triathlonplanner.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PlannedWorkoutWithWeekIndex(
    @Embedded val workout: PlannedWorkoutEntity,
    val weekIndex: Int,
)

@Dao
interface PlannedWorkoutDao {
    @Insert
    suspend fun insertAll(workouts: List<PlannedWorkoutEntity>): List<Long>

    @Query(
        "SELECT pw.*, wk.weekIndex as weekIndex FROM planned_workout pw " +
            "INNER JOIN plan_week wk ON pw.weekId = wk.id " +
            "WHERE wk.planId = :planId ORDER BY pw.dateEpochDay, pw.sortOrderInDay",
    )
    fun observeForPlan(planId: Long): Flow<List<PlannedWorkoutWithWeekIndex>>

    @Query("SELECT * FROM planned_workout WHERE weekId = :weekId ORDER BY dateEpochDay, sortOrderInDay")
    fun observeForWeek(weekId: Long): Flow<List<PlannedWorkoutEntity>>

    // Scoped to the ACTIVE plan only - without this, a cancelled/abandoned plan's workouts for
    // today's calendar date would keep showing up alongside the new plan's, since dateEpochDay
    // ranges from different plans can (and often do) overlap.
    @Query(
        "SELECT pw.* FROM planned_workout pw " +
            "INNER JOIN plan_week wk ON pw.weekId = wk.id " +
            "INNER JOIN training_plan tp ON wk.planId = tp.id " +
            "WHERE tp.status = 'ACTIVE' AND pw.dateEpochDay = :dateEpochDay ORDER BY pw.sortOrderInDay",
    )
    fun observeForDate(dateEpochDay: Long): Flow<List<PlannedWorkoutEntity>>

    @Query(
        "SELECT pw.* FROM planned_workout pw " +
            "INNER JOIN plan_week wk ON pw.weekId = wk.id " +
            "WHERE wk.planId = :planId ORDER BY pw.dateEpochDay, pw.sortOrderInDay",
    )
    suspend fun getForPlanOnce(planId: Long): List<PlannedWorkoutEntity>

    @Query("UPDATE planned_workout SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE planned_workout SET status = :status, substitutedDiscipline = :substitutedDiscipline WHERE id = :id")
    suspend fun updateStatusWithSubstitution(id: Long, status: String, substitutedDiscipline: String)

    /** Volume-only retune from SessionRebalancer - deliberately cannot touch workoutType or zone. */
    @Query("UPDATE planned_workout SET plannedDurationSec = :durationSec, plannedLoad = :load WHERE id = :id")
    suspend fun updateDurationAndLoad(id: Long, durationSec: Int, load: Int)

    @Query("UPDATE planned_workout SET workoutType = :workoutType, zoneLevel = :zoneLevel WHERE id = :id")
    suspend fun updateWorkoutTypeAndZone(id: Long, workoutType: String, zoneLevel: Int?)

    /** Needed for AdaptationEngine's PlannedWorkoutSnapshot, which carries weekIndex directly. */
    @Query(
        "SELECT pw.*, wk.weekIndex as weekIndex FROM planned_workout pw " +
            "INNER JOIN plan_week wk ON pw.weekId = wk.id " +
            "WHERE wk.planId = :planId ORDER BY pw.dateEpochDay, pw.sortOrderInDay",
    )
    suspend fun getForPlanWithWeekIndex(planId: Long): List<PlannedWorkoutWithWeekIndex>

    @Query("SELECT * FROM planned_workout WHERE id = :id")
    suspend fun getById(id: Long): PlannedWorkoutEntity?

    @Query("SELECT * FROM planned_workout WHERE id = :id")
    fun observeById(id: Long): Flow<PlannedWorkoutEntity?>
}
