package com.triathlonplanner.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the Room schema itself (foreign keys, indices, join queries) actually works at
 * runtime, in-JVM via Robolectric - no emulator needed. Compile-time KSP success only proves the
 * annotations were well-formed, not that the SQL is valid.
 *
 * Pinned to API 36: Robolectric hasn't shadowed API 37 yet, independent of this module's real
 * compileSdk/targetSdk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `user profile upsert and observe round-trips`() = runBlocking {
        val profile = UserProfileEntity(
            maxHr = 190,
            restingHr = 50,
            ftpWatts = 250,
            ftpSource = "MANUAL",
            cssPaceSecPer100m = null,
            cssSource = null,
            weightKg = 75.0,
            updatedAtEpochMilli = 1000L,
        )

        db.userProfileDao().upsert(profile)
        val loaded = db.userProfileDao().observe().first()

        assertThat(loaded?.maxHr).isEqualTo(190)
        assertThat(loaded?.ftpWatts).isEqualTo(250)
    }

    @Test
    fun `full plan hierarchy inserts and joins correctly`() = runBlocking {
        val raceGoalId = db.raceGoalDao().insert(
            RaceGoalEntity(distance = "OLYMPIC", raceDateEpochDay = 20000L, raceName = null, targetFinishTimeSec = null, isActive = true, createdAtEpochMilli = 0L),
        )
        val planId = db.trainingPlanDao().insert(
            TrainingPlanEntity(raceGoalId = raceGoalId, startDateEpochDay = 19000L, totalWeeks = 16, status = "ACTIVE", generatedAtEpochMilli = 0L),
        )
        val weekIds = db.planWeekDao().insertAll(
            listOf(PlanWeekEntity(planId = planId, weekIndex = 1, startDateEpochDay = 19000L, phase = "BASE", isRecoveryWeek = false, plannedWeeklyLoad = 300)),
        )
        db.plannedWorkoutDao().insertAll(
            listOf(
                PlannedWorkoutEntity(
                    weekId = weekIds.single(),
                    dateEpochDay = 19000L,
                    discipline = "RUN",
                    workoutType = "EASY",
                    title = "Easy Run",
                    plannedDurationSec = 1800,
                    plannedDistanceM = null,
                    plannedLoad = 40,
                    status = "PLANNED",
                    zoneLevel = 1,
                    brickGroupId = null,
                    sortOrderInDay = 0,
                ),
            ),
        )

        val workouts = db.plannedWorkoutDao().getForPlanOnce(planId)

        assertThat(workouts).hasSize(1)
        assertThat(workouts.single().title).isEqualTo("Easy Run")
    }

    @Test
    fun `deleting a plan cascades to its weeks and workouts`() = runBlocking {
        val raceGoalId = db.raceGoalDao().insert(
            RaceGoalEntity(distance = "SPRINT", raceDateEpochDay = 20000L, raceName = null, targetFinishTimeSec = null, isActive = true, createdAtEpochMilli = 0L),
        )
        val planId = db.trainingPlanDao().insert(
            TrainingPlanEntity(raceGoalId = raceGoalId, startDateEpochDay = 19000L, totalWeeks = 12, status = "ACTIVE", generatedAtEpochMilli = 0L),
        )
        db.planWeekDao().insertAll(
            listOf(PlanWeekEntity(planId = planId, weekIndex = 1, startDateEpochDay = 19000L, phase = "BASE", isRecoveryWeek = false, plannedWeeklyLoad = 300)),
        )

        // Deleting the parent race goal should cascade: race_goal -> training_plan -> plan_week.
        db.openHelper.writableDatabase.execSQL("DELETE FROM race_goal WHERE id = $raceGoalId")

        val remainingWeeks = db.planWeekDao().observeForPlan(planId).first()
        assertThat(remainingWeeks).isEmpty()
    }

    @Test
    fun `completed activity dedupes on health connect record id`() = runBlocking {
        val activity = CompletedActivityEntity(
            healthConnectRecordId = "hc-123",
            discipline = "RUN",
            startTimeEpochMilli = 1000L,
            endTimeEpochMilli = 2000L,
            distanceM = 5000.0,
            avgHr = 150,
            maxHr = 170,
            avgPowerW = null,
            normalizedPowerW = null,
            calculatedLoad = 60,
            matchedPlannedWorkoutId = null,
            matchStatus = "UNMATCHED_EXTRA",
        )

        db.completedActivityDao().upsertAll(listOf(activity))
        db.completedActivityDao().upsertAll(listOf(activity.copy(calculatedLoad = 99))) // same record id, updated load

        val all = db.completedActivityDao().getSinceOnce(0L)
        assertThat(all).hasSize(1)
        assertThat(all.single().calculatedLoad).isEqualTo(99)
    }
}
