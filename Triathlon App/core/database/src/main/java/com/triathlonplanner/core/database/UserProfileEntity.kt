package com.triathlonplanner.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row (fixed id=1). Enum-like fields are stored as their [Enum.name] string and
 * dates/instants as epoch day/millis - this keeps Room converter-free; mapping to/from the
 * core:model enums happens in :data:repository, which already owns Entity<->domain mapping.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val maxHr: Int,
    val restingHr: Int?,
    val ftpWatts: Int?,
    val ftpSource: String?,
    val cssPaceSecPer100m: Int?,
    val cssSource: String?,
    val weightKg: Double?,
    val updatedAtEpochMilli: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
