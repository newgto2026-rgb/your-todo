package com.neo.yourtodo.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import com.neo.yourtodo.core.network.auth.NetworkAuthUser
import com.neo.yourtodo.core.network.push.NetworkDeletePushTokenRequest
import com.neo.yourtodo.core.network.push.NetworkDeletePushTokenResponse
import com.neo.yourtodo.core.network.push.NetworkPushToken
import com.neo.yourtodo.core.network.push.NetworkPushTokenRequest
import com.neo.yourtodo.core.network.push.NetworkPushTokenResponse
import com.neo.yourtodo.core.network.push.PushAuthRequiredException
import com.neo.yourtodo.core.network.push.PushNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PushTokenRepositoryImplTest {
    @Test
    fun registerCurrentToken_defersUntilAuthSessionExists() = runTest {
        val prefs = FakePreferencesDataSource()
        val network = FakePushNetworkDataSource()
        val repository = repository(prefs = prefs, network = network)

        repository.saveCurrentToken("token-a")
        val result = repository.registerCurrentToken()

        assertThat(result.isSuccess).isTrue()
        assertThat(network.upsertedTokens).isEmpty()
        assertThat(prefs.registeredToken.value).isNull()
    }

    @Test
    fun registerCurrentToken_sendsTokenAndStoresRegisteredToken() = runTest {
        val prefs = FakePreferencesDataSource().apply {
            saveAuthSession(authSession())
            setPushCurrentToken("token-a")
        }
        val network = FakePushNetworkDataSource()
        val repository = repository(prefs = prefs, network = network)

        val result = repository.registerCurrentToken()

        assertThat(result.isSuccess).isTrue()
        assertThat(network.upsertedTokens).containsExactly("token-a")
        assertThat(prefs.registeredToken.value).isEqualTo("token-a")
    }

    @Test
    fun registerCurrentToken_reupsertsAlreadyRegisteredToken() = runTest {
        val prefs = FakePreferencesDataSource().apply {
            saveAuthSession(authSession())
            setPushCurrentToken("token-a")
            setPushRegisteredToken("token-a")
        }
        val network = FakePushNetworkDataSource()
        val repository = repository(prefs = prefs, network = network)

        val result = repository.registerCurrentToken()

        assertThat(result.isSuccess).isTrue()
        assertThat(network.upsertedTokens).containsExactly("token-a")
        assertThat(prefs.registeredToken.value).isEqualTo("token-a")
    }

    @Test
    fun registerCurrentToken_refreshesSessionOnUnauthorized() = runTest {
        val prefs = FakePreferencesDataSource().apply {
            saveAuthSession(authSession(accessToken = "expired-access", refreshToken = "refresh-a"))
            setPushCurrentToken("token-a")
        }
        val network = FakePushNetworkDataSource(authRequiredOnce = true)
        val repository = repository(
            prefs = prefs,
            network = network,
            authNetwork = FakeAuthNetworkDataSource()
        )

        val result = repository.registerCurrentToken()

        assertThat(result.isSuccess).isTrue()
        assertThat(network.accessTokens).containsExactly("expired-access", "new-access").inOrder()
        assertThat(prefs.registeredToken.value).isEqualTo("token-a")
    }

    @Test
    fun deleteRegisteredToken_revokesAndClearsLocalRegistration() = runTest {
        val prefs = FakePreferencesDataSource().apply {
            saveAuthSession(authSession())
            setPushRegisteredToken("token-a")
        }
        val network = FakePushNetworkDataSource()
        val repository = repository(prefs = prefs, network = network)

        val result = repository.deleteRegisteredToken()

        assertThat(result.isSuccess).isTrue()
        assertThat(network.deletedTokens).containsExactly("token-a")
        assertThat(prefs.registeredToken.value).isNull()
    }

    private fun repository(
        prefs: FakePreferencesDataSource = FakePreferencesDataSource(),
        network: FakePushNetworkDataSource = FakePushNetworkDataSource(),
        authNetwork: FakeAuthNetworkDataSource = FakeAuthNetworkDataSource(),
        assignmentFeedFreshnessTracker: AssignmentFeedFreshnessTracker = AssignmentFeedFreshnessTracker()
    ) = PushTokenRepositoryImpl(
        userPreferencesDataSource = prefs,
        pushNetworkDataSource = network,
        assignmentFeedFreshnessTracker = assignmentFeedFreshnessTracker,
        authSessionRefresher = AuthSessionRefresher(
            prefs,
            authNetwork,
            assignmentFeedFreshnessTracker
        )
    )

    private fun authSession(
        accessToken: String = "access-a",
        refreshToken: String = "refresh-a"
    ) = AuthSessionData(
        accessToken = accessToken,
        refreshToken = refreshToken,
        userId = "user-a",
        nickname = "neo",
        email = "neo@example.com",
        onboardingRequired = false
    )

    private class FakePreferencesDataSource : UserPreferencesDataSource {
        private val session = MutableStateFlow<AuthSessionData?>(null)
        val registeredToken = MutableStateFlow<String?>(null)
        private val currentToken = MutableStateFlow<String?>(null)

        override val authSession: Flow<AuthSessionData?> = session.asStateFlow()
        override val selectedTodoFilter: Flow<TodoFilter> = flowOf(TodoFilter.ALL)
        override val selectedTodoCategoryFilter: Flow<Long?> = flowOf(null)
        override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> =
            flowOf(TodoPriorityFilter.ALL)
        override val todoSyncCursor: Flow<String?> = flowOf(null)
        override val todoSyncHaltReason: Flow<String?> = flowOf(null)
        override val pushCurrentToken: Flow<String?> = currentToken.asStateFlow()
        override val pushRegisteredToken: Flow<String?> = registeredToken.asStateFlow()

        override suspend fun saveAuthSession(session: AuthSessionData) {
            this.session.value = session
        }

        override suspend fun clearAuthSession() {
            session.value = null
        }

        override suspend fun setSelectedTodoFilter(filter: TodoFilter) = Unit
        override suspend fun setSelectedTodoCategoryFilter(categoryId: Long?) = Unit
        override suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter) = Unit
        override suspend fun setTodoSyncCursor(cursor: String?) = Unit
        override suspend fun setTodoSyncHaltReason(reason: String?) = Unit
        override suspend fun clearTodoSyncState() = Unit
        override suspend fun setPushCurrentToken(token: String?) {
            currentToken.value = token
        }

        override suspend fun setPushRegisteredToken(token: String?) {
            registeredToken.value = token
        }
    }

    private class FakePushNetworkDataSource(
        private var authRequiredOnce: Boolean = false
    ) : PushNetworkDataSource {
        val upsertedTokens = mutableListOf<String>()
        val deletedTokens = mutableListOf<String>()
        val accessTokens = mutableListOf<String>()

        override suspend fun upsertPushToken(
            accessToken: String,
            request: NetworkPushTokenRequest
        ): NetworkPushTokenResponse {
            accessTokens += accessToken
            if (authRequiredOnce) {
                authRequiredOnce = false
                throw PushAuthRequiredException()
            }
            upsertedTokens += request.token
            return NetworkPushTokenResponse(
                token = NetworkPushToken(
                    id = "push-token-id",
                    platform = request.platform,
                    lastSeenAt = "2026-05-09T00:00:00.000Z"
                )
            )
        }

        override suspend fun deletePushToken(
            accessToken: String,
            request: NetworkDeletePushTokenRequest
        ): NetworkDeletePushTokenResponse {
            accessTokens += accessToken
            deletedTokens += request.token
            return NetworkDeletePushTokenResponse(revokedCount = 1)
        }
    }

    private class FakeAuthNetworkDataSource : AuthNetworkDataSource {
        override suspend fun signInWithGoogle(idToken: String): NetworkAuthSession =
            authSession()

        override suspend fun refreshSession(refreshToken: String): NetworkAuthSession =
            authSession()

        override suspend fun completeNicknameOnboarding(
            accessToken: String,
            nickname: String
        ) = NetworkAuthUser(
            id = "user-a",
            nickname = nickname,
            email = "neo@example.com",
            onboardingRequired = false
        ).let { user -> com.neo.yourtodo.core.network.auth.NetworkAuthUserResponse(user) }

        private fun authSession() = NetworkAuthSession(
            accessToken = "new-access",
            refreshToken = "new-refresh",
            user = NetworkAuthUser(
                id = "user-a",
                nickname = "neo",
                email = "neo@example.com",
                onboardingRequired = false
            )
        )
    }
}
