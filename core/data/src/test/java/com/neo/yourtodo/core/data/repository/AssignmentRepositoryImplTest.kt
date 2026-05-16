package com.neo.yourtodo.core.data.repository

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.database.dao.AssignedTodoDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.dao.TodoOutboxDao
import com.neo.yourtodo.core.database.entity.AssignedTodoChecklistItemEntity
import com.neo.yourtodo.core.database.entity.AssignedTodoEntity
import com.neo.yourtodo.core.database.entity.AssignedTodoWithChecklist
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.database.entity.TodoOutboxEntity
import com.neo.yourtodo.core.database.entity.assignedTodoCacheKey
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedCacheKey
import com.neo.yourtodo.core.domain.repository.AssignmentFeedCachePolicy
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.network.assignments.AssignmentAuthRequiredException
import com.neo.yourtodo.core.network.assignments.AssignmentNetworkDataSource
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodo
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoChecklistItem
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoMutationItem
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoMutationResponse
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoReminder
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodoReminderResponse
import com.neo.yourtodo.core.network.assignments.NetworkAssignedTodosResponse
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentBundle
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentBundleResponse
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentDecision
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentSummary
import com.neo.yourtodo.core.network.assignments.NetworkAssignmentUser
import com.neo.yourtodo.core.network.assignments.NetworkCreateAssignmentBundleRequest
import com.neo.yourtodo.core.network.assignments.NetworkDecideAssignmentItemsRequest
import com.neo.yourtodo.core.network.assignments.NetworkDirectAssignmentConsentSummary
import com.neo.yourtodo.core.network.assignments.NetworkDirectAssignmentConsentSummaryResponse
import com.neo.yourtodo.core.network.assignments.NetworkFriendAssignmentSummaryResponse
import com.neo.yourtodo.core.network.assignments.NetworkSetDirectAssignmentOptInRequest
import com.neo.yourtodo.core.network.assignments.NetworkUpsertAssignedTodoReminderRequest
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import com.neo.yourtodo.core.network.auth.NetworkAuthUser
import com.neo.yourtodo.core.network.auth.NetworkAuthUserResponse
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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
        assertThat(network.lastCreateRequest!!.assignmentMode).isEqualTo("REQUEST")
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
    fun createDirectBundleMapsAssignmentModeAndCachesMode() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeAssignmentNetworkDataSource().apply {
            bundleItem = networkTodo(
                bundleId = "bundle-1",
                assignmentMode = "DIRECT",
                status = "ACCEPTED"
            )
        }
        val assignedTodoDao = FakeAssignedTodoDao()
        val repository = repository(prefs = prefs, network = network, assignedTodoDao = assignedTodoDao)

        repository.createBundle(
            receiverUserId = "receiver-1",
            items = listOf(
                AssignmentDraftItem(
                    title = "Direct task",
                    description = null,
                    dueDate = null,
                    priority = TodoPriority.MEDIUM,
                    category = null
                )
            ),
            assignmentMode = AssignmentMode.DIRECT
        ).getOrThrow()

        assertThat(network.lastCreateRequest!!.assignmentMode).isEqualTo("DIRECT")
        val observed = repository.observeSentAssignedTodos(AssignmentFeedStatus.ACTIVE).first().single()
        assertThat(observed.assignmentMode).isEqualTo(AssignmentMode.DIRECT)
        assertThat(observed.status.name).isEqualTo("ACCEPTED")
    }

    @Test
    fun directAssignmentOptInMapsSummary() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeAssignmentNetworkDataSource()
        val repository = repository(prefs = prefs, network = network)

        val enabled = repository.setDirectAssignmentOptIn("friend-1", true).getOrThrow()
        val disabled = repository.setDirectAssignmentOptIn("friend-1", false).getOrThrow()

        assertThat(enabled.grantedByMe.name).isEqualTo("ACTIVE")
        assertThat(disabled.grantedByMe.name).isEqualTo("NONE")
        assertThat(network.directAssignmentOptInRequests)
            .containsExactly("friend-1" to true, "friend-1" to false)
            .inOrder()
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
        val reopened = repository.reopenAssignedTodo("assigned-1").getOrThrow()
        val deleted = repository.deleteReceivedAssignedTodo("assigned-1").getOrThrow()
        val canceled = repository.cancelAssignedTodo("assigned-1").getOrThrow()

        assertThat(summary.friendUserId).isEqualTo("friend-1")
        assertThat(summary.sent.totalCount).isEqualTo(2)
        assertThat(network.lastFriendDirection).isEqualTo("sent")
        assertThat(network.lastFriendStatus).isEqualTo("active")
        assertThat(network.lastReceivedStatus).isEqualTo("pending")
        assertThat(network.lastSentStatus).isEqualTo("history")
        assertThat(friendItems.single().dueTimeMinutes).isEqualTo(14 * 60 + 30)
        assertThat(friendItems.single().completedAt).isEqualTo(Instant.parse("2026-05-09T00:00:00Z"))
        assertThat(received.single().sender?.nickname).isEqualTo("monday")
        assertThat(sent.single().receiver?.nickname).isEqualTo("neo")
        assertThat(network.lastDecisionRequest!!.decisions.single())
            .isEqualTo(NetworkAssignmentDecision("assigned-1", "ACCEPT"))
        assertThat(decided.items.single().title).isEqualTo("Shared todo")
        assertThat(completed.id).isEqualTo("assigned-1")
        assertThat(reopened.id).isEqualTo("assigned-1")
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

    @Test
    fun requestRethrowsCancellationAndKeepsFeedFreshness() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val tracker = AssignmentFeedFreshnessTracker()
        val feed = AssignmentFeedCacheKey(
            direction = AssignmentDirection.RECEIVED,
            status = AssignmentFeedStatus.ACTIVE
        )
        tracker.recordRefresh(
            ownerUserId = "user-id",
            feed = feed,
            refreshedAt = 123L
        )
        val network = FakeAssignmentNetworkDataSource().apply {
            receivedException = CancellationException("cancelled")
        }
        val repository = repository(
            prefs = prefs,
            network = network,
            assignmentFeedFreshnessTracker = tracker
        )

        var cancellationThrown = false
        try {
            repository.getReceivedAssignedTodos(AssignmentFeedStatus.ACTIVE)
        } catch (_: CancellationException) {
            cancellationThrown = true
        }

        assertThat(cancellationThrown).isTrue()
        assertThat(tracker.observeRefreshTime("user-id", feed).first()).isEqualTo(123L)
        assertThat(prefs.authSession.first()).isNotNull()
    }

    @Test
    fun getReceivedAssignedTodosCachesItemsForObservers() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val assignedTodoDao = FakeAssignedTodoDao()
        val repository = repository(prefs = prefs, assignedTodoDao = assignedTodoDao)

        repository.getReceivedAssignedTodos(AssignmentFeedStatus.PENDING).getOrThrow()

        val observed = repository.observeReceivedAssignedTodos(AssignmentFeedStatus.PENDING).first()
        assertThat(observed.map { it.id }).containsExactly("assigned-1")
        assertThat(observed.single().checklist.single().title).isEqualTo("Step")
        assertThat(observed.single().sender?.nickname).isEqualTo("monday")
    }

    @Test
    fun completeAssignedTodoMergesPartialMutationResponseWithCachedItem() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val assignedTodoDao = FakeAssignedTodoDao()
        val network = FakeAssignmentNetworkDataSource().apply {
            mutationItem = NetworkAssignedTodoMutationItem(
                id = "assigned-1",
                status = "DONE",
                progressPercent = 100,
                completedAt = "2026-05-09T00:00:00Z"
            )
        }
        val repository = repository(prefs = prefs, network = network, assignedTodoDao = assignedTodoDao)
        repository.getReceivedAssignedTodos(AssignmentFeedStatus.PENDING).getOrThrow()

        val completed = repository.completeAssignedTodo("assigned-1").getOrThrow()

        assertThat(completed.title).isEqualTo("Shared todo")
        assertThat(completed.sender?.nickname).isEqualTo("monday")
        assertThat(completed.status.name).isEqualTo("DONE")
        val observed = repository.observeReceivedAssignedTodos(AssignmentFeedStatus.HISTORY).first().single()
        assertThat(observed.title).isEqualTo("Shared todo")
        assertThat(observed.checklist.single().title).isEqualTo("Step")
        assertThat(observed.status.name).isEqualTo("DONE")
    }

    @Test
    fun completeAssignedTodoReplacesCachedChecklistWithEmptyMutationChecklist() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val assignedTodoDao = FakeAssignedTodoDao()
        val network = FakeAssignmentNetworkDataSource().apply {
            mutationItem = NetworkAssignedTodoMutationItem(
                id = "assigned-1",
                status = "DONE",
                progressPercent = 100,
                checklist = emptyList(),
                completedAt = "2026-05-09T00:00:00Z"
            )
        }
        val repository = repository(prefs = prefs, network = network, assignedTodoDao = assignedTodoDao)
        repository.getReceivedAssignedTodos(AssignmentFeedStatus.PENDING).getOrThrow()
        assertThat(repository.observeReceivedAssignedTodos(AssignmentFeedStatus.PENDING).first().single().checklist)
            .hasSize(1)

        repository.completeAssignedTodo("assigned-1").getOrThrow()

        val observed = repository.observeReceivedAssignedTodos(AssignmentFeedStatus.HISTORY).first().single()
        assertThat(observed.status.name).isEqualTo("DONE")
        assertThat(observed.checklist).isEmpty()
    }

    @Test
    fun hideReceivedAssignedTodoFromTaskSurfaceKeepsFriendHistoryCache() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val assignedTodoDao = FakeAssignedTodoDao()
        val repository = repository(prefs = prefs, assignedTodoDao = assignedTodoDao)

        repository.getReceivedAssignedTodos(AssignmentFeedStatus.PENDING).getOrThrow()
        assertThat(repository.observeReceivedAssignedTodos(AssignmentFeedStatus.PENDING).first().map { it.id })
            .containsExactly("assigned-1")

        repository.hideReceivedAssignedTodoFromTaskSurface("assigned-1").getOrThrow()

        assertThat(repository.observeReceivedAssignedTodos(AssignmentFeedStatus.PENDING).first()).isEmpty()
        assertThat(
            repository.observeFriendAssignedTodos(
                friendUserId = "friend-1",
                direction = AssignmentDirection.RECEIVED,
                status = AssignmentFeedStatus.PENDING
            ).first().map { it.id }
        ).containsExactly("assigned-1")
    }

    @Test
    fun assignedTodoObserversAreScopedToCurrentUser() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val assignedTodoDao = FakeAssignedTodoDao()
        val repository = repository(prefs = prefs, assignedTodoDao = assignedTodoDao)

        repository.getReceivedAssignedTodos(AssignmentFeedStatus.PENDING).getOrThrow()
        prefs.saveAuthSession(authSession(accessToken = "other-access", userId = "other-user"))

        assertThat(repository.observeReceivedAssignedTodos(AssignmentFeedStatus.PENDING).first()).isEmpty()

        prefs.saveAuthSession(authSession())

        assertThat(repository.observeReceivedAssignedTodos(AssignmentFeedStatus.PENDING).first().map { it.id })
            .containsExactly("assigned-1")
    }

    @Test
    fun getReceivedAssignedTodosRecordsFeedFreshnessForEmptyRefresh() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeAssignmentNetworkDataSource().apply {
            receivedItems = emptyList()
        }
        val assignedTodoDao = FakeAssignedTodoDao()
        val repository = repository(prefs = prefs, network = network, assignedTodoDao = assignedTodoDao)
        val feed = AssignmentFeedCacheKey(
            direction = AssignmentDirection.RECEIVED,
            status = AssignmentFeedStatus.ACTIVE
        )

        val beforeRefresh = System.currentTimeMillis()
        repository.getReceivedAssignedTodos(AssignmentFeedStatus.ACTIVE).getOrThrow()
        val afterRefresh = System.currentTimeMillis()

        val freshness = repository.observeFeedCacheFreshness(feed).first()
        val lastUpdatedAt = freshness.lastUpdatedAtEpochMillis
        assertThat(lastUpdatedAt).isNotNull()
        assertThat(lastUpdatedAt!!).isAtLeast(beforeRefresh)
        assertThat(lastUpdatedAt).isAtMost(afterRefresh)
        assertThat(freshness.isStale(lastUpdatedAt + AssignmentFeedCachePolicy.STALE_AFTER_MILLIS - 1))
            .isFalse()
        assertThat(freshness.shouldForceRefresh(lastUpdatedAt + AssignmentFeedCachePolicy.STALE_AFTER_MILLIS))
            .isTrue()
    }

    @Test
    fun emptyFeedFreshnessSurvivesNewRepositoryAndTrackerInstances() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val network = FakeAssignmentNetworkDataSource().apply {
            receivedItems = emptyList()
        }
        val feed = AssignmentFeedCacheKey(
            direction = AssignmentDirection.RECEIVED,
            status = AssignmentFeedStatus.ACTIVE
        )

        repository(prefs = prefs, network = network)
            .getReceivedAssignedTodos(AssignmentFeedStatus.ACTIVE)
            .getOrThrow()
        val recordedAt = prefs.assignmentFeedRefreshTimes.value.values.single()
        val restartedRepository = repository(
            prefs = prefs,
            network = network,
            assignedTodoDao = FakeAssignedTodoDao(),
            assignmentFeedFreshnessTracker = AssignmentFeedFreshnessTracker()
        )

        val freshnessAfterRestart = restartedRepository.observeFeedCacheFreshness(feed).first()

        assertThat(freshnessAfterRestart.lastUpdatedAtEpochMillis).isEqualTo(recordedAt)
        assertThat(freshnessAfterRestart.isStale(recordedAt + AssignmentFeedCachePolicy.STALE_AFTER_MILLIS - 1))
            .isFalse()
    }

    @Test
    fun observeFeedCacheFreshnessUsesPersistedCacheUpdatedAtForCachedRows() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val assignedTodoDao = FakeAssignedTodoDao()
        val repository = repository(prefs = prefs, assignedTodoDao = assignedTodoDao)

        assignedTodoDao.upsertAssignedTodoGraph(
            items = listOf(
                cachedAssignedTodo(id = "newer", cacheUpdatedAt = 200L),
                cachedAssignedTodo(id = "older", cacheUpdatedAt = 100L)
            ),
            checklistItems = emptyList()
        )

        val freshness = repository.observeFeedCacheFreshness(
            AssignmentFeedCacheKey(
                direction = AssignmentDirection.RECEIVED,
                status = AssignmentFeedStatus.ACTIVE
            )
        ).first()

        assertThat(freshness.lastUpdatedAtEpochMillis).isEqualTo(100L)
        assertThat(freshness.isStale(100L + AssignmentFeedCachePolicy.STALE_AFTER_MILLIS))
            .isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeFeedCacheFreshnessDoesNotEmitForUnrelatedFeedRefresh() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val assignedTodoDao = FakeAssignedTodoDao()
        val repository = repository(prefs = prefs, assignedTodoDao = assignedTodoDao)
        val feed = AssignmentFeedCacheKey(
            direction = AssignmentDirection.RECEIVED,
            status = AssignmentFeedStatus.ACTIVE
        )
        val emissions = mutableListOf<Long?>()

        backgroundScope.launch {
            repository.observeFeedCacheFreshness(feed)
                .collect { freshness -> emissions += freshness.lastUpdatedAtEpochMillis }
        }
        runCurrent()

        assertThat(emissions).containsExactly(null)

        repository.getReceivedAssignedTodos(AssignmentFeedStatus.PENDING).getOrThrow()
        runCurrent()

        assertThat(emissions).containsExactly(null)
    }

    @Test
    fun signOutClearsInMemoryFeedFreshnessForSameUserRelogin() = runTest {
        val prefs = FakePreferencesDataSource().apply { saveAuthSession(authSession()) }
        val assignedTodoDao = FakeAssignedTodoDao()
        val assignmentFeedFreshnessTracker = AssignmentFeedFreshnessTracker()
        val repository = repository(
            prefs = prefs,
            assignedTodoDao = assignedTodoDao,
            assignmentFeedFreshnessTracker = assignmentFeedFreshnessTracker
        )
        val authRepository = AuthRepositoryImpl(
            networkDataSource = FakeAuthNetworkDataSource(),
            preferencesDataSource = prefs,
            todoDao = NoOpTodoDao(),
            todoOutboxDao = NoOpTodoOutboxDao(),
            assignedTodoDao = assignedTodoDao,
            assignmentFeedFreshnessTracker = assignmentFeedFreshnessTracker
        )
        val feed = AssignmentFeedCacheKey(
            direction = AssignmentDirection.RECEIVED,
            status = AssignmentFeedStatus.ACTIVE
        )

        repository.getReceivedAssignedTodos(AssignmentFeedStatus.ACTIVE).getOrThrow()
        assertThat(repository.observeFeedCacheFreshness(feed).first().lastUpdatedAtEpochMillis)
            .isNotNull()

        authRepository.signOut()
        prefs.saveAuthSession(authSession())

        val freshnessAfterRelogin = repository.observeFeedCacheFreshness(feed).first()
        assertThat(freshnessAfterRelogin.lastUpdatedAtEpochMillis).isNull()
        assertThat(freshnessAfterRelogin.shouldForceRefresh(System.currentTimeMillis())).isTrue()
    }

    private fun repository(
        prefs: FakePreferencesDataSource = FakePreferencesDataSource(),
        network: FakeAssignmentNetworkDataSource = FakeAssignmentNetworkDataSource(),
        authNetwork: FakeAuthNetworkDataSource = FakeAuthNetworkDataSource(),
        assignedTodoDao: FakeAssignedTodoDao = FakeAssignedTodoDao(),
        assignmentFeedFreshnessTracker: AssignmentFeedFreshnessTracker = AssignmentFeedFreshnessTracker()
    ) = AssignmentRepositoryImpl(
        userPreferencesDataSource = prefs,
        assignmentNetworkDataSource = network,
        assignedTodoDao = assignedTodoDao,
        assignmentFeedFreshnessTracker = assignmentFeedFreshnessTracker,
        authSessionRefresher = AuthSessionRefresher(
            prefs,
            authNetwork,
            assignmentFeedFreshnessTracker
        )
    )

    private fun authSession(
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
        userId: String = "user-id"
    ) = AuthSessionData(
        accessToken = accessToken,
        refreshToken = refreshToken,
        userId = userId,
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
        val assignmentFeedRefreshTimes = MutableStateFlow<Map<String, Long>>(emptyMap())

        override val authSession: Flow<AuthSessionData?> = authSessionFlow.asStateFlow()
        override val selectedTodoFilter: Flow<TodoFilter> = filterFlow.asStateFlow()
        override val selectedTodoCategoryFilter: Flow<Long?> = categoryFilterFlow.asStateFlow()
        override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> = priorityFilterFlow.asStateFlow()
        override val todoSyncCursor: Flow<String?> = syncCursorFlow.asStateFlow()
        override val todoSyncHaltReason: Flow<String?> = syncHaltReasonFlow.asStateFlow()

        override fun observeAssignmentFeedRefreshTime(feedKey: String): Flow<Long?> =
            assignmentFeedRefreshTimes.map { refreshTimes -> refreshTimes[feedKey] }

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

        override suspend fun setAssignmentFeedRefreshTime(feedKey: String, refreshedAtEpochMillis: Long) {
            assignmentFeedRefreshTimes.value = assignmentFeedRefreshTimes.value + (feedKey to refreshedAtEpochMillis)
        }

        override suspend fun clearAssignmentFeedRefreshTimes() {
            assignmentFeedRefreshTimes.value = emptyMap()
        }
    }

    private class FakeAssignedTodoDao : AssignedTodoDao {
        private val state = MutableStateFlow(CacheState())

        override fun observeReceivedAssignedTodos(
            ownerUserId: String,
            statuses: List<String>
        ): Flow<List<AssignedTodoWithChecklist>> =
            state.map { cache ->
                cache.items.values
                    .filter {
                        it.ownerUserId == ownerUserId &&
                            it.receivedCached &&
                            !it.receivedTaskHidden &&
                            it.status in statuses
                    }
                    .toWithChecklist(cache)
            }

        override fun observeSentAssignedTodos(
            ownerUserId: String,
            statuses: List<String>
        ): Flow<List<AssignedTodoWithChecklist>> =
            state.map { cache ->
                cache.items.values
                    .filter { it.ownerUserId == ownerUserId && it.sentCached && it.status in statuses }
                    .toWithChecklist(cache)
            }

        override fun observeSentAssignedTodosByFriend(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ): Flow<List<AssignedTodoWithChecklist>> =
            state.map { cache ->
                cache.items.values
                    .filter {
                        it.ownerUserId == ownerUserId &&
                            it.sentCached &&
                            it.receiverUserId == friendUserId &&
                            it.status in statuses
                    }
                    .toWithChecklist(cache)
            }

        override fun observeReceivedAssignedTodosByFriend(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ): Flow<List<AssignedTodoWithChecklist>> =
            state.map { cache ->
                cache.items.values
                    .filter {
                        it.ownerUserId == ownerUserId &&
                            it.receivedCached &&
                            it.senderUserId == friendUserId &&
                            it.status in statuses
                    }
                    .toWithChecklist(cache)
            }

        override suspend fun getAssignedTodoById(ownerUserId: String, id: String): AssignedTodoEntity? =
            state.value.items.values.firstOrNull { it.ownerUserId == ownerUserId && it.id == id }

        override fun observeReceivedFeedCacheUpdatedAt(
            ownerUserId: String,
            statuses: List<String>
        ): Flow<Long?> =
            observeCacheUpdatedAt {
                it.ownerUserId == ownerUserId &&
                    it.receivedCached &&
                    !it.receivedTaskHidden &&
                    it.status in statuses
            }

        override fun observeSentFeedCacheUpdatedAt(
            ownerUserId: String,
            statuses: List<String>
        ): Flow<Long?> =
            observeCacheUpdatedAt {
                it.ownerUserId == ownerUserId &&
                    it.sentCached &&
                    it.status in statuses
            }

        override fun observeSentFriendFeedCacheUpdatedAt(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ): Flow<Long?> =
            observeCacheUpdatedAt {
                it.ownerUserId == ownerUserId &&
                    it.sentCached &&
                    it.receiverUserId == friendUserId &&
                    it.status in statuses
            }

        override fun observeReceivedFriendFeedCacheUpdatedAt(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ): Flow<Long?> =
            observeCacheUpdatedAt {
                it.ownerUserId == ownerUserId &&
                    it.receivedCached &&
                    it.senderUserId == friendUserId &&
                    it.status in statuses
            }

        override suspend fun deleteByOwner(ownerUserId: String) {
            deleteWhere { it.ownerUserId == ownerUserId }
        }

        override suspend fun upsertAssignedTodos(items: List<AssignedTodoEntity>) {
            state.value = state.value.copy(
                items = state.value.items + items.associateBy { it.cacheKey }
            )
        }

        override suspend fun upsertChecklistItems(items: List<AssignedTodoChecklistItemEntity>) {
            state.value = state.value.copy(
                checklist = state.value.checklist + items.groupBy { it.assignedTodoCacheKey }
            )
        }

        override suspend fun deleteChecklistItems(assignedTodoCacheKeys: List<String>) {
            state.value = state.value.copy(checklist = state.value.checklist - assignedTodoCacheKeys.toSet())
        }

        override suspend fun deleteReceivedByStatuses(ownerUserId: String, statuses: List<String>) {
            deleteWhere { it.ownerUserId == ownerUserId && it.receivedCached && it.status in statuses }
        }

        override suspend fun hideReceivedByStatuses(ownerUserId: String, statuses: List<String>) {
            hideWhere { it.ownerUserId == ownerUserId && it.receivedCached && it.status in statuses }
        }

        override suspend fun hideReceivedFromTaskSurface(ownerUserId: String, id: String) {
            hideWhere { it.ownerUserId == ownerUserId && it.id == id && it.receivedCached }
        }

        override suspend fun deleteReceivedByStatusesExcept(
            ownerUserId: String,
            statuses: List<String>,
            ids: List<String>
        ) {
            deleteWhere { it.ownerUserId == ownerUserId && it.receivedCached && it.status in statuses && it.id !in ids }
        }

        override suspend fun hideReceivedByStatusesExcept(ownerUserId: String, statuses: List<String>, ids: List<String>) {
            hideWhere { it.ownerUserId == ownerUserId && it.receivedCached && it.status in statuses && it.id !in ids }
        }

        override suspend fun deleteSentByStatuses(ownerUserId: String, statuses: List<String>) {
            deleteWhere { it.ownerUserId == ownerUserId && it.sentCached && it.status in statuses }
        }

        override suspend fun deleteSentByStatusesExcept(ownerUserId: String, statuses: List<String>, ids: List<String>) {
            deleteWhere { it.ownerUserId == ownerUserId && it.sentCached && it.status in statuses && it.id !in ids }
        }

        override suspend fun deleteSentByFriendAndStatuses(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ) {
            deleteWhere {
                it.ownerUserId == ownerUserId &&
                    it.sentCached &&
                    it.receiverUserId == friendUserId &&
                    it.status in statuses
            }
        }

        override suspend fun deleteSentByFriendAndStatusesExcept(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>,
            ids: List<String>
        ) {
            deleteWhere {
                it.ownerUserId == ownerUserId &&
                    it.sentCached &&
                    it.receiverUserId == friendUserId &&
                    it.status in statuses &&
                    it.id !in ids
            }
        }

        override suspend fun deleteReceivedByFriendAndStatuses(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>
        ) {
            deleteWhere {
                it.ownerUserId == ownerUserId &&
                    it.receivedCached &&
                    it.senderUserId == friendUserId &&
                    it.status in statuses
            }
        }

        override suspend fun deleteReceivedByFriendAndStatusesExcept(
            ownerUserId: String,
            friendUserId: String,
            statuses: List<String>,
            ids: List<String>
        ) {
            deleteWhere {
                it.ownerUserId == ownerUserId &&
                    it.receivedCached &&
                    it.senderUserId == friendUserId &&
                    it.status in statuses &&
                    it.id !in ids
            }
        }

        private fun deleteWhere(predicate: (AssignedTodoEntity) -> Boolean) {
            val removedCacheKeys = state.value.items.values
                .filter(predicate)
                .map { it.cacheKey }
                .toSet()
            state.value = state.value.copy(
                items = state.value.items.filterKeys { it !in removedCacheKeys },
                checklist = state.value.checklist.filterKeys { it !in removedCacheKeys }
            )
        }

        private fun hideWhere(predicate: (AssignedTodoEntity) -> Boolean) {
            state.value = state.value.copy(
                items = state.value.items.mapValues { (_, item) ->
                    if (predicate(item)) item.copy(receivedTaskHidden = true) else item
                }
            )
        }

        private fun observeCacheUpdatedAt(
            predicate: (AssignedTodoEntity) -> Boolean
        ): Flow<Long?> =
            state.map { cache ->
                cache.items.values
                    .filter(predicate)
                    .minOfOrNull { it.cacheUpdatedAt }
            }

        private fun Collection<AssignedTodoEntity>.toWithChecklist(
            cache: CacheState
        ): List<AssignedTodoWithChecklist> =
            sortedWith(compareByDescending<AssignedTodoEntity> { it.createdAtEpochMillis ?: 0L }.thenBy { it.id })
                .map { item ->
                    AssignedTodoWithChecklist(
                        assignedTodo = item,
                        checklist = cache.checklist[item.cacheKey].orEmpty()
                    )
                }
    }

    private data class CacheState(
        val items: Map<String, AssignedTodoEntity> = emptyMap(),
        val checklist: Map<String, List<AssignedTodoChecklistItemEntity>> = emptyMap()
    )

    private class NoOpTodoDao : TodoDao {
        override fun observeTodos(): Flow<List<TodoEntity>> = flowOf(emptyList())
        override fun observeTodosByDueDateRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TodoEntity>> =
            flowOf(emptyList())

        override suspend fun insert(todo: TodoEntity): Long = todo.id
        override suspend fun update(todo: TodoEntity) = Unit
        override suspend fun delete(todo: TodoEntity) = Unit
        override suspend fun getTodoById(id: Long): TodoEntity? = null
        override suspend fun getTodosByIds(ids: List<Long>): List<TodoEntity> = emptyList()
        override suspend fun getTodoByServerId(ownerUserId: String, serverId: String): TodoEntity? = null
        override suspend fun getTodoByClientId(ownerUserId: String, clientId: String): TodoEntity? = null
        override suspend fun deleteSyncedTodosByOwner(ownerUserId: String) = Unit
        override suspend fun getTodosWithActiveReminder(): List<TodoEntity> = emptyList()
    }

    private class NoOpTodoOutboxDao : TodoOutboxDao {
        override suspend fun getPendingMutations(ownerUserId: String): List<TodoOutboxEntity> = emptyList()
        override suspend fun getByTodoLocalId(todoLocalId: Long): TodoOutboxEntity? = null
        override suspend fun insert(outbox: TodoOutboxEntity): Long = outbox.id
        override suspend fun update(outbox: TodoOutboxEntity) = Unit
        override suspend fun delete(outbox: TodoOutboxEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun deleteByTodoLocalId(todoLocalId: Long) = Unit
        override suspend fun deleteByOwner(ownerUserId: String) = Unit
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
        var lastReminderRequest: NetworkUpsertAssignedTodoReminderRequest? = null
        var deletedReminderTodoId: String? = null
        var mutationItem: NetworkAssignedTodoMutationItem? = null
        var bundleItem: NetworkAssignedTodo? = null
        var receivedItems: List<NetworkAssignedTodo> = listOf(networkTodo())
        var receivedException: Throwable? = null
        val directAssignmentOptInRequests = mutableListOf<Pair<String, Boolean>>()

        override suspend fun createBundle(
            accessToken: String,
            idempotencyKey: String,
            request: NetworkCreateAssignmentBundleRequest
        ): NetworkAssignmentBundleResponse {
            createTokens += accessToken
            failAuthIfNeeded()
            lastCreateRequest = request
            return bundleResponse(item = bundleItem)
        }

        override suspend fun getReceivedAssignedTodos(
            accessToken: String,
            status: String
        ): NetworkAssignedTodosResponse {
            receivedTokens += accessToken
            receivedException?.let { throw it }
            failAuthIfNeeded()
            lastReceivedStatus = status
            return NetworkAssignedTodosResponse(items = receivedItems)
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

        override suspend fun setDirectAssignmentOptIn(
            accessToken: String,
            idempotencyKey: String,
            friendUserId: String,
            request: NetworkSetDirectAssignmentOptInRequest
        ): NetworkDirectAssignmentConsentSummaryResponse {
            failAuthIfNeeded()
            directAssignmentOptInRequests += friendUserId to request.enabled
            return NetworkDirectAssignmentConsentSummaryResponse(
                directAssignment = NetworkDirectAssignmentConsentSummary(
                    canFriendDirectAssignToMe = request.enabled,
                    canDirectAssignToFriend = false
                )
            )
        }

        override suspend fun completeAssignedTodo(
            accessToken: String,
            assignedTodoId: String
        ): NetworkAssignedTodoMutationResponse {
            failAuthIfNeeded()
            return mutationResponse(assignedTodoId, mutationItem)
        }

        override suspend fun reopenAssignedTodo(
            accessToken: String,
            assignedTodoId: String
        ): NetworkAssignedTodoMutationResponse {
            failAuthIfNeeded()
            return mutationResponse(assignedTodoId, mutationItem)
        }

        override suspend fun deleteReceivedAssignedTodo(
            accessToken: String,
            idempotencyKey: String,
            assignedTodoId: String
        ): NetworkAssignedTodoMutationResponse {
            failAuthIfNeeded()
            return mutationResponse(assignedTodoId, mutationItem)
        }

        override suspend fun cancelAssignedTodo(
            accessToken: String,
            idempotencyKey: String,
            assignedTodoId: String
        ): NetworkAssignedTodoMutationResponse {
            failAuthIfNeeded()
            return mutationResponse(assignedTodoId, mutationItem)
        }

        override suspend fun upsertAssignedTodoReminder(
            accessToken: String,
            assignedTodoId: String,
            request: NetworkUpsertAssignedTodoReminderRequest
        ): NetworkAssignedTodoReminderResponse {
            failAuthIfNeeded()
            lastReminderRequest = request
            return NetworkAssignedTodoReminderResponse(
                reminder = NetworkAssignedTodoReminder(
                    reminderAt = request.reminderAt,
                    enabled = request.enabled
                )
            )
        }

        override suspend fun deleteAssignedTodoReminder(
            accessToken: String,
            assignedTodoId: String
        ): NetworkAssignedTodoReminderResponse {
            failAuthIfNeeded()
            deletedReminderTodoId = assignedTodoId
            return NetworkAssignedTodoReminderResponse(reminder = null)
        }

        private fun failAuthIfNeeded() {
            if (authFailuresRemaining > 0) {
                authFailuresRemaining -= 1
                throw AssignmentAuthRequiredException()
            }
        }
    }
}

private fun cachedAssignedTodo(
    id: String,
    cacheUpdatedAt: Long,
    ownerUserId: String = "user-id",
    status: String = "ACCEPTED"
) = AssignedTodoEntity(
    ownerUserId = ownerUserId,
    id = id,
    cacheKey = assignedTodoCacheKey(ownerUserId, id),
    bundleId = "bundle-1",
    title = "Cached $id",
    description = null,
    dueDateEpochDay = null,
    dueTimeMinutes = null,
    priority = TodoPriority.MEDIUM.name,
    category = null,
    status = status,
    terminalReason = null,
    progressPercent = 0,
    senderUserId = "friend-1",
    senderNickname = "monday",
    receiverUserId = ownerUserId,
    receiverNickname = "neo",
    assignmentMode = AssignmentMode.REQUEST.name,
    reminderAt = null,
    reminderEnabled = null,
    createdAtEpochMillis = null,
    completedAtEpochMillis = null,
    receivedCached = true,
    receivedTaskHidden = false,
    sentCached = false,
    cacheUpdatedAt = cacheUpdatedAt
)

private fun bundleResponse(item: NetworkAssignedTodo? = null) = NetworkAssignmentBundleResponse(
    bundle = networkBundle(),
    items = listOf(item ?: networkTodo(bundleId = null))
)

private fun mutationResponse(
    assignedTodoId: String,
    item: NetworkAssignedTodoMutationItem? = null
) = NetworkAssignedTodoMutationResponse(
    item = item ?: networkMutationTodo(id = assignedTodoId)
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
    bundleId: String? = "bundle-1",
    assignmentMode: String? = null,
    status: String = "PENDING_ACCEPTANCE"
) = NetworkAssignedTodo(
    id = id,
    bundleId = bundleId,
    assignmentMode = assignmentMode,
    sender = NetworkAssignmentUser(id = "friend-1", nickname = "monday"),
    receiver = NetworkAssignmentUser(id = "user-id", nickname = "neo"),
    title = "Shared todo",
    description = null,
    dueDate = "2026-05-10",
    dueTimeMinutes = 14 * 60 + 30,
    priority = "MEDIUM",
    category = null,
    status = status,
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
    ),
    completedAt = "2026-05-09T00:00:00Z"
)

private fun networkMutationTodo(id: String = "assigned-1") = NetworkAssignedTodoMutationItem(
    id = id,
    bundleId = "bundle-1",
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
    ),
    completedAt = "2026-05-09T00:00:00Z"
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
