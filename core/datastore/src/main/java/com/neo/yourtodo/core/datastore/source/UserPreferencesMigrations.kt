package com.neo.yourtodo.core.datastore.source

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SCHEMA_VERSION
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_PRIORITY_FILTER
import com.neo.yourtodo.core.model.TodoPriorityFilter

internal object UserPreferencesMigrations {
    private const val CURRENT_SCHEMA_VERSION = 1

    fun encryptLegacyAuthTokens(
        authTokenStoragePolicy: AuthTokenStoragePolicy
    ) = object : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences): Boolean =
            authTokenStoragePolicy.shouldMigrateLegacyPlaintextTokens(currentData)

        override suspend fun migrate(currentData: Preferences): Preferences =
            authTokenStoragePolicy.migrateLegacyPlaintextTokens(currentData)

        override suspend fun cleanUp() = Unit
    }

    val resetLegacyTodoPriorityFilter = object : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences): Boolean =
            (currentData[SCHEMA_VERSION] ?: 0) < CURRENT_SCHEMA_VERSION

        override suspend fun migrate(currentData: Preferences): Preferences =
            currentData.toMutablePreferences().apply {
                this[SELECTED_TODO_PRIORITY_FILTER] = TodoPriorityFilter.ALL.name
                this[SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
            }.toPreferences()

        override suspend fun cleanUp() = Unit
    }
}
