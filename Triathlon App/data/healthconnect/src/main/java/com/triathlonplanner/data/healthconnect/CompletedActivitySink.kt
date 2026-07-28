package com.triathlonplanner.data.healthconnect

import com.triathlonplanner.core.model.CompletedActivity

/**
 * Persistence boundary: :data:repository implements this (it owns Room + the user profile needed
 * to compute real load) and binds it via Hilt. Keeps :data:healthconnect from depending on
 * :data:repository, which already depends on :data:healthconnect - avoids a circular dependency.
 */
interface CompletedActivitySink {
    suspend fun onActivitiesSynced(activities: List<CompletedActivity>)
}
