package com.triathlonplanner.data.healthconnect

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private val CHANGE_TOKEN_KEY = stringPreferencesKey("health_connect_change_token")

/** Persists the Health Connect Changes API token so each sync only processes new data. */
class ChangeTokenStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun get(): String? = dataStore.data.first()[CHANGE_TOKEN_KEY]

    suspend fun save(token: String) {
        dataStore.edit { it[CHANGE_TOKEN_KEY] = token }
    }
}
