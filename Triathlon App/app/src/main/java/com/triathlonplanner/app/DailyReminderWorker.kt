package com.triathlonplanner.app

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.data.repository.PlanRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Once-daily reminder of today's planned workout(s) - the periodic HC sync worker is a separate concern. */
@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted context: android.content.Context,
    @Assisted params: WorkerParameters,
    private val planRepository: PlanRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val workouts = planRepository.observeWorkoutsForDate(LocalDate.now()).first()
        val actionable = workouts.filter { it.discipline != Discipline.REST && it.status == WorkoutStatus.PLANNED }
        if (actionable.isEmpty()) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val text = if (actionable.size == 1) {
            val workout = actionable.first()
            "${workout.workoutType.name.lowercase().replaceFirstChar(Char::uppercase)} " +
                workout.discipline.name.lowercase().replaceFirstChar(Char::uppercase)
        } else {
            "${actionable.size} workouts planned today"
        }

        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.DAILY_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Today's training")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val UNIQUE_WORK_NAME = "daily_workout_reminder"
        private const val REMINDER_HOUR = 7

        fun schedule(workManager: WorkManager) {
            val now = ZonedDateTime.now()
            var nextRun = now.withHour(REMINDER_HOUR).withMinute(0).withSecond(0).withNano(0)
            if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
            val initialDelayMinutes = Duration.between(now, nextRun).toMinutes()

            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
