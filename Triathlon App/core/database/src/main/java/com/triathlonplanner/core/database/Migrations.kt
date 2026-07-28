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
