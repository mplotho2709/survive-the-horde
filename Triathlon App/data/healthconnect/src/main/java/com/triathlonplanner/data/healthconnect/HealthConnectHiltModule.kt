package com.triathlonplanner.data.healthconnect

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.health.connect.client.HealthConnectClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.healthConnectDataStore: DataStore<Preferences> by preferencesDataStore(name = "health_connect_sync")

@Module
@InstallIn(SingletonComponent::class)
object HealthConnectHiltModule {

    /**
     * HealthConnectClient.getOrCreate throws if Health Connect isn't installed - callers must
     * check [checkHealthConnectAvailability] first. Hilt provides are lazy, so this is safe as
     * long as nothing injects [HealthConnectDataSource] before that check.
     */
    @Provides
    @Singleton
    fun provideHealthConnectClient(@ApplicationContext context: Context): HealthConnectClient =
        HealthConnectClient.getOrCreate(context)

    @Provides
    @Singleton
    fun provideChangeTokenDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.healthConnectDataStore
}
