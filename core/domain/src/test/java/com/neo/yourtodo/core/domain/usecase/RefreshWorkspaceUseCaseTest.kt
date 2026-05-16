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

    private class RecordingCalendarWidgetUpdater(
        private val events: MutableList<String> = mutableListOf()
    ) : CalendarWidgetUpdater {
        override suspend fun updateCalendarWidgets(): Result<Unit> {
            events += "calendar.updateWidgets"
            return Result.success(Unit)
        }
    }

    private class FakeTodoRepository(
        private val events: MutableList<String>,
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
            return Result.success(Unit)
        }
    }

    private class FakeFriendRepository(
        private val events: MutableList<String> = mutableListOf(),
        private val friends: List<Friend> = emptyList(),
        private val incoming: List<FriendRequest> = emptyList(),
        private val outgoing: List<FriendRequest> = emptyList()
    ) : FriendRepository {
        override suspend fun getFriends(): Result<List<Friend>> {
            events += "friend.getFriends"
            return Result.success(friends)
        }

        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> {
            events += "friend.getIncoming"
            return Result.success(incoming)
        }

        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> {
            events += "friend.getOutgoing"
            return Result.success(outgoing)
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
        private val activeReceived: List<AssignedTodo> = emptyList(),
        private val historyReceived: List<AssignedTodo> = emptyList()
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
                AssignmentFeedStatus.ACTIVE -> Result.success(activeReceived)
                AssignmentFeedStatus.HISTORY -> Result.success(historyReceived)
                AssignmentFeedStatus.PENDING -> Result.success(emptyList())
            }
        }

        override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException())

        override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            flowOf(
                when (status) {
                    AssignmentFeedStatus.ACTIVE -> activeReceived
                    AssignmentFeedStatus.HISTORY -> historyReceived
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

    private fun testAssignedTodo() = AssignedTodo(
        id = "assigned-1",
        bundleId = "bundle-1",
        title = "Shared todo",
        description = null,
        dueDate = LocalDate.of(2026, 5, 9),
        dueTimeMinutes = 9 * 60,
        priority = TodoPriority.MEDIUM,
        category = null,
        status = AssignedTodoStatus.ACCEPTED,
        terminalReason = null,
        progressPercent = 0,
        sender = AssignedTodoUser(id = "sender-id", nickname = "neo"),
        receiver = AssignedTodoUser(id = "receiver-id", nickname = "monday"),
        reminder = null,
        completedAt = null
    )
}
