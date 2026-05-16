package com.neo.yourtodo.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoUser
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test

class RefreshWorkspaceUseCaseTest {

    @Test
    fun invokePublishesWorkspaceSnapshot() = runTest {
        val notifier = WorkspaceSyncNotifier()
        val friend = testFriend()
        val assignedTodo = testAssignedTodo()
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(events = mutableListOf()),
            friendRepository = FakeFriendRepository(friends = listOf(friend)),
            assignmentRepository = FakeAssignmentRepository(activeReceived = listOf(assignedTodo)),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(),
            syncNotifier = notifier
        )

        notifier.snapshots.test {
            assertThat(awaitItem()).isNull()

            val result = useCase()

            assertThat(result.isSuccess).isTrue()
            val snapshot = awaitItem()
            assertThat(snapshot?.friends).containsExactly(friend)
            assertThat(snapshot?.visibleReceivedAssignedTodos).containsExactly(assignedTodo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun invokeRunsTodoSyncBeforeOtherWorkspaceRequests() = runTest {
        val events = mutableListOf<String>()
        val calendarWidgetUpdater = RecordingCalendarWidgetUpdater(events)
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(events = events),
            friendRepository = FakeFriendRepository(events = events),
            assignmentRepository = FakeAssignmentRepository(events = events),
            calendarWidgetUpdater = calendarWidgetUpdater
        )

        val result = useCase()

        assertThat(result.isSuccess).isTrue()
        assertThat(events.first()).isEqualTo("todo.sync")
        assertThat(events.drop(1)).containsAtLeast(
            "friend.getFriends",
            "friend.getIncoming",
            "friend.getOutgoing",
            "assignment.getReceived.pending",
            "assignment.getReceived.active",
            "assignment.getReceived.history"
        )
        assertThat(events.last()).isEqualTo("calendar.updateWidgets")
    }

    @Test
    fun invokeSharesConcurrentRefresh() = runTest {
        val events = mutableListOf<String>()
        val syncStarted = CompletableDeferred<Unit>()
        val allowSyncToFinish = CompletableDeferred<Unit>()
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(
                events = events,
                onSync = {
                    syncStarted.complete(Unit)
                    allowSyncToFinish.await()
                }
            ),
            friendRepository = FakeFriendRepository(events = events),
            assignmentRepository = FakeAssignmentRepository(events = events),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(events)
        )

        val firstRefresh = async { useCase() }
        syncStarted.await()
        val concurrentRefresh = async { useCase() }
        yield()

        allowSyncToFinish.complete(Unit)
        val results = awaitAll(firstRefresh, concurrentRefresh)

        assertThat(results.all { it.isSuccess }).isTrue()
        assertThat(events.count { it == "todo.sync" }).isEqualTo(1)
        assertThat(events.count { it == "calendar.updateWidgets" }).isEqualTo(1)
    }

