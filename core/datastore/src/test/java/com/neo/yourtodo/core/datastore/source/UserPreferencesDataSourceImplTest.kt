package com.neo.yourtodo.core.datastore.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_ACCESS_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ENCRYPTED_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_ONBOARDING_REQUIRED
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_REFRESH_TOKEN
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_EMAIL
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_ID
import com.neo.yourtodo.core.datastore.source.UserPreferenceKeys.AUTH_USER_NICKNAME
import com.neo.yourtodo.core.model.TodoFilter
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun authSessionDoesNotDecryptWhenUnrelatedPreferencesChange() = runTest {
        val dataStore = FakePreferencesDataStore()
        val cipher = CountingAuthTokenCipher()
        val dataSource = createDataSource(
            dataStore = dataStore,
            authTokenStoragePolicy = AuthTokenStoragePolicy(cipher)
        )
        dataSource.saveAuthSession(authSession())
        val observedSessions = mutableListOf<AuthSessionData?>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            dataSource.authSession.collect { session -> observedSessions += session }
        }
        advanceUntilIdle()

        assertThat(observedSessions).containsExactly(authSession())
        assertThat(cipher.decryptCalls).isEqualTo(2)

        dataSource.setSelectedTodoFilter(TodoFilter.TODAY)
        advanceUntilIdle()

        assertThat(observedSessions).containsExactly(authSession())
        assertThat(cipher.decryptCalls).isEqualTo(2)

        val renamedSession = authSession(nickname = "Trinity")
        dataSource.saveAuthSession(renamedSession)
        advanceUntilIdle()

        assertThat(observedSessions).containsExactly(authSession(), renamedSession).inOrder()
        assertThat(cipher.decryptCalls).isEqualTo(4)

        collectJob.cancel()
    }

    @Test
    fun assignmentFeedRefreshTimePersistsAndClearsByPrefix() = runTest {
        val dataStore = createDataStore(backgroundScope)
        val firstDataSource = createDataSource(dataStore)
        val secondDataSource = createDataSource(dataStore)

        firstDataSource.setAssignmentFeedRefreshTime("user-id_received_active", 123L)

        assertThat(secondDataSource.observeAssignmentFeedRefreshTime("user-id_received_active").first())
            .isEqualTo(123L)

        secondDataSource.clearAssignmentFeedRefreshTimes()

        assertThat(firstDataSource.observeAssignmentFeedRefreshTime("user-id_received_active").first())
            .isNull()
    }

    private fun createDataSource(
        dataStore: DataStore<Preferences>,
        authTokenStoragePolicy: AuthTokenStoragePolicy = AuthTokenStoragePolicy(FakeAuthTokenCipher())
    ): UserPreferencesDataSourceImpl =
        UserPreferencesDataSourceImpl(
            dataStore = dataStore,
            authTokenStoragePolicy = authTokenStoragePolicy
        )

    private fun createDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(temporaryFolder.root, "user.preferences_pb") }
        )

    private fun authSession(
        nickname: String = "Neo",
        onboardingRequired: Boolean = false
    ) = AuthSessionData(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        userId = "user-id",
        nickname = nickname,
        email = "neo@example.com",
        onboardingRequired = onboardingRequired
    )

    private class FakeAuthTokenCipher : AuthTokenCipher {
        override fun encrypt(plainText: String): String = "encrypted:$plainText"

        override fun decrypt(cipherText: String): String? =
            cipherText.removePrefix("encrypted:").takeIf { it != cipherText }
    }

    private class CountingAuthTokenCipher : AuthTokenCipher {
        var decryptCalls = 0
            private set

        override fun encrypt(plainText: String): String = "encrypted:$plainText"

        override fun decrypt(cipherText: String): String? {
            decryptCalls += 1
            return cipherText.removePrefix("encrypted:").takeIf { it != cipherText }
        }
    }

    private class FakePreferencesDataStore(
        initialPreferences: Preferences = preferencesOf()
    ) : DataStore<Preferences> {
        private val preferencesFlow = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = preferencesFlow

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            val updated = transform(preferencesFlow.value)
            preferencesFlow.value = updated
            return updated
        }
    }
}
