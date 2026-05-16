package com.neo.yourtodo.core.datastore.source

import androidx.datastore.preferences.core.preferencesOf
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SCHEMA_VERSION
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.SELECTED_TODO_PRIORITY_FILTER
import com.neo.yourtodo.core.model.TodoPriorityFilter
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

    @Test
    fun encryptLegacyAuthTokensMovesPlaintextTokensToEncryptedStorage() = runTest {
        val legacyPreferences = preferencesOf(
            AUTH_ACCESS_TOKEN to "legacy-access-token",
            AUTH_REFRESH_TOKEN to "legacy-refresh-token"
        )
        val migration = UserPreferencesMigrations.encryptLegacyAuthTokens(
            AuthTokenStoragePolicy(FakeAuthTokenCipher())
        )

        assertThat(migration.shouldMigrate(legacyPreferences)).isTrue()

        val migrated = migration.migrate(legacyPreferences)

        assertThat(migrated[AUTH_ENCRYPTED_ACCESS_TOKEN]).isEqualTo("encrypted:legacy-access-token")
        assertThat(migrated[AUTH_ENCRYPTED_REFRESH_TOKEN]).isEqualTo("encrypted:legacy-refresh-token")
        assertThat(migrated[AUTH_ACCESS_TOKEN]).isNull()
        assertThat(migrated[AUTH_REFRESH_TOKEN]).isNull()
        assertThat(migration.shouldMigrate(migrated)).isFalse()
    }

    private class FakeAuthTokenCipher : AuthTokenCipher {
        override fun encrypt(plainText: String): String = "encrypted:$plainText"

        override fun decrypt(cipherText: String): AuthTokenDecryptResult {
            val plainText = cipherText.removePrefix("encrypted:").takeIf { it != cipherText }
            return if (plainText == null) {
                AuthTokenDecryptResult.Failure(
                    AuthTokenCipherFailure(
                        operation = AuthTokenCipherOperation.DECRYPT,
                        type = AuthTokenCipherFailureType.INVALID_FORMAT,
                        message = "Stored token format is not recognized"
                    )
                )
            } else {
                AuthTokenDecryptResult.Success(plainText)
            }
        }
    }
}
