package com.triathlonplanner.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.triathlonplanner.data.healthconnect.HealthConnectSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TriathlonApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createChannels(this)
        HealthConnectSyncWorker.schedulePeriodic(WorkManager.getInstance(this))
        DailyReminderWorker.schedule(WorkManager.getInstance(this))
    }
}