    @Test
    fun invokeClearsInFlightAndCancelsWaiterWhenStarterIsCancelled() = runTest {
        val events = mutableListOf<String>()
        val firstSyncStarted = CompletableDeferred<Unit>()
        val firstSyncCancelled = CompletableDeferred<Unit>()
        var syncAttempts = 0
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(
                events = events,
                onSync = {
                    syncAttempts += 1
                    if (syncAttempts == 1) {
                        firstSyncStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            firstSyncCancelled.complete(Unit)
                        }
                    }
                }
            ),
            friendRepository = FakeFriendRepository(events = events),
            assignmentRepository = FakeAssignmentRepository(events = events),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(events)
        )

        val starter = async { useCase() }
        firstSyncStarted.await()
        val waitingCaller = async {
            runCatching { useCase() }.exceptionOrNull()
        }
        yield()

        starter.cancel(CancellationException("starter cancelled"))
        starter.join()
        firstSyncCancelled.await()

        assertThat(waitingCaller.await()).isInstanceOf(CancellationException::class.java)

        val retryResult = useCase()

        assertThat(retryResult.isSuccess).isTrue()
        assertThat(events.count { it == "todo.sync" }).isEqualTo(2)
        assertThat(events.count { it == "calendar.updateWidgets" }).isEqualTo(1)
    }

    @Test
    fun invokeSkipsFreshFullySyncedSnapshot() = runTest {
        val events = mutableListOf<String>()
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(events = events),
            friendRepository = FakeFriendRepository(events = events),
            assignmentRepository = FakeAssignmentRepository(events = events),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(events)
        )

        val firstResult = useCase()
        val skippedResult = useCase()

        assertThat(firstResult.isSuccess).isTrue()
        assertThat(skippedResult.getOrThrow()).isEqualTo(firstResult.getOrThrow())
        assertThat(events.count { it == "todo.sync" }).isEqualTo(1)
        assertThat(events.count { it == "calendar.updateWidgets" }).isEqualTo(1)
    }

    @Test
    fun invokeForceRefreshBypassesFreshSnapshotSkip() = runTest {
        val events = mutableListOf<String>()
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(events = events),
            friendRepository = FakeFriendRepository(events = events),
            assignmentRepository = FakeAssignmentRepository(events = events),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(events)
        )

        val firstResult = useCase()
        val forcedResult = useCase(forceRefresh = true)

        assertThat(firstResult.isSuccess).isTrue()
        assertThat(forcedResult.isSuccess).isTrue()
        assertThat(events.count { it == "todo.sync" }).isEqualTo(2)
        assertThat(events.count { it == "calendar.updateWidgets" }).isEqualTo(2)
    }

    @Test
    fun invokeDoesNotSkipPartialSnapshot() = runTest {
        val events = mutableListOf<String>()
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(
                events = events,
                syncResult = Result.failure(IllegalStateException("todo sync failed"))
            ),
            friendRepository = FakeFriendRepository(events = events),
            assignmentRepository = FakeAssignmentRepository(events = events),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(events)
        )

        val firstResult = useCase()
        val retryResult = useCase()

        assertThat(firstResult.getOrThrow().isFullySynced).isFalse()
        assertThat(retryResult.getOrThrow().isFullySynced).isFalse()
        assertThat(events.count { it == "todo.sync" }).isEqualTo(2)
        assertThat(events.count { it == "calendar.updateWidgets" }).isEqualTo(2)
    }

    @Test
    fun invokePublishesPartialSnapshotWhenOptionalFeedsFail() = runTest {
        val notifier = WorkspaceSyncNotifier()
        val friend = testFriend()
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(
                events = mutableListOf(),
                syncResult = Result.failure(IllegalStateException("todo sync failed"))
            ),
            friendRepository = FakeFriendRepository(friends = listOf(friend)),
            assignmentRepository = FakeAssignmentRepository(
                activeReceivedResult = Result.failure(IllegalStateException("active failed")),
                historyReceived = listOf(testAssignedTodo(id = "history-1"))
            ),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(),
            syncNotifier = notifier
        )

        val result = useCase()

        assertThat(result.isSuccess).isTrue()
        val snapshot = result.getOrThrow()
        assertThat(snapshot.isFullySynced).isFalse()
        assertThat(snapshot.friends).containsExactly(friend)
        assertThat(snapshot.visibleReceivedAssignedTodos)
            .containsExactly(testAssignedTodo(id = "history-1"))
        assertThat(notifier.snapshots.value).isEqualTo(snapshot)
    }

    @Test
    fun invokeFailsWithoutPublishingWhenRequiredFriendFeedFails() = runTest {
        val notifier = WorkspaceSyncNotifier()
        val events = mutableListOf<String>()
        val failure = IllegalStateException("friends failed")
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(events = events),
            friendRepository = FakeFriendRepository(
                events = events,
                friendsResult = Result.failure(failure)
            ),
            assignmentRepository = FakeAssignmentRepository(events = events),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(events),
            syncNotifier = notifier
        )

        val result = useCase()

        assertThat(result.exceptionOrNull()).isSameInstanceAs(failure)
        assertThat(notifier.snapshots.value).isNull()
        assertThat(events).doesNotContain("calendar.updateWidgets")
    }

    @Test
    fun invokeKeepsRefreshSuccessfulWhenCalendarWidgetUpdateFails() = runTest {
        val notifier = WorkspaceSyncNotifier()
        val friend = testFriend()
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(events = mutableListOf()),
            friendRepository = FakeFriendRepository(friends = listOf(friend)),
            assignmentRepository = FakeAssignmentRepository(),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(
                result = Result.failure(IllegalStateException("widget failed"))
            ),
            syncNotifier = notifier
        )

        val result = useCase()

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().isFullySynced).isTrue()
        assertThat(notifier.snapshots.value).isEqualTo(result.getOrThrow())
    }

    @Test
    fun invokeKeepsRefreshSuccessfulWhenCalendarWidgetUpdateThrows() = runTest {
        val notifier = WorkspaceSyncNotifier()
        val friend = testFriend()
        val useCase = RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(events = mutableListOf()),
            friendRepository = FakeFriendRepository(friends = listOf(friend)),
            assignmentRepository = FakeAssignmentRepository(),
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(
                throwable = IllegalStateException("widget crashed")
            ),
            syncNotifier = notifier
        )

        val result = useCase()

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().friends).containsExactly(friend)
        assertThat(notifier.snapshots.value).isEqualTo(result.getOrThrow())
    }

    @Test
    fun policySkipsFreshFullySyncedSnapshotBeforeStaleThreshold() {
        val snapshot = testWorkspaceSnapshot(isFullySynced = true)
        val lastRefresh = WorkspaceRefreshState(
            snapshot = snapshot,
            completedAtEpochMillis = 1_000
        )

        val decision = WorkspaceRefreshPolicy().decide(
            forceRefresh = false,
            lastRefresh = lastRefresh,
            nowEpochMillis = 1_000 + WorkspaceRefreshPolicy.STALE_THRESHOLD_MILLIS - 1
        )

        assertThat(decision).isEqualTo(WorkspaceRefreshDecision.Skip(snapshot))
    }

    @Test
    fun policyRefreshesAtStaleThreshold() {
        val lastRefresh = WorkspaceRefreshState(
            snapshot = testWorkspaceSnapshot(isFullySynced = true),
            completedAtEpochMillis = 1_000
        )

        val decision = WorkspaceRefreshPolicy().decide(
            forceRefresh = false,
            lastRefresh = lastRefresh,
            nowEpochMillis = 1_000 + WorkspaceRefreshPolicy.STALE_THRESHOLD_MILLIS
        )

        assertThat(decision).isEqualTo(WorkspaceRefreshDecision.Refresh)
    }

    @Test
    fun policyRefreshesWhenForcedOrLastSnapshotWasPartial() {
        val fullRefresh = WorkspaceRefreshState(
            snapshot = testWorkspaceSnapshot(isFullySynced = true),
            completedAtEpochMillis = 1_000
        )
        val partialRefresh = WorkspaceRefreshState(
            snapshot = testWorkspaceSnapshot(isFullySynced = false),
            completedAtEpochMillis = 1_000
        )
        val policy = WorkspaceRefreshPolicy()

        assertThat(
            policy.decide(
                forceRefresh = true,
                lastRefresh = fullRefresh,
                nowEpochMillis = 1_001
            )
        ).isEqualTo(WorkspaceRefreshDecision.Refresh)
        assertThat(
            policy.decide(
                forceRefresh = false,
                lastRefresh = partialRefresh,
                nowEpochMillis = 1_001
            )
        ).isEqualTo(WorkspaceRefreshDecision.Refresh)
    }

    private class RecordingCalendarWidgetUpdater(
        private val events: MutableList<String> = mutableListOf(),
        private val result: Result<Unit> = Result.success(Unit),
        private val throwable: Throwable? = null
    ) : CalendarWidgetUpdater {
        override suspend fun updateCalendarWidgets(): Result<Unit> {
            events += "calendar.updateWidgets"
            throwable?.let { throw it }
            return result
        }
    }

    private class FakeTodoRepository(
        private val events: MutableList<String>,
        private val syncResult: Result<Unit> = Result.success(Unit),
        private val onSync: suspend () -> Unit = {}
    ) : TodoItemRepository {
        override fun observeTodos(): Flow<List<TodoItem>> = flowOf(emptyList())

        override fun observeTodosByDueDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TodoItem>> =
            flowOf(emptyList())

        override suspend fun getTodo(id: Long): TodoItem? = null

        override suspend fun addTodo(
            title: String,
            dueDate: LocalDate?,
            categoryId: Long?,
            dueTimeMinutes: Int?,
            reminderAtEpochMillis: Long?,
            isReminderEnabled: Boolean,
            reminderRepeatType: ReminderRepeatType,
            reminderRepeatDaysMask: Int,
            reminderLeadMinutes: Int?,
            priority: TodoPriority
        ): Result<Long> = Result.failure(UnsupportedOperationException())

        override suspend fun updateTodo(
            id: Long,
            title: String,
            dueDate: LocalDate?,
            categoryId: Long?,
            dueTimeMinutes: Int?,
            reminderAtEpochMillis: Long?,
            isReminderEnabled: Boolean,
            reminderRepeatType: ReminderRepeatType,
            reminderRepeatDaysMask: Int,
            reminderLeadMinutes: Int?,
            priority: TodoPriority
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun deleteTodo(id: Long): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun toggleTodoDone(id: Long): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun syncTodos(): Result<Unit> {
            events += "todo.sync"
            onSync()
            return syncResult
        }
    }

    private class FakeFriendRepository(
        private val events: MutableList<String> = mutableListOf(),
        friends: List<Friend> = emptyList(),
        incoming: List<FriendRequest> = emptyList(),
        outgoing: List<FriendRequest> = emptyList(),
        private val friendsResult: Result<List<Friend>> = Result.success(friends),
        private val incomingResult: Result<List<FriendRequest>> = Result.success(incoming),
        private val outgoingResult: Result<List<FriendRequest>> = Result.success(outgoing)
    ) : FriendRepository {
        override suspend fun getFriends(): Result<List<Friend>> {
            events += "friend.getFriends"
            return friendsResult
        }

        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> {
            events += "friend.getIncoming"
            return incomingResult
        }

        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> {
            events += "friend.getOutgoing"
            return outgoingResult
        }

        override suspend fun sendRequest(nickname: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun acceptRequest(requestId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun declineRequest(requestId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun removeFriend(friendshipId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }

    private class FakeAssignmentRepository(
        private val events: MutableList<String> = mutableListOf(),
        activeReceived: List<AssignedTodo> = emptyList(),
        historyReceived: List<AssignedTodo> = emptyList(),
        private val pendingReceivedResult: Result<List<AssignedTodo>> = Result.success(emptyList()),
        private val activeReceivedResult: Result<List<AssignedTodo>> = Result.success(activeReceived),
        private val historyReceivedResult: Result<List<AssignedTodo>> = Result.success(historyReceived)
    ) : AssignmentRepository {
        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>,
            assignmentMode: com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
        ): Result<AssignmentBundle> = Result.failure(UnsupportedOperationException())

        override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> = Result.failure(UnsupportedOperationException())

        override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> {
            events += "assignment.getReceived.${status.wireValue}"
            return when (status) {
                AssignmentFeedStatus.ACTIVE -> activeReceivedResult
                AssignmentFeedStatus.HISTORY -> historyReceivedResult
                AssignmentFeedStatus.PENDING -> pendingReceivedResult
            }
        }

        override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException())

        override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            flowOf(
                when (status) {
                    AssignmentFeedStatus.ACTIVE -> activeReceivedResult.getOrDefault(emptyList())
                    AssignmentFeedStatus.HISTORY -> historyReceivedResult.getOrDefault(emptyList())
                    AssignmentFeedStatus.PENDING -> emptyList()
                }
            )

        override fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            flowOf(emptyList())

        override fun observeFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Flow<List<AssignedTodo>> = flowOf(emptyList())

        override suspend fun decideBundleItems(
            bundleId: String,
            decisions: Map<String, AssignmentDecision>
        ): Result<AssignmentBundle> = Result.failure(UnsupportedOperationException())

        override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun upsertAssignedTodoReminder(
            assignedTodoId: String,
            reminderAt: String,
            enabled: Boolean
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }

    private fun testFriend() = Friend(
        friendshipId = "friendship-id",
        userId = "friend-id",
        nickname = "monday",
        status = FriendshipStatus.ACTIVE,
        createdAt = "2026-05-09T00:00:00.000Z",
        removedAt = null
    )

    private fun testWorkspaceSnapshot(isFullySynced: Boolean) = WorkspaceRefreshSnapshot(
        isFullySynced = isFullySynced,
        friends = emptyList(),
        incomingRequests = emptyList(),
        outgoingRequests = emptyList(),
        visibleReceivedAssignedTodos = emptyList()
    )

    private fun testAssignedTodo(
        id: String = "assigned-1",
        status: AssignedTodoStatus = AssignedTodoStatus.ACCEPTED
    ) = AssignedTodo(
        id = id,
        bundleId = "bundle-1",
        title = "Shared todo",
        description = null,
        dueDate = LocalDate.of(2026, 5, 9),
        dueTimeMinutes = 9 * 60,
        priority = TodoPriority.MEDIUM,
        category = null,
        status = status,
        terminalReason = null,
        progressPercent = 0,
        sender = AssignedTodoUser(id = "sender-id", nickname = "neo"),
        receiver = AssignedTodoUser(id = "receiver-id", nickname = "monday"),
        reminder = null,
        completedAt = null
    )
}
