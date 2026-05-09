package com.neo.yourtodo.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.network.assignments.AssignmentAuthRequiredException
import com.neo.yourtodo.core.network.assignments.AssignmentNetworkDataSource
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodo
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoChecklistItem
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoMutationResponse
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoReminder
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodosResponse
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentBundle
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentBundleResponse
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentDecision
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentSummary
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentUser
import com.neo.yourtodo.core.network.assignments.NetworkCreateAssignmentBundleRequest
import com.neo.yourtodo.core.network.assignments.NetworkDecideAssignmentItemsRequest
import com.neo.yourtodo.core.network.assignments.NetworkFriendAssignmentSummaryResponse
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import com.neo.yourtodo.core.network.auth.NetworkAuthUser
import com.neo.yourtodo.core.network.auth.NetworkAuthUserResponse
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AssignmentRepositoryImplTest {

    @Test
    fun createBundleMapsDraftRequestAndBundleResponse() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeAssignmentNetworkDataSource()
        val repository = repository(prefs = prefs, network = network)

        val result = repository.createBundle(
            receiverUserId = "receiver-1",
            items = listOf(
                AssignmentDraftItem(
                    title = "Buy milk",
                    description = null,
                    dueDate = "2026-05-10",
                    dueTimeMinutes = 14 * 60 + 30,
                    priority = TodoPriority.HIGH,
                    category = null
                )
            )
        )

        val bundle = result.getOrThrow()
        val requestItem = network.lastCreateRequest!!.items.single()
        assertThat(network.createTokens).containsExactly("access-token")
        assertThat(network.lastCreateRequest!!.receiverUserId).isEqualTo("receiver-1")
        assertThat(requestItem.title).isEqualTo("Buy milk")
        assertThat(requestItem.dueDate).isEqualTo("2026-05-10")
        assertThat(requestItem.dueTimeMinutes).isEqualTo(14 * 60 + 30)
        assertThat(requestItem.priority).isEqualTo(TodoPriority.HIGH.name)
        assertThat(bundle.id).isEqualTo("bundle-1")
        assertThat(bundle.items.single().bundleId).isEqualTo("bundle-1")
        assertThat(bundle.items.single().dueDate).isEqualTo(LocalDate.of(2026, 5, 10))
        assertThat(bundle.items.single().dueTimeMinutes).isEqualTo(14 * 60 + 30)
        assertThat(bundle.items.single().checklist.single().completed).isFalse()
        assertThat(bundle.items.single().reminder?.enabled).isTrue()
    }

    @Test
    fun feedSummaryDecisionAndMutationCallsMapWireValues() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeAssignmentNetworkDataSource()
        val repository = repository(prefs = prefs, network = network)

        val summary = repository.getFriendSummary("friend-1").getOrThrow()
        val friendItems = repository.getFriendAssignedTodos(
            friendUserId = "friend-1",
            direction = AssignmentDirection.SENT,
            status = AssignmentFeedStatus.ACTIVE
        ).getOrThrow()
        val received = repository.getReceivedAssignedTodos(AssignmentFeedStatus.PENDING).getOrThrow()
        val sent = repository.getSentAssignedTodos(AssignmentFeedStatus.HISTORY).getOrThrow()
        val decided = repository.decideBundleItems(
            bundleId = "bundle-1",
            decisions = mapOf("assigned-1" to AssignmentDecision.ACCEPT)
        ).getOrThrow()
        val completed = repository.completeAssignedTodo("assigned-1").getOrThrow()
        val deleted = repository.deleteReceivedAssignedTodo("assigned-1").getOrThrow()
        val canceled = repository.cancelAssignedTodo("assigned-1").getOrThrow()

        assertThat(summary.friendUserId).isEqualTo("friend-1")
        assertThat(summary.sent.totalCount).isEqualTo(2)
        assertThat(network.lastFriendDirection).isEqualTo("sent")
        assertThat(network.lastFriendStatus).isEqualTo("active")
        assertThat(network.lastReceivedStatus).isEqualTo("pending")
        assertThat(network.lastSentStatus).isEqualTo("history")
        assertThat(friendItems.single().dueTimeMinutes).isEqualTo(14 * 60 + 30)
        assertThat(received.single().sender?.nickname).isEqualTo("monday")
        assertThat(sent.single().receiver?.nickname).isEqualTo("neo")
        assertThat(network.lastDecisionRequest!!.decisions.single())
            .isEqualTo(NetworkAssignmentDecision("assigned-1", "ACCEPT"))
        assertThat(decided.items.single().title).isEqualTo("Shared todo")
        assertThat(completed.id).isEqualTo("assigned-1")
        assertThat(deleted.id).isEqualTo("assigned-1")
        assertThat(canceled.id).isEqualTo("assigned-1")
    }

    @Test
    fun requestRetriesWithRefreshedSessionWhenAccessTokenExpires() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeAssignmentNetworkDataSource().apply {
            authFailuresRemaining = 1
        }
        val authNetwork = FakeAuthNetworkDataSource()
        val repository = repository(
            prefs = prefs,
            network = network,
            authNetwork = authNetwork
        )

        val result = repository.getReceivedAssignedTodos(AssignmentFeedStatus.ACTIVE)

        assertThat(result.isSuccess).isTrue()
        assertThat(network.receivedTokens).containsExactly("access-token", "refreshed-access-token").inOrder()
        assertThat(authNetwork.lastRefreshToken).isEqualTo("refresh-token")
        assertThat(prefs.authSession.first()?.accessToken).isEqualTo("refreshed-access-token")
    }

    @Test
    fun requestReturnsAuthRequiredWhenSessionIsMissing() = runTest {
        val repository = repository()

        val result = repository.getSentAssignedTodos(AssignmentFeedStatus.ACTIVE)

        assertThat(result.exceptionOrNull()).isInstanceOf(AuthRequiredException::class.java)
    }

    @Test
    fun requestClearsSessionWhenRefreshFails() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeAssignmentNetworkDataSource().apply {
            authFailuresRemaining = 1
        }
        val authNetwork = FakeAuthNetworkDataSource(refreshFails = true)
        val repository = repository(
            prefs = prefs,
            network = network,
            authNetwork = authNetwork
        )

        val result = repository.getReceivedAssignedTodos(AssignmentFeedStatus.ACTIVE)

        assertThat(result.exceptionOrNull()).isInstanceOf(AuthRequiredException::class.java)
        assertThat(authNetwork.lastRefreshToken).isEqualTo("refresh-token")
        assertThat(prefs.authSession.first()).isNull()
    }

    private fun repository(
        prefs: FakePreferencesDataSource = FakePreferencesDataSource(),
        network: FakeAssignmentNetworkDataSource = FakeAssignmentNetworkDataSource(),
        authNetwork: FakeAuthNetworkDataSource = FakeAuthNetworkDataSource()
    ) = AssignmentRepositoryImpl(
        userPreferencesDataSource = prefs,
        assignmentNetworkDataSource = network,
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
            NetworkAuthUserResponse(user = refreshedSession().user.copy(nickname = nickname))

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

    private class FakeAssignmentNetworkDataSource : AssignmentNetworkDataSource {
        var authFailuresRemaining = 0
        val createTokens = mutableListOf<String>()
        val receivedTokens = mutableListOf<String>()
        var lastCreateRequest: NetworkCreateAssignmentBundleRequest? = null
        var lastReceivedStatus: String? = null
        var lastSentStatus: String? = null
        var lastFriendDirection: String? = null
        var lastFriendStatus: String? = null
        var lastDecisionRequest: NetworkDecideAssignmentItemsRequest? = null

        override suspend fun createBundle(
            accessToken: String,
            idempotencyKey: String,
            request: NetworkCreateAssignmentBundleRequest
        ): NetworkAssignmentBundleResponse {
            createTokens += accessToken
            failAuthIfNeeded()
            lastCreateRequest = request
            return bundleResponse()
        }

        override suspend fun getReceivedAssignedTodos(
            accessToken: String,
            status: String
        ): NetworkAssignedTodosResponse {
            receivedTokens += accessToken
            failAuthIfNeeded()
            lastReceivedStatus = status
            return NetworkAssignedTodosResponse(items = listOf(networkTodo()))
        }

        override suspend fun getSentAssignedTodos(
            accessToken: String,
            status: String
        ): NetworkAssignedTodosResponse {
            failAuthIfNeeded()
            lastSentStatus = status
            return NetworkAssignedTodosResponse(items = listOf(networkTodo()))
        }

        override suspend fun getFriendSummary(
            accessToken: String,
            friendUserId: String
        ): NetworkFriendAssignmentSummaryResponse {
            failAuthIfNeeded()
            return NetworkFriendAssignmentSummaryResponse(
                friendUserId = friendUserId,
                sent = summary(totalCount = 2),
                received = summary(totalCount = 1)
            )
        }

        override suspend fun getFriendAssignedTodos(
            accessToken: String,
            friendUserId: String,
            direction: String,
            status: String
        ): NetworkAssignedTodosResponse {
            failAuthIfNeeded()
            lastFriendDirection = direction
            lastFriendStatus = status
            return NetworkAssignedTodosResponse(items = listOf(networkTodo()))
        }

        override suspend fun decideItems(
            accessToken: String,
            idempotencyKey: String,
            bundleId: String,
            request: NetworkDecideAssignmentItemsRequest
        ): NetworkAssignmentBundleResponse {
            failAuthIfNeeded()
            lastDecisionRequest = request
            return bundleResponse()
        }

        override suspend fun completeAssignedTodo(
            accessToken: String,
            assignedTodoId: String
        ): NetworkAssignedTodoMutationResponse {
            failAuthIfNeeded()
            return mutationResponse(assignedTodoId)
        }

        override suspend fun deleteReceivedAssignedTodo(
            accessToken: String,
            idempotencyKey: String,
            assignedTodoId: String
        ): NetworkAssignedTodoMutationResponse {
            failAuthIfNeeded()
            return mutationResponse(assignedTodoId)
        }

        override suspend fun cancelAssignedTodo(
            accessToken: String,
            idempotencyKey: String,
            assignedTodoId: String
        ): NetworkAssignedTodoMutationResponse {
            failAuthIfNeeded()
            return mutationResponse(assignedTodoId)
        }

        private fun failAuthIfNeeded() {
            if (authFailuresRemaining > 0) {
                authFailuresRemaining -= 1
                throw AssignmentAuthRequiredException()
            }
        }
    }
}

