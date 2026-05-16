package com.neo.yourtodo.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedCacheFreshness
import com.neo.yourtodo.core.domain.repository.AssignmentFeedCacheKey
import com.neo.yourtodo.core.domain.repository.AssignmentFeedCachePolicy
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoTerminalReason
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoUser
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundleStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.AssignmentSummary
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentState
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentSummary
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AssignmentUseCasesTest {

    @Test
    fun createAssignmentBundleDelegatesReceiverAndDraftItems() = runTest {
        val bundle = testBundle()
        val repository = FakeAssignmentRepository(createResult = Result.success(bundle))
        val useCase = CreateAssignmentBundleUseCase(repository)
        val draft = testDraft()

        val result = useCase(receiverUserId = "friend-1", items = listOf(draft))

        assertThat(result.getOrNull()).isEqualTo(bundle)
        assertThat(repository.createdReceiverUserIds).containsExactly("friend-1")
        assertThat(repository.createdItems).containsExactly(listOf(draft))
        assertThat(repository.createdModes).containsExactly(AssignmentMode.REQUEST)
    }

    @Test
    fun createAssignmentBundleDelegatesDirectMode() = runTest {
        val repository = FakeAssignmentRepository(createResult = Result.success(testBundle()))
        val useCase = CreateAssignmentBundleUseCase(repository)

        useCase(
            receiverUserId = "friend-1",
            items = listOf(testDraft()),
            assignmentMode = AssignmentMode.DIRECT
        )

        assertThat(repository.createdModes).containsExactly(AssignmentMode.DIRECT)
    }

    @Test
    fun setDirectAssignmentOptInDelegatesEnabledState() = runTest {
        val summary = DirectAssignmentConsentSummary(
            grantedByMe = DirectAssignmentConsentState.ACTIVE,
            grantedToMe = DirectAssignmentConsentState.NONE
        )
        val repository = FakeAssignmentRepository(consentResult = Result.success(summary))
        val useCase = SetDirectAssignmentOptInUseCase(repository)

        assertThat(useCase("friend-1", true).getOrThrow()).isEqualTo(summary)
        assertThat(useCase("friend-2", false).getOrThrow()).isEqualTo(summary)

        assertThat(repository.directAssignmentOptInRequests)
            .containsExactly("friend-1" to true, "friend-2" to false)
            .inOrder()
    }

    @Test
    fun getFriendAssignmentSummaryDelegatesFriendUserId() = runTest {
        val summary = FriendAssignmentSummary(
            friendUserId = "friend-1",
            sent = testSummary(totalCount = 2),
            received = testSummary(totalCount = 1)
        )
        val repository = FakeAssignmentRepository(summaryResult = Result.success(summary))
        val useCase = GetFriendAssignmentSummaryUseCase(repository)

        val result = useCase("friend-1")

        assertThat(result.getOrNull()).isEqualTo(summary)
        assertThat(repository.summaryFriendUserIds).containsExactly("friend-1")
    }

    @Test
    fun getAssignedTodosDelegatesReceivedSentAndFriendFeeds() = runTest {
        val todos = listOf(testTodo())
        val repository = FakeAssignmentRepository(
            receivedResult = Result.success(todos),
            sentResult = Result.success(todos),
            friendTodosResult = Result.success(todos)
        )
        val useCase = GetAssignedTodosUseCase(repository)

        val received = useCase.received(AssignmentFeedStatus.PENDING)
        val sent = useCase.sent(AssignmentFeedStatus.HISTORY)
        val friend = useCase.byFriend(
            friendUserId = "friend-1",
            direction = AssignmentDirection.SENT,
            status = AssignmentFeedStatus.ACTIVE
        )

        assertThat(received.getOrNull()).isEqualTo(todos)
        assertThat(sent.getOrNull()).isEqualTo(todos)
        assertThat(friend.getOrNull()).isEqualTo(todos)
        assertThat(repository.receivedStatuses).containsExactly(AssignmentFeedStatus.PENDING)
        assertThat(repository.sentStatuses).containsExactly(AssignmentFeedStatus.HISTORY)
        assertThat(repository.friendFeedRequests)
            .containsExactly(FriendFeedRequest("friend-1", AssignmentDirection.SENT, AssignmentFeedStatus.ACTIVE))
    }

    @Test
    fun assignmentFeedCacheFreshnessDefinesStaleAndForceRefreshBoundary() {
        val feed = AssignmentFeedCacheKey(
            direction = AssignmentDirection.RECEIVED,
            status = AssignmentFeedStatus.ACTIVE
        )
        val refreshedAt = 1_000L
        val freshness = AssignmentFeedCacheFreshness(
            feed = feed,
            lastUpdatedAtEpochMillis = refreshedAt
        )
        val unknown = AssignmentFeedCacheFreshness(
            feed = feed,
            lastUpdatedAtEpochMillis = null
        )

        assertThat(freshness.isStale(refreshedAt + AssignmentFeedCachePolicy.STALE_AFTER_MILLIS - 1))
            .isFalse()
        assertThat(freshness.isStale(refreshedAt + AssignmentFeedCachePolicy.STALE_AFTER_MILLIS))
            .isTrue()
        assertThat(freshness.shouldForceRefresh(refreshedAt + AssignmentFeedCachePolicy.STALE_AFTER_MILLIS))
            .isTrue()
        assertThat(unknown.isStale(refreshedAt)).isTrue()
    }

    @Test
    fun respondAssignmentBundleDelegatesDecisions() = runTest {
        val bundle = testBundle()
        val decisions = mapOf(
            "assigned-1" to AssignmentDecision.ACCEPT,
            "assigned-2" to AssignmentDecision.REJECT
        )
        val repository = FakeAssignmentRepository(decideResult = Result.success(bundle))
        val useCase = RespondAssignmentBundleUseCase(repository)

        val result = useCase(bundleId = "bundle-1", decisions = decisions)

        assertThat(result.getOrNull()).isEqualTo(bundle)
        assertThat(repository.decidedBundleIds).containsExactly("bundle-1")
        assertThat(repository.decidedItems).containsExactly(decisions)
    }

    @Test
    fun manageAssignedTodoDelegatesCompleteDeleteAndCancel() = runTest {
        val todo = testTodo()
        val repository = FakeAssignmentRepository(
            completeResult = Result.success(todo),
            reopenResult = Result.success(todo),
            deleteResult = Result.success(todo),
            cancelResult = Result.success(todo)
        )
        val useCase = ManageAssignedTodoUseCase(
            repository,
            successfulRefreshWorkspaceUseCase(repository)
        )

        val complete = useCase.complete("assigned-complete")
        val reopen = useCase.reopen("assigned-reopen")
        val delete = useCase.deleteReceived("assigned-delete")
        val hide = useCase.hideReceivedFromTaskSurface("assigned-hide")
        val cancel = useCase.cancel("assigned-cancel")

        assertThat(complete.getOrNull()).isEqualTo(todo)
        assertThat(reopen.getOrNull()).isEqualTo(todo)
        assertThat(delete.getOrNull()).isEqualTo(todo)
        assertThat(hide.isSuccess).isTrue()
        assertThat(cancel.getOrNull()).isEqualTo(todo)
        assertThat(repository.completedTodoIds).containsExactly("assigned-complete")
        assertThat(repository.reopenedTodoIds).containsExactly("assigned-reopen")
        assertThat(repository.deletedTodoIds).containsExactly("assigned-delete")
        assertThat(repository.hiddenFromTaskSurfaceTodoIds).containsExactly("assigned-hide")
        assertThat(repository.canceledTodoIds).containsExactly("assigned-cancel")
    }

    @Test
    fun manageAssignedTodoDelegatesReminderMutations() = runTest {
        val repository = FakeAssignmentRepository(
            upsertReminderResult = Result.success(Unit),
            deleteReminderResult = Result.success(Unit)
        )
        val useCase = ManageAssignedTodoUseCase(
            repository,
            successfulRefreshWorkspaceUseCase(repository)
        )

        val upsert = useCase.upsertReminder(
            assignedTodoId = "assigned-reminder",
            reminderAt = "2026-05-10T09:00:00Z",
            enabled = true
        )
        val delete = useCase.deleteReminder("assigned-reminder")

        assertThat(upsert.isSuccess).isTrue()
        assertThat(delete.isSuccess).isTrue()
        assertThat(repository.upsertedReminders)
            .containsExactly(Triple("assigned-reminder", "2026-05-10T09:00:00Z", true))
        assertThat(repository.deletedReminderTodoIds).containsExactly("assigned-reminder")
    }

    @Test
    fun visibleReceivedFetchesActiveAndHistoryFeeds() = runTest {
        val accepted = testTodo(status = AssignedTodoStatus.ACCEPTED).copy(id = "accepted")
        val done = testTodo(status = AssignedTodoStatus.DONE).copy(id = "done")
        val rejected = testTodo(status = AssignedTodoStatus.REJECTED).copy(id = "rejected")
        val repository = FakeAssignmentRepository(
            receivedResults = mapOf(
                AssignmentFeedStatus.ACTIVE to Result.success(listOf(accepted)),
                AssignmentFeedStatus.HISTORY to Result.success(listOf(done, rejected))
            )
        )
        val useCase = GetAssignedTodosUseCase(repository)

        val result = useCase.visibleReceived()

        assertThat(result.getOrThrow()).containsExactly(accepted, done).inOrder()
        assertThat(repository.receivedStatuses)
            .containsExactly(AssignmentFeedStatus.ACTIVE, AssignmentFeedStatus.HISTORY)
            .inOrder()
    }

    @Test
    fun visibleReceivedReturnsFirstFeedFailure() = runTest {
        val failure = IllegalStateException("active failed")
        val repository = FakeAssignmentRepository(
            receivedResults = mapOf(
                AssignmentFeedStatus.ACTIVE to Result.failure(failure),
                AssignmentFeedStatus.HISTORY to Result.success(emptyList())
            )
        )
        val useCase = GetAssignedTodosUseCase(repository)

        val result = useCase.visibleReceived()

        assertThat(result.exceptionOrNull()).isSameInstanceAs(failure)
    }

    @Test
    fun observeVisibleReceivedCombinesActiveAndHistoryFeeds() = runTest {
        val accepted = testTodo(status = AssignedTodoStatus.ACCEPTED).copy(id = "accepted")
        val done = testTodo(status = AssignedTodoStatus.DONE).copy(id = "done")
        val rejected = testTodo(status = AssignedTodoStatus.REJECTED).copy(id = "rejected")
        val repository = FakeAssignmentRepository(
            receivedResults = mapOf(
                AssignmentFeedStatus.ACTIVE to Result.success(listOf(accepted)),
                AssignmentFeedStatus.HISTORY to Result.success(listOf(done, rejected))
            )
        )
        val useCase = GetAssignedTodosUseCase(repository)

        val result = useCase.observeVisibleReceived().first()

        assertThat(result).containsExactly(accepted, done).inOrder()
        assertThat(repository.observedReceivedStatuses)
            .containsExactly(AssignmentFeedStatus.ACTIVE, AssignmentFeedStatus.HISTORY)
            .inOrder()
    }

    @Test
    fun observeAssignedTodosDelegatesReceivedSentAndFriendFeeds() = runTest {
        val todos = listOf(testTodo())
        val repository = FakeAssignmentRepository(
            receivedResult = Result.success(todos),
            sentResult = Result.success(todos),
            friendTodosResult = Result.success(todos)
        )
        val useCase = GetAssignedTodosUseCase(repository)

        val received = useCase.observeReceived(AssignmentFeedStatus.PENDING).first()
        val sent = useCase.observeSent(AssignmentFeedStatus.HISTORY).first()
        val friend = useCase.observeByFriend(
            friendUserId = "friend-1",
            direction = AssignmentDirection.SENT,
            status = AssignmentFeedStatus.ACTIVE
        ).first()

        assertThat(received).isEqualTo(todos)
        assertThat(sent).isEqualTo(todos)
        assertThat(friend).isEqualTo(todos)
        assertThat(repository.observedReceivedStatuses).containsExactly(AssignmentFeedStatus.PENDING)
        assertThat(repository.observedSentStatuses).containsExactly(AssignmentFeedStatus.HISTORY)
        assertThat(repository.observedFriendFeedRequests)
            .containsExactly(FriendFeedRequest("friend-1", AssignmentDirection.SENT, AssignmentFeedStatus.ACTIVE))
    }

    @Test
    fun visibleByFriendFetchesPendingActiveAndHistoryFeedsButShowsOnlyCurrentItems() = runTest {
        val pending = testTodo(status = AssignedTodoStatus.PENDING_ACCEPTANCE).copy(id = "pending")
        val active = testTodo(status = AssignedTodoStatus.IN_PROGRESS).copy(id = "active")
        val done = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.now()
        ).copy(id = "done")
        val repository = FakeAssignmentRepository(
            friendTodosResults = mapOf(
                AssignmentFeedStatus.PENDING to Result.success(listOf(pending)),
                AssignmentFeedStatus.ACTIVE to Result.success(listOf(active)),
                AssignmentFeedStatus.HISTORY to Result.success(listOf(done))
            )
        )
        val useCase = GetAssignedTodosUseCase(repository)

        val result = useCase.visibleByFriend("friend-1", AssignmentDirection.RECEIVED)

        assertThat(result.getOrThrow()).containsExactly(pending, active).inOrder()
        assertThat(repository.friendFeedRequests).containsExactly(
            FriendFeedRequest("friend-1", AssignmentDirection.RECEIVED, AssignmentFeedStatus.PENDING),
            FriendFeedRequest("friend-1", AssignmentDirection.RECEIVED, AssignmentFeedStatus.ACTIVE),
            FriendFeedRequest("friend-1", AssignmentDirection.RECEIVED, AssignmentFeedStatus.HISTORY)
        ).inOrder()
    }

    @Test
    fun visibleByFriendReturnsFirstFeedFailure() = runTest {
        val failure = IllegalStateException("active failed")
        val repository = FakeAssignmentRepository(
            friendTodosResults = mapOf(
                AssignmentFeedStatus.PENDING to Result.success(emptyList()),
                AssignmentFeedStatus.ACTIVE to Result.failure(failure),
                AssignmentFeedStatus.HISTORY to Result.success(emptyList())
            )
        )
        val useCase = GetAssignedTodosUseCase(repository)

        val result = useCase.visibleByFriend("friend-1", AssignmentDirection.RECEIVED)

        assertThat(result.exceptionOrNull()).isSameInstanceAs(failure)
        assertThat(repository.friendFeedRequests).containsExactly(
            FriendFeedRequest("friend-1", AssignmentDirection.RECEIVED, AssignmentFeedStatus.PENDING),
            FriendFeedRequest("friend-1", AssignmentDirection.RECEIVED, AssignmentFeedStatus.ACTIVE),
            FriendFeedRequest("friend-1", AssignmentDirection.RECEIVED, AssignmentFeedStatus.HISTORY)
        ).inOrder()
    }

    @Test
    fun completedHistoryByFriendReturnsHistoryFailure() = runTest {
        val failure = IllegalStateException("history failed")
        val repository = FakeAssignmentRepository(
            friendTodosResults = mapOf(
                AssignmentFeedStatus.HISTORY to Result.failure(failure)
            )
        )
        val useCase = GetAssignedTodosUseCase(repository)

        val result = useCase.completedHistoryByFriend("friend-1", AssignmentDirection.SENT)

        assertThat(result.exceptionOrNull()).isSameInstanceAs(failure)
    }

    @Test
    fun observeVisibleByFriendCombinesPendingActiveAndHistoryFeedsButShowsOnlyCurrentItems() = runTest {
        val pending = testTodo(status = AssignedTodoStatus.PENDING_ACCEPTANCE).copy(id = "pending")
        val active = testTodo(status = AssignedTodoStatus.ACCEPTED).copy(id = "active")
        val done = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.now()
        ).copy(id = "done")
        val repository = FakeAssignmentRepository(
            friendTodosResults = mapOf(
                AssignmentFeedStatus.PENDING to Result.success(listOf(pending)),
                AssignmentFeedStatus.ACTIVE to Result.success(listOf(active)),
                AssignmentFeedStatus.HISTORY to Result.success(listOf(done))
            )
        )
        val useCase = GetAssignedTodosUseCase(repository)

        val result = useCase.observeVisibleByFriend("friend-1", AssignmentDirection.RECEIVED).first()

        assertThat(result).containsExactly(pending, active).inOrder()
        assertThat(repository.observedFriendFeedRequests).containsExactly(
            FriendFeedRequest("friend-1", AssignmentDirection.RECEIVED, AssignmentFeedStatus.PENDING),
            FriendFeedRequest("friend-1", AssignmentDirection.RECEIVED, AssignmentFeedStatus.ACTIVE),
            FriendFeedRequest("friend-1", AssignmentDirection.RECEIVED, AssignmentFeedStatus.HISTORY)
        ).inOrder()
    }

    @Test
    fun observeCompletedHistoryByFriendKeepsTerminalItemsNewestFirst() = runTest {
        val recentDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-05-03T00:00:00Z")
        ).copy(id = "recent-done")
        val oldDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-04-30T00:00:00Z")
        ).copy(id = "old-done")
        val rejected = testTodo(
            status = AssignedTodoStatus.REJECTED,
            terminalReason = AssignedTodoTerminalReason.REJECTED_BY_RECEIVER,
            createdAt = Instant.parse("2026-05-02T00:00:00Z")
        ).copy(id = "rejected")
        val canceled = testTodo(
            status = AssignedTodoStatus.CANCELED,
            terminalReason = AssignedTodoTerminalReason.CANCELED_BY_SENDER,
            createdAt = Instant.parse("2026-05-01T00:00:00Z")
        ).copy(id = "canceled")
        val repository = FakeAssignmentRepository(
            friendTodosResults = mapOf(
                AssignmentFeedStatus.HISTORY to Result.success(listOf(oldDone, rejected, canceled, recentDone))
            )
        )
        val useCase = GetAssignedTodosUseCase(repository)

        val result = useCase.observeCompletedHistoryByFriend("friend-1", AssignmentDirection.SENT).first()

        assertThat(result.map { it.id })
            .containsExactly("recent-done", "rejected", "canceled", "old-done")
            .inOrder()
        assertThat(repository.observedFriendFeedRequests)
            .containsExactly(FriendFeedRequest("friend-1", AssignmentDirection.SENT, AssignmentFeedStatus.HISTORY))
    }

    @Test
    fun taskSurfaceVisibilityHidesPendingUntilAccepted() {
        val pending = testTodo(status = AssignedTodoStatus.PENDING_ACCEPTANCE).copy(id = "pending")
        val accepted = testTodo(status = AssignedTodoStatus.ACCEPTED).copy(id = "accepted")
        val done = testTodo(status = AssignedTodoStatus.DONE).copy(id = "done")

        val result = visibleTaskSurfaceAssignedTodos(listOf(pending, accepted, done))

        assertThat(result).containsExactly(accepted, done).inOrder()
    }

    @Test
    fun friendDetailVisibilityIncludesOnlyCurrentItems() {
        val pending = testTodo(
            status = AssignedTodoStatus.PENDING_ACCEPTANCE,
            createdAt = Instant.parse("2026-05-02T00:00:00Z")
        ).copy(id = "pending")
        val accepted = testTodo(
            status = AssignedTodoStatus.ACCEPTED,
            createdAt = Instant.parse("2026-05-03T00:00:00Z")
        ).copy(id = "accepted")
        val done = testTodo(
            status = AssignedTodoStatus.DONE,
            createdAt = Instant.parse("2026-05-04T00:00:00Z"),
            completedAt = Instant.now()
        ).copy(id = "done")
        val rejected = testTodo(
            status = AssignedTodoStatus.REJECTED,
            terminalReason = AssignedTodoTerminalReason.REJECTED_BY_RECEIVER,
            createdAt = Instant.parse("2026-05-05T00:00:00Z")
        ).copy(id = "rejected")
        val canceled = testTodo(
            status = AssignedTodoStatus.CANCELED,
            terminalReason = AssignedTodoTerminalReason.CANCELED_BY_SENDER,
            createdAt = Instant.parse("2026-05-06T00:00:00Z")
        ).copy(id = "canceled")

        val result = visibleFriendDetailAssignedTodos(listOf(accepted, pending, done, rejected, canceled))

        assertThat(result).containsExactly(pending, accepted).inOrder()
    }

    @Test
    fun friendDetailVisibilitySortsItemsByNewestRequestWithinStatus() {
        val olderPending = testTodo(
            status = AssignedTodoStatus.PENDING_ACCEPTANCE,
            createdAt = Instant.parse("2026-05-01T00:00:00Z")
        ).copy(id = "older-pending")
        val newerPending = testTodo(
            status = AssignedTodoStatus.PENDING_ACCEPTANCE,
            createdAt = Instant.parse("2026-05-03T00:00:00Z")
        ).copy(id = "newer-pending")
        val active = testTodo(
            status = AssignedTodoStatus.ACCEPTED,
            createdAt = Instant.parse("2026-05-04T00:00:00Z")
        ).copy(id = "active")

        val result = visibleFriendDetailAssignedTodos(listOf(olderPending, active, newerPending))

        assertThat(result.map { it.id }).containsExactly(
            "newer-pending",
            "older-pending",
            "active"
        ).inOrder()
    }

    @Test
    fun friendDetailVisibilityExcludesCompletedItemsFromDefaultMonitoring() {
        val active = testTodo(status = AssignedTodoStatus.ACCEPTED).copy(id = "active")
        val recentDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-05-03T00:00:00Z")
        ).copy(id = "recent-done")
        val oldDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-04-30T00:00:00Z")
        ).copy(id = "old-done")

        val result = visibleFriendDetailAssignedTodos(listOf(active, recentDone, oldDone))

        assertThat(result.map { it.id }).containsExactly("active")
    }

    @Test
    fun friendDetailVisibilityExcludesCompletedItemsAtSevenDayBoundary() {
        val boundaryDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-05-02T00:00:00Z")
        ).copy(id = "boundary-done")

        val result = visibleFriendDetailAssignedTodos(listOf(boundaryDone))

        assertThat(result).isEmpty()
    }

    @Test
    fun completedFriendDetailHistoryContainsTerminalItemsNewestFirst() {
        val recentDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-05-03T00:00:00Z")
        ).copy(id = "recent-done")
        val oldDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-04-30T00:00:00Z")
        ).copy(id = "old-done")
        val rejected = testTodo(
            status = AssignedTodoStatus.REJECTED,
            terminalReason = AssignedTodoTerminalReason.REJECTED_BY_RECEIVER,
            createdAt = Instant.parse("2026-05-02T00:00:00Z")
        ).copy(id = "rejected")
        val canceled = testTodo(
            status = AssignedTodoStatus.CANCELED,
            terminalReason = AssignedTodoTerminalReason.CANCELED_BY_SENDER,
            createdAt = Instant.parse("2026-05-01T00:00:00Z")
        ).copy(id = "canceled")
        val rejectedWithoutTerminalReason = testTodo(
            status = AssignedTodoStatus.REJECTED,
            createdAt = Instant.parse("2026-05-04T00:00:00Z")
        ).copy(id = "rejected-without-terminal-reason")

        val result = completedFriendDetailHistoryAssignedTodos(
            listOf(oldDone, rejected, canceled, recentDone, rejectedWithoutTerminalReason)
        )

        assertThat(result.map { it.id })
            .containsExactly("recent-done", "rejected", "canceled", "old-done")
            .inOrder()
    }

    @Test
    fun completedFriendDetailHistoryRestoresCompletedItemsDeletedByReceiver() {
        val deletedAfterCompletion = testTodo(
            status = AssignedTodoStatus.REJECTED,
            terminalReason = AssignedTodoTerminalReason.DELETED_BY_RECEIVER,
            completedAt = Instant.parse("2026-05-03T00:00:00Z")
        ).copy(id = "deleted-after-completion")
        val rejectedWithoutCompletion = testTodo(
            status = AssignedTodoStatus.REJECTED,
            terminalReason = AssignedTodoTerminalReason.DELETED_BY_RECEIVER,
            completedAt = null
        ).copy(id = "deleted-before-completion")

        val result = completedFriendDetailHistoryAssignedTodos(
            listOf(deletedAfterCompletion, rejectedWithoutCompletion)
        )

        assertThat(result.map { it.id }).containsExactly("deleted-after-completion")
        assertThat(result.single().status).isEqualTo(AssignedTodoStatus.DONE)
        assertThat(result.single().progressPercent).isEqualTo(100)
        assertThat(result.single().terminalReason).isNull()
    }

    private data class FriendFeedRequest(
        val friendUserId: String,
        val direction: AssignmentDirection,
        val status: AssignmentFeedStatus
    )

    private fun successfulRefreshWorkspaceUseCase(
        assignmentRepository: AssignmentRepository
    ): RefreshWorkspaceUseCase =
        RefreshWorkspaceUseCase(
            todoRepository = SuccessfulTodoRepository(),
            friendRepository = SuccessfulFriendRepository(),
            assignmentRepository = assignmentRepository,
            calendarWidgetUpdater = RecordingCalendarWidgetUpdater(),
            refreshPolicy = WorkspaceRefreshPolicy(),
            refreshClock = WorkspaceRefreshClock(),
            syncNotifier = WorkspaceSyncNotifier()
        )

    private class SuccessfulTodoRepository : TodoItemRepository {
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

        override suspend fun syncTodos(): Result<Unit> = Result.success(Unit)
    }

    private class SuccessfulFriendRepository : FriendRepository {
        override suspend fun getFriends(): Result<List<Friend>> = Result.success(emptyList())

        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> = Result.success(emptyList())

        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> = Result.success(emptyList())

        override suspend fun sendRequest(nickname: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun acceptRequest(requestId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun declineRequest(requestId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun removeFriend(friendshipId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }

    private class RecordingCalendarWidgetUpdater : CalendarWidgetUpdater {
        var updateCount = 0

        override suspend fun updateCalendarWidgets(): Result<Unit> {
            updateCount += 1
            return Result.success(Unit)
        }
    }

    private class FakeAssignmentRepository(
        private val createResult: Result<AssignmentBundle> =
            Result.failure(UnsupportedOperationException()),
        private val summaryResult: Result<FriendAssignmentSummary> =
            Result.failure(UnsupportedOperationException()),
        private val friendTodosResult: Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException()),
        private val friendTodosResults: Map<AssignmentFeedStatus, Result<List<AssignedTodo>>> = emptyMap(),
        private val receivedResult: Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException()),
        private val receivedResults: Map<AssignmentFeedStatus, Result<List<AssignedTodo>>> = emptyMap(),
        private val sentResult: Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException()),
        private val decideResult: Result<AssignmentBundle> =
            Result.failure(UnsupportedOperationException()),
        private val completeResult: Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException()),
        private val reopenResult: Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException()),
        private val deleteResult: Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException()),
        private val cancelResult: Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException()),
        private val upsertReminderResult: Result<Unit> =
            Result.failure(UnsupportedOperationException()),
        private val deleteReminderResult: Result<Unit> =
            Result.failure(UnsupportedOperationException()),
        private val consentResult: Result<DirectAssignmentConsentSummary> =
            Result.failure(UnsupportedOperationException())
    ) : AssignmentRepository {
        val createdReceiverUserIds = mutableListOf<String>()
        val createdItems = mutableListOf<List<AssignmentDraftItem>>()
        val createdModes = mutableListOf<AssignmentMode>()
        val directAssignmentOptInRequests = mutableListOf<Pair<String, Boolean>>()
        val summaryFriendUserIds = mutableListOf<String>()
        val friendFeedRequests = mutableListOf<FriendFeedRequest>()
        val receivedStatuses = mutableListOf<AssignmentFeedStatus>()
        val sentStatuses = mutableListOf<AssignmentFeedStatus>()
        val observedReceivedStatuses = mutableListOf<AssignmentFeedStatus>()
        val observedSentStatuses = mutableListOf<AssignmentFeedStatus>()
        val observedFriendFeedRequests = mutableListOf<FriendFeedRequest>()
        val decidedBundleIds = mutableListOf<String>()
        val decidedItems = mutableListOf<Map<String, AssignmentDecision>>()
        val completedTodoIds = mutableListOf<String>()
        val reopenedTodoIds = mutableListOf<String>()
        val deletedTodoIds = mutableListOf<String>()
        val hiddenFromTaskSurfaceTodoIds = mutableListOf<String>()
        val canceledTodoIds = mutableListOf<String>()
        val upsertedReminders = mutableListOf<Triple<String, String, Boolean>>()
        val deletedReminderTodoIds = mutableListOf<String>()

        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>
        ): Result<AssignmentBundle> {
            createdReceiverUserIds += receiverUserId
            createdItems += items
            createdModes += AssignmentMode.REQUEST
            return createResult
        }

        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>,
            assignmentMode: AssignmentMode
        ): Result<AssignmentBundle> {
            createdReceiverUserIds += receiverUserId
            createdItems += items
            createdModes += assignmentMode
            return createResult
        }

        override suspend fun setDirectAssignmentOptIn(
            friendUserId: String,
            enabled: Boolean
        ): Result<DirectAssignmentConsentSummary> {
            directAssignmentOptInRequests += friendUserId to enabled
            return consentResult
        }

        override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> {
            summaryFriendUserIds += friendUserId
            return summaryResult
        }

        override suspend fun getFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> {
            friendFeedRequests += FriendFeedRequest(friendUserId, direction, status)
            return friendTodosResults[status] ?: friendTodosResult
        }

        override suspend fun getReceivedAssignedTodos(
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> {
            receivedStatuses += status
            return receivedResults[status] ?: receivedResult
        }

        override suspend fun getSentAssignedTodos(
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> {
            sentStatuses += status
            return sentResult
        }

        override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            flowOf((receivedResults[status] ?: receivedResult).getOrDefault(emptyList())).also {
                observedReceivedStatuses += status
            }

        override fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            flowOf(sentResult.getOrDefault(emptyList())).also {
                observedSentStatuses += status
            }

        override fun observeFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Flow<List<AssignedTodo>> =
            flowOf((friendTodosResults[status] ?: friendTodosResult).getOrDefault(emptyList())).also {
                observedFriendFeedRequests += FriendFeedRequest(friendUserId, direction, status)
            }

        override suspend fun decideBundleItems(
            bundleId: String,
            decisions: Map<String, AssignmentDecision>
        ): Result<AssignmentBundle> {
            decidedBundleIds += bundleId
            decidedItems += decisions
            return decideResult
        }

        override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> {
            completedTodoIds += assignedTodoId
            return completeResult
        }

        override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> {
            reopenedTodoIds += assignedTodoId
            return reopenResult
        }

        override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> {
            deletedTodoIds += assignedTodoId
            return deleteResult
        }

        override suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit> =
            Result.success(Unit).also {
                hiddenFromTaskSurfaceTodoIds += assignedTodoId
            }

        override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> {
            canceledTodoIds += assignedTodoId
            return cancelResult
        }

        override suspend fun upsertAssignedTodoReminder(
            assignedTodoId: String,
            reminderAt: String,
            enabled: Boolean
        ): Result<Unit> {
            upsertedReminders += Triple(assignedTodoId, reminderAt, enabled)
            return upsertReminderResult
        }

        override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> {
            deletedReminderTodoIds += assignedTodoId
            return deleteReminderResult
        }
    }

    private fun testDraft() = AssignmentDraftItem(
        title = "Shared todo",
        description = null,
        dueDate = "2026-05-10",
        dueTimeMinutes = 9 * 60,
        priority = TodoPriority.MEDIUM,
        category = null
    )

    private fun testBundle() = AssignmentBundle(
        id = "bundle-1",
        sender = AssignedTodoUser(id = "user-1", nickname = "neo"),
        receiver = AssignedTodoUser(id = "friend-1", nickname = "monday"),
        status = AssignmentBundleStatus.SENT,
        summary = testSummary(totalCount = 1),
        items = listOf(testTodo())
    )

    private fun testTodo(
        status: AssignedTodoStatus = AssignedTodoStatus.PENDING_ACCEPTANCE,
        terminalReason: AssignedTodoTerminalReason? = null,
        createdAt: Instant? = null,
        completedAt: Instant? = null
    ) = AssignedTodo(
        id = "assigned-1",
        bundleId = "bundle-1",
        title = "Shared todo",
        description = null,
        dueDate = null,
        dueTimeMinutes = 9 * 60,
        priority = TodoPriority.MEDIUM,
        category = null,
        status = status,
        terminalReason = terminalReason,
        progressPercent = if (status == AssignedTodoStatus.DONE) 100 else 0,
        sender = AssignedTodoUser(id = "user-1", nickname = "neo"),
        receiver = AssignedTodoUser(id = "friend-1", nickname = "monday"),
        reminder = null,
        createdAt = createdAt,
        completedAt = completedAt
    )

    private fun testSummary(totalCount: Int) = AssignmentSummary(
        totalCount = totalCount,
        pendingCount = totalCount,
        acceptedCount = 0,
        inProgressCount = 0,
        doneCount = 0,
        rejectedCount = 0,
        canceledCount = 0,
        progressPercent = 0
    )
}
