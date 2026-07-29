package com.triathlonplanner.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE race_goal ADD COLUMN targetWeeklyHours REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE race_goal ADD COLUMN targetDaysPerWeek INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE planned_workout ADD COLUMN substitutedDiscipline TEXT DEFAULT NULL")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_step ADD COLUMN cueText TEXT DEFAULT NULL")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE race_goal ADD COLUMN currentFitnessEstimateSec INTEGER DEFAULT NULL")
    }
}
