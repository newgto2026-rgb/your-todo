package com.neo.yourtodo.core.datastore.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ONBOARDING_REQUIRED
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_EMAIL
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_ID
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_NICKNAME
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesDataSourceImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun saveAuthSessionWritesEncryptedTokensAndClearsLegacyPlaintext() = runTest {
        val dataStore = createDataStore(backgroundScope)
        dataStore.edit { preferences ->
            preferences[AUTH_ACCESS_TOKEN] = "legacy-access-token"
            preferences[AUTH_REFRESH_TOKEN] = "legacy-refresh-token"
        }
        val dataSource = createDataSource(dataStore)

        dataSource.saveAuthSession(authSession())

        val saved = dataStore.data.first()
        assertThat(saved[AUTH_ENCRYPTED_ACCESS_TOKEN]).isEqualTo("encrypted:access-token")
        assertThat(saved[AUTH_ENCRYPTED_REFRESH_TOKEN]).isEqualTo("encrypted:refresh-token")
        assertThat(saved[AUTH_ACCESS_TOKEN]).isNull()
        assertThat(saved[AUTH_REFRESH_TOKEN]).isNull()
        assertThat(dataSource.authSession.first()).isEqualTo(authSession())
    }

    @Test
    fun authSessionReadsLegacyPlaintextTokensForBackwardCompatibility() = runTest {
        val dataStore = createDataStore(backgroundScope)
        dataStore.edit { preferences ->
            preferences[AUTH_ACCESS_TOKEN] = "access-token"
            preferences[AUTH_REFRESH_TOKEN] = "refresh-token"
            preferences[AUTH_USER_ID] = "user-id"
            preferences[AUTH_USER_NICKNAME] = "Neo"
            preferences[AUTH_USER_EMAIL] = "neo@example.com"
            preferences[AUTH_ONBOARDING_REQUIRED] = 1
        }
        val dataSource = createDataSource(dataStore)

        assertThat(dataSource.authSession.first()).isEqualTo(
            authSession(onboardingRequired = true)
        )
    }

    private fun createDataSource(
        dataStore: DataStore<Preferences>
    ): UserPreferencesDataSourceImpl =
        UserPreferencesDataSourceImpl(
            dataStore = dataStore,
            authTokenStoragePolicy = AuthTokenStoragePolicy(FakeAuthTokenCipher())
        )

    private fun createDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(temporaryFolder.root, "user.preferences_pb") }
        )

    private fun authSession(
        onboardingRequired: Boolean = false
    ) = AuthSessionData(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        userId = "user-id",
        nickname = "Neo",
        email = "neo@example.com",
        onboardingRequired = onboardingRequired
    )

    private class FakeAuthTokenCipher : AuthTokenCipher {
        override fun encrypt(plainText: String): String = "encrypted:$plainText"

        override fun decrypt(cipherText: String): String? =
            cipherText.removePrefix("encrypted:").takeIf { it != cipherText }
    }
}
