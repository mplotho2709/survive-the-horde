package com.triathlonplanner.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val DAILY_REMINDER_CHANNEL_ID = "daily_workout_reminder"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            DAILY_REMINDER_CHANNEL_ID,
            "Workout reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A daily reminder for today's planned workout"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
