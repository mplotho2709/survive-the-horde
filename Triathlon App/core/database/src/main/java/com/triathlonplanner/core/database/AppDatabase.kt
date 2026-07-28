package com.triathlonplanner.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        RaceGoalEntity::class,
        TrainingPlanEntity::class,
        PlanWeekEntity::class,
        PlannedWorkoutEntity::class,
        WorkoutStepEntity::class,
        CompletedActivityEntity::class,
        AdaptationEventEntity::class,
        RollingLoadStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun raceGoalDao(): RaceGoalDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun planWeekDao(): PlanWeekDao
    abstract fun plannedWorkoutDao(): PlannedWorkoutDao
    abstract fun workoutStepDao(): WorkoutStepDao
    abstract fun completedActivityDao(): CompletedActivityDao
    abstract fun adaptationEventDao(): AdaptationEventDao
    abstract fun rollingLoadStateDao(): RollingLoadStateDao

    companion object {
        const val DATABASE_NAME = "triathlon_planner.db"
    }
}
