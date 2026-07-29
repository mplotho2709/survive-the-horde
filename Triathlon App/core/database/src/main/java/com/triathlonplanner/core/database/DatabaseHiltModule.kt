package com.triathlonplanner.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseHiltModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides
    fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    fun provideRaceGoalDao(db: AppDatabase): RaceGoalDao = db.raceGoalDao()

    @Provides
    fun provideTrainingPlanDao(db: AppDatabase): TrainingPlanDao = db.trainingPlanDao()

    @Provides
    fun providePlanWeekDao(db: AppDatabase): PlanWeekDao = db.planWeekDao()

    @Provides
    fun providePlannedWorkoutDao(db: AppDatabase): PlannedWorkoutDao = db.plannedWorkoutDao()

    @Provides
    fun provideWorkoutStepDao(db: AppDatabase): WorkoutStepDao = db.workoutStepDao()

    @Provides
    fun provideCompletedActivityDao(db: AppDatabase): CompletedActivityDao = db.completedActivityDao()

    @Provides
    fun provideAdaptationEventDao(db: AppDatabase): AdaptationEventDao = db.adaptationEventDao()

    @Provides
    fun provideRollingLoadStateDao(db: AppDatabase): RollingLoadStateDao = db.rollingLoadStateDao()
}