private fun bundleResponse() = NetworkAssignmentBundleResponse(
    bundle = networkBundle(),
    items = listOf(networkTodo(bundleId = null))
)

private fun mutationResponse(assignedTodoId: String) = NetworkAssignedTodoMutationResponse(
    item = networkTodo(id = assignedTodoId),
    bundle = networkBundle()
)

private fun networkBundle() = NetworkAssignmentBundle(
    id = "bundle-1",
    sender = NetworkAssignmentUser(id = "user-id", nickname = "neo"),
    receiver = NetworkAssignmentUser(id = "friend-1", nickname = "monday"),
    status = "SENT",
    summary = summary(totalCount = 1)
)

private fun networkTodo(
    id: String = "assigned-1",
    bundleId: String? = "bundle-1"
) = NetworkAssignedTodo(
    id = id,
    bundleId = bundleId,
    sender = NetworkAssignmentUser(id = "friend-1", nickname = "monday"),
    receiver = NetworkAssignmentUser(id = "user-id", nickname = "neo"),
    title = "Shared todo",
    description = null,
    dueDate = "2026-05-10",
    dueTimeMinutes = 14 * 60 + 30,
    priority = "MEDIUM",
    category = null,
    status = "PENDING_ACCEPTANCE",
    progressPercent = 20,
    checklist = listOf(
        NetworkAssignedTodoChecklistItem(
            id = "check-1",
            title = "Step",
            completed = false
        )
    ),
    reminder = NetworkAssignedTodoReminder(
        reminderAt = "2026-05-10T14:00:00Z",
        enabled = true
    )
)

private fun summary(totalCount: Int) = NetworkAssignmentSummary(
    totalCount = totalCount,
    pendingCount = 0,
    acceptedCount = totalCount,
    doneCount = 0,
    rejectedCount = 0,
    canceledCount = 0,
    progressPercent = 20
)
