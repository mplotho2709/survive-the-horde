package com.triathlonplanner.data.repository

import com.triathlonplanner.data.healthconnect.CompletedActivitySink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryHiltModule {

    @Binds
    @Singleton
    abstract fun bindCompletedActivitySink(impl: ActivitySyncRepository): CompletedActivitySink
}
