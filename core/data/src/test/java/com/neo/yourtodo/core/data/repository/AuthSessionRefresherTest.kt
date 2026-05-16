package com.neo.yourtodo.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedCacheKey
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import com.neo.yourtodo.core.network.auth.NetworkAuthUser
import com.neo.yourtodo.core.network.auth.NetworkAuthUserResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthSessionRefresherTest {

    @Test
    fun refreshStoresNewSession() = runTest {
        val prefs = FakePreferencesDataSource(authSession(refreshToken = "old-refresh"))
        val network = FakeAuthNetworkDataSource()
        val refresher = AuthSessionRefresher(prefs, network)

        val refreshed = refresher.refresh("old-refresh")

        assertThat(network.refreshTokens).containsExactly("old-refresh")
        assertThat(refreshed?.accessToken).isEqualTo("new-access")
        assertThat(prefs.authSession.first()?.refreshToken).isEqualTo("new-refresh")
    }

    @Test
    fun refreshReturnsCurrentSessionWithoutNetworkWhenAnotherCallerAlreadyRotatedToken() = runTest {
        val prefs = FakePreferencesDataSource(authSession(refreshToken = "new-refresh"))
        val network = FakeAuthNetworkDataSource()
        val refresher = AuthSessionRefresher(prefs, network)

        val refreshed = refresher.refresh("old-refresh")

        assertThat(network.refreshTokens).isEmpty()
        assertThat(refreshed?.refreshToken).isEqualTo("new-refresh")
    }

    @Test
    fun refreshReturnsNullWithoutNetworkWhenAnotherCallerAlreadyClearedSession() = runTest {
        val prefs = FakePreferencesDataSource(session = null)
        val network = FakeAuthNetworkDataSource()
        val refresher = AuthSessionRefresher(prefs, network)

        val refreshed = refresher.refresh("old-refresh")

        assertThat(refreshed).isNull()
        assertThat(network.refreshTokens).isEmpty()
    }

    @Test
    fun refreshFailureClearsOnlySessionAndPreservesLocalRefreshState() = runTest {
        val prefs = FakePreferencesDataSource(authSession(refreshToken = "old-refresh"))
        val network = FakeAuthNetworkDataSource(refreshException = IllegalStateException("Refresh failed"))
        val assignmentFeedFreshnessTracker = AssignmentFeedFreshnessTracker()
        val feed = AssignmentFeedCacheKey(
            direction = AssignmentDirection.RECEIVED,
            status = AssignmentFeedStatus.ACTIVE
        )
        assignmentFeedFreshnessTracker.recordRefresh(
            ownerUserId = "user-id",
            feed = feed,
            refreshedAt = 123L
        )
        prefs.setAssignmentFeedRefreshTime("user-id_received_active", 123L)
        val refresher = AuthSessionRefresher(prefs, network)

        val refreshed = refresher.refresh("old-refresh")

        assertThat(refreshed).isNull()
        assertThat(network.refreshTokens).containsExactly("old-refresh")
        assertThat(prefs.authSession.first()).isNull()
        assertThat(assignmentFeedFreshnessTracker.observeRefreshTime("user-id", feed).first())
            .isEqualTo(123L)
        assertThat(prefs.observeAssignmentFeedRefreshTime("user-id_received_active").first())
            .isEqualTo(123L)
    }

    @Test
    fun refreshCancellationKeepsSessionAndRethrows() = runTest {
        val prefs = FakePreferencesDataSource(authSession(refreshToken = "old-refresh"))
        val network = FakeAuthNetworkDataSource(refreshException = CancellationException("Cancelled"))
        val refresher = AuthSessionRefresher(prefs, network)

        var cancellationThrown = false
        try {
            refresher.refresh("old-refresh")
        } catch (_: CancellationException) {
            cancellationThrown = true
        }

        assertThat(cancellationThrown).isTrue()
        assertThat(network.refreshTokens).containsExactly("old-refresh")
        assertThat(prefs.authSession.first()?.refreshToken).isEqualTo("old-refresh")
    }

    private class FakePreferencesDataSource(
        session: AuthSessionData?
    ) : UserPreferencesDataSource {
        private val authSessionFlow = MutableStateFlow(session)

        override val authSession: Flow<AuthSessionData?> = authSessionFlow.asStateFlow()
        override val selectedTodoFilter: Flow<TodoFilter> = MutableStateFlow(TodoFilter.ALL)
        override val selectedTodoCategoryFilter: Flow<Long?> = MutableStateFlow(null)
        override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> =
            MutableStateFlow(TodoPriorityFilter.ALL)
        override val todoSyncCursor: Flow<String?> = MutableStateFlow(null)
        override val todoSyncHaltReason: Flow<String?> = MutableStateFlow(null)
        private val assignmentFeedRefreshTimes = MutableStateFlow<Map<String, Long>>(emptyMap())

        override suspend fun saveAuthSession(session: AuthSessionData) {
            authSessionFlow.value = session
        }

        override suspend fun clearAuthSession() {
            authSessionFlow.value = null
        }

        override suspend fun setSelectedTodoFilter(filter: TodoFilter) = Unit
        override suspend fun setSelectedTodoCategoryFilter(categoryId: Long?) = Unit
        override suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter) = Unit
        override suspend fun setTodoSyncCursor(cursor: String?) = Unit
        override suspend fun setTodoSyncHaltReason(reason: String?) = Unit
        override suspend fun clearTodoSyncState() = Unit
        override fun observeAssignmentFeedRefreshTime(feedKey: String): Flow<Long?> =
            MutableStateFlow(assignmentFeedRefreshTimes.value[feedKey])

        override suspend fun setAssignmentFeedRefreshTime(feedKey: String, refreshedAtEpochMillis: Long) {
            assignmentFeedRefreshTimes.value += feedKey to refreshedAtEpochMillis
        }

        override suspend fun clearAssignmentFeedRefreshTimes() {
            assignmentFeedRefreshTimes.value = emptyMap()
        }
    }

    private class FakeAuthNetworkDataSource(
        private val refreshException: Exception? = null
    ) : AuthNetworkDataSource {
        val refreshTokens = mutableListOf<String>()

        override suspend fun signInWithGoogle(idToken: String): NetworkAuthSession =
            error("Not used.")

        override suspend fun refreshSession(refreshToken: String): NetworkAuthSession {
            refreshTokens += refreshToken
            refreshException?.let { throw it }
            return NetworkAuthSession(
                accessToken = "new-access",
                refreshToken = "new-refresh",
                user = NetworkAuthUser(
                    id = "user-id",
                    nickname = "neo",
                    email = "neo@example.com",
                    onboardingRequired = false
                )
            )
        }

        override suspend fun completeNicknameOnboarding(
            accessToken: String,
            nickname: String
        ): NetworkAuthUserResponse = error("Not used.")
    }

    private fun authSession(refreshToken: String) = AuthSessionData(
        accessToken = "access",
        refreshToken = refreshToken,
        userId = "user-id",
        nickname = "neo",
        email = "neo@example.com",
        onboardingRequired = false
    )
}
