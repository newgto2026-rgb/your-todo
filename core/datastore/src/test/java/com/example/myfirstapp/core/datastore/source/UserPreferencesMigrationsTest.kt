package com.example.myfirstapp.core.datastore.source

import androidx.datastore.preferences.core.preferencesOf
import com.example.myfirstapp.core.datastore.source.UserPreferenceKeys.SCHEMA_VERSION
import com.example.myfirstapp.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_PRIORITY_FILTER
import com.example.myfirstapp.core.model.TodoPriorityFilter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UserPreferencesMigrationsTest {

    @Test
    fun resetLegacyTodoPriorityFilterClearsStoredPriorityFilterOnce() = runTest {
        val legacyPreferences = preferencesOf(
            SELECTED_TODO_PRIORITY_FILTER to TodoPriorityFilter.MEDIUM.name
        )
        val migration = UserPreferencesMigrations.resetLegacyTodoPriorityFilter

        assertThat(migration.shouldMigrate(legacyPreferences)).isTrue()

        val migrated = migration.migrate(legacyPreferences)

        assertThat(migrated[SELECTED_TODO_PRIORITY_FILTER]).isEqualTo(TodoPriorityFilter.ALL.name)
        assertThat(migrated[SCHEMA_VERSION]).isEqualTo(1)
        assertThat(migration.shouldMigrate(migrated)).isFalse()
    }
}
