package com.triathlonplanner.data.healthconnect

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Foreground sync happens on app resume (see :feature:today's ViewModel); this periodic worker is
 * only the safety net for users who don't open the app daily, per the plan's sync strategy.
 */
@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dataSource: HealthConnectDataSource,
    private val changeTokenStore: ChangeTokenStore,
    private val sink: CompletedActivitySink,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (checkHealthConnectAvailability(applicationContext) != HealthConnectAvailability.AVAILABLE) {
            return Result.success()
        }
        if (!dataSource.hasAllPermissions()) {
            return Result.success()
        }

        return try {
            val token = changeTokenStore.get() ?: dataSource.getChangesToken()
            val changes = dataSource.getChangedSessions(token)
            val activities = changes.newOrUpdatedSessions.mapNotNull { dataSource.buildCompletedActivity(it) }
            if (activities.isNotEmpty()) {
                sink.onActivitiesSynced(activities)
            }
            changeTokenStore.save(changes.nextChangeToken)
            Result.success()
        } catch (e: SecurityException) {
            // Permission was revoked externally (e.g. system settings) - nothing more to do
            // until the user re-grants it; retrying won't help.
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "health_connect_sync"

        fun schedulePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(8, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun triggerImmediateSync(workManager: WorkManager) {
            workManager.enqueue(OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build())
        }
    }
}
