package com.neo.yourtodo.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import com.neo.yourtodo.core.network.auth.NetworkAuthUser
import com.neo.yourtodo.core.network.auth.NetworkAuthUserResponse
import com.neo.yourtodo.core.network.friends.FriendAuthRequiredException
import com.neo.yourtodo.core.network.friends.FriendNetworkDataSource
import com.neo.yourtodo.core.network.friends.NetworkAcceptFriendRequestResponse
import com.neo.yourtodo.core.network.friends.NetworkDeclineFriendRequestResponse
import com.neo.yourtodo.core.network.friends.NetworkFriend
import com.neo.yourtodo.core.network.friends.NetworkFriendRequest
import com.neo.yourtodo.core.network.friends.NetworkFriendRequestsResponse
import com.neo.yourtodo.core.network.friends.NetworkFriendUser
import com.neo.yourtodo.core.network.friends.NetworkFriendsResponse
import com.neo.yourtodo.core.network.friends.NetworkRemoveFriendResponse
import com.neo.yourtodo.core.network.friends.NetworkSendFriendRequestResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FriendRepositoryImplTest {
    @Test
    fun getFriendsMapsNetworkFriendsWithCurrentSession() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeFriendNetworkDataSource()
        val repository = repository(prefs = prefs, network = network)

        val result = repository.getFriends()

        assertThat(result.isSuccess).isTrue()
        assertThat(network.friendTokens).containsExactly("access-token")
        assertThat(result.getOrThrow()).hasSize(1)
        assertThat(result.getOrThrow().first().nickname).isEqualTo("monday")
    }

    @Test
    fun getFriendsDoesNotFallbackToPreviousSnapshotWhenNetworkFails() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeFriendNetworkDataSource()
        val repository = repository(prefs = prefs, network = network)
        val failure = IllegalStateException("Network unavailable")

        assertThat(repository.getFriends().getOrThrow().map { it.nickname })
            .containsExactly("monday")

        network.getFriendsFailure = failure
        val result = repository.getFriends()

        assertThat(result.exceptionOrNull()).isSameInstanceAs(failure)
        assertThat(network.friendTokens).containsExactly("access-token", "access-token")
        assertThat(prefs.authSession.first()).isNotNull()
    }

    @Test
    fun requestQueriesReturnNetworkFailureWithoutCachedFallback() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val incomingFailure = IllegalStateException("Incoming unavailable")
        val outgoingFailure = IllegalStateException("Outgoing unavailable")
        val network = FakeFriendNetworkDataSource().apply {
            getIncomingRequestsFailure = incomingFailure
            getOutgoingRequestsFailure = outgoingFailure
        }
        val repository = repository(prefs = prefs, network = network)

        val incoming = repository.getIncomingRequests()
        val outgoing = repository.getOutgoingRequests()

        assertThat(incoming.exceptionOrNull()).isSameInstanceAs(incomingFailure)
        assertThat(outgoing.exceptionOrNull()).isSameInstanceAs(outgoingFailure)
        assertThat(network.incomingTokens).containsExactly("access-token")
        assertThat(network.outgoingTokens).containsExactly("access-token")
        assertThat(prefs.authSession.first()).isNotNull()
    }

    @Test
    fun sendRequestRetriesWithRefreshedSessionWhenAccessTokenExpires() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeFriendNetworkDataSource().apply {
            authFailuresRemaining = 1
        }
        val authNetwork = FakeAuthNetworkDataSource()
        val repository = repository(
            prefs = prefs,
            network = network,
            authNetwork = authNetwork
        )

        val result = repository.sendRequest("monday")

        assertThat(result.isSuccess).isTrue()
        assertThat(authNetwork.lastRefreshToken).isEqualTo("refresh-token")
        assertThat(network.sendTokens).containsExactly("access-token", "refreshed-access-token").inOrder()
        assertThat(network.lastNickname).isEqualTo("monday")
        assertThat(prefs.authSession.first()?.accessToken).isEqualTo("refreshed-access-token")
    }

    @Test
    fun acceptRequestReturnsAuthRequiredWhenRefreshFails() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeFriendNetworkDataSource().apply {
            authFailuresRemaining = 1
        }
        val repository = repository(
            prefs = prefs,
            network = network,
            authNetwork = FakeAuthNetworkDataSource(refreshFails = true)
        )

        val result = repository.acceptRequest("request-1")

        assertThat(result.exceptionOrNull()).isInstanceOf(AuthRequiredException::class.java)
        assertThat(network.acceptTokens).containsExactly("access-token")
        assertThat(prefs.authSession.first()).isNull()
    }

    @Test
    fun removeFriendReturnsAuthRequiredWhenSessionIsMissing() = runTest {
        val repository = repository()

        val result = repository.removeFriend("friendship-1")

        assertThat(result.exceptionOrNull()).isInstanceOf(AuthRequiredException::class.java)
    }

    private fun repository(
        prefs: FakePreferencesDataSource = FakePreferencesDataSource(),
        network: FakeFriendNetworkDataSource = FakeFriendNetworkDataSource(),
        authNetwork: FakeAuthNetworkDataSource = FakeAuthNetworkDataSource()
    ) = FriendRepositoryImpl(
        userPreferencesDataSource = prefs,
        friendNetworkDataSource = network,
        authNetworkDataSource = authNetwork
    )

    private fun authSession(
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token"
    ) = AuthSessionData(
        accessToken = accessToken,
        refreshToken = refreshToken,
        userId = "user-id",
        nickname = "neo",
        email = "neo@example.com",
        onboardingRequired = false
    )

    private class FakePreferencesDataSource : UserPreferencesDataSource {
        private val authSessionFlow = MutableStateFlow<AuthSessionData?>(null)
        private val filterFlow = MutableStateFlow(TodoFilter.ALL)
        private val categoryFilterFlow = MutableStateFlow<Long?>(null)
        private val priorityFilterFlow = MutableStateFlow(TodoPriorityFilter.ALL)
        private val syncCursorFlow = MutableStateFlow<String?>(null)
        private val syncHaltReasonFlow = MutableStateFlow<String?>(null)

        override val authSession: Flow<AuthSessionData?> = authSessionFlow.asStateFlow()
        override val selectedTodoFilter: Flow<TodoFilter> = filterFlow.asStateFlow()
        override val selectedTodoCategoryFilter: Flow<Long?> = categoryFilterFlow.asStateFlow()
        override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> = priorityFilterFlow.asStateFlow()
        override val todoSyncCursor: Flow<String?> = syncCursorFlow.asStateFlow()
        override val todoSyncHaltReason: Flow<String?> = syncHaltReasonFlow.asStateFlow()

        override suspend fun saveAuthSession(session: AuthSessionData) {
            authSessionFlow.value = session
        }

        override suspend fun clearAuthSession() {
            authSessionFlow.value = null
        }

        override suspend fun setSelectedTodoFilter(filter: TodoFilter) {
            filterFlow.value = filter
        }

        override suspend fun setSelectedTodoCategoryFilter(categoryId: Long?) {
            categoryFilterFlow.value = categoryId
        }

        override suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter) {
            priorityFilterFlow.value = filter
        }

        override suspend fun setTodoSyncCursor(cursor: String?) {
            syncCursorFlow.value = cursor
        }

        override suspend fun setTodoSyncHaltReason(reason: String?) {
            syncHaltReasonFlow.value = reason
        }

        override suspend fun clearTodoSyncState() {
            syncCursorFlow.value = null
            syncHaltReasonFlow.value = null
        }
    }

    private class FakeAuthNetworkDataSource(
        private val refreshFails: Boolean = false
    ) : AuthNetworkDataSource {
        var lastRefreshToken: String? = null

        override suspend fun signInWithGoogle(idToken: String): NetworkAuthSession =
            refreshedSession()

        override suspend fun refreshSession(refreshToken: String): NetworkAuthSession {
            lastRefreshToken = refreshToken
            if (refreshFails) error("Refresh failed")
            return refreshedSession()
        }

        override suspend fun completeNicknameOnboarding(
            accessToken: String,
            nickname: String
        ): NetworkAuthUserResponse =
            NetworkAuthUserResponse(
                user = refreshedSession().user.copy(nickname = nickname)
            )

        private fun refreshedSession() =
            NetworkAuthSession(
                accessToken = "refreshed-access-token",
                refreshToken = "refreshed-refresh-token",
                user = NetworkAuthUser(
                    id = "user-id",
                    nickname = "neo",
                    email = "neo@example.com",
                    onboardingRequired = false
                )
            )
    }

    private class FakeFriendNetworkDataSource : FriendNetworkDataSource {
        var authFailuresRemaining = 0
        var getFriendsFailure: Throwable? = null
        var getIncomingRequestsFailure: Throwable? = null
        var getOutgoingRequestsFailure: Throwable? = null
        val friendTokens = mutableListOf<String>()
        val incomingTokens = mutableListOf<String>()
        val outgoingTokens = mutableListOf<String>()
        val sendTokens = mutableListOf<String>()
        val acceptTokens = mutableListOf<String>()
        var lastNickname: String? = null

        override suspend fun getFriends(accessToken: String): NetworkFriendsResponse {
            friendTokens += accessToken
            failAuthIfNeeded()
            getFriendsFailure?.let { throw it }
            return NetworkFriendsResponse(
                friends = listOf(networkFriend())
            )
        }

        override suspend fun getIncomingRequests(accessToken: String): NetworkFriendRequestsResponse {
            incomingTokens += accessToken
            failAuthIfNeeded()
            getIncomingRequestsFailure?.let { throw it }
            return NetworkFriendRequestsResponse(requests = listOf(networkRequest()))
        }

        override suspend fun getOutgoingRequests(accessToken: String): NetworkFriendRequestsResponse {
            outgoingTokens += accessToken
            failAuthIfNeeded()
            getOutgoingRequestsFailure?.let { throw it }
            return NetworkFriendRequestsResponse(requests = emptyList())
        }

        override suspend fun sendRequest(
            accessToken: String,
            nickname: String
        ): NetworkSendFriendRequestResponse {
            sendTokens += accessToken
            lastNickname = nickname
            failAuthIfNeeded()
            return NetworkSendFriendRequestResponse(
                request = networkRequest(),
                autoAccepted = false,
                friendship = null
            )
        }

        override suspend fun acceptRequest(
            accessToken: String,
            requestId: String
        ): NetworkAcceptFriendRequestResponse {
            acceptTokens += accessToken
            failAuthIfNeeded()
            return NetworkAcceptFriendRequestResponse(
                request = networkRequest(status = "ACCEPTED"),
                friendship = networkFriend()
            )
        }

        override suspend fun declineRequest(
            accessToken: String,
            requestId: String
        ): NetworkDeclineFriendRequestResponse {
            failAuthIfNeeded()
            return NetworkDeclineFriendRequestResponse(
                request = networkRequest(status = "DECLINED")
            )
        }

        override suspend fun removeFriend(
            accessToken: String,
            friendshipId: String
        ): NetworkRemoveFriendResponse {
            failAuthIfNeeded()
            return NetworkRemoveFriendResponse(
                friendship = networkFriend(status = "REMOVED")
            )
        }

        private fun failAuthIfNeeded() {
            if (authFailuresRemaining > 0) {
                authFailuresRemaining -= 1
                throw FriendAuthRequiredException()
            }
        }

        private fun networkFriend(status: String = "ACTIVE") =
            NetworkFriend(
                friendshipId = "friendship-1",
                userId = "friend-1",
                nickname = "monday",
                status = status,
                createdAt = "2026-05-09T00:00:00Z",
                removedAt = null
            )

        private fun networkRequest(status: String = "PENDING") =
            NetworkFriendRequest(
                id = "request-1",
                requester = NetworkFriendUser(
                    id = "friend-1",
                    nickname = "monday"
                ),
                receiver = NetworkFriendUser(
                    id = "user-id",
                    nickname = "neo"
                ),
                status = status,
                createdAt = "2026-05-09T00:00:00Z",
                respondedAt = null
            )
    }
}
