package com.neo.yourtodo.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoUser
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundleStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentSummary
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import java.time.Instant
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
            deleteResult = Result.success(todo),
            cancelResult = Result.success(todo)
        )
        val useCase = ManageAssignedTodoUseCase(repository)

        val complete = useCase.complete("assigned-complete")
        val delete = useCase.deleteReceived("assigned-delete")
        val cancel = useCase.cancel("assigned-cancel")

        assertThat(complete.getOrNull()).isEqualTo(todo)
        assertThat(delete.getOrNull()).isEqualTo(todo)
        assertThat(cancel.getOrNull()).isEqualTo(todo)
        assertThat(repository.completedTodoIds).containsExactly("assigned-complete")
        assertThat(repository.deletedTodoIds).containsExactly("assigned-delete")
        assertThat(repository.canceledTodoIds).containsExactly("assigned-cancel")
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
    fun friendDetailVisibilityIncludesPendingDecisionItems() {
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

        val result = visibleFriendDetailAssignedTodos(listOf(accepted, pending, done))

        assertThat(result).containsExactly(pending, accepted, done).inOrder()
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
    fun friendDetailVisibilityKeepsOnlyRecentCompletedItemsInDefaultMonitoring() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val active = testTodo(status = AssignedTodoStatus.ACCEPTED).copy(id = "active")
        val recentDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-05-03T00:00:00Z")
        ).copy(id = "recent-done")
        val oldDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-04-30T00:00:00Z")
        ).copy(id = "old-done")

        val result = visibleFriendDetailAssignedTodos(
            listOf(active, recentDone, oldDone),
            now = now
        )

        assertThat(result.map { it.id }).containsExactly("active", "recent-done").inOrder()
    }

    @Test
    fun friendDetailVisibilityIncludesCompletedItemsAtSevenDayBoundary() {
        val now = Instant.parse("2026-05-09T00:00:00Z")
        val boundaryDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-05-02T00:00:00Z")
        ).copy(id = "boundary-done")

        val result = visibleFriendDetailAssignedTodos(listOf(boundaryDone), now = now)

        assertThat(result.map { it.id }).containsExactly("boundary-done")
    }

    @Test
    fun completedFriendDetailHistoryContainsAllDoneItemsNewestFirst() {
        val recentDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-05-03T00:00:00Z")
        ).copy(id = "recent-done")
        val oldDone = testTodo(
            status = AssignedTodoStatus.DONE,
            completedAt = Instant.parse("2026-04-30T00:00:00Z")
        ).copy(id = "old-done")
        val rejected = testTodo(status = AssignedTodoStatus.REJECTED).copy(id = "rejected")

        val result = completedFriendDetailHistoryAssignedTodos(listOf(oldDone, rejected, recentDone))

        assertThat(result.map { it.id }).containsExactly("recent-done", "old-done").inOrder()
    }

    private data class FriendFeedRequest(
        val friendUserId: String,
        val direction: AssignmentDirection,
        val status: AssignmentFeedStatus
    )

    private class FakeAssignmentRepository(
        private val createResult: Result<AssignmentBundle> =
            Result.failure(UnsupportedOperationException()),
        private val summaryResult: Result<FriendAssignmentSummary> =
            Result.failure(UnsupportedOperationException()),
        private val friendTodosResult: Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException()),
        private val receivedResult: Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException()),
        private val sentResult: Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException()),
        private val decideResult: Result<AssignmentBundle> =
            Result.failure(UnsupportedOperationException()),
        private val completeResult: Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException()),
        private val deleteResult: Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException()),
        private val cancelResult: Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())
    ) : AssignmentRepository {
        val createdReceiverUserIds = mutableListOf<String>()
        val createdItems = mutableListOf<List<AssignmentDraftItem>>()
        val summaryFriendUserIds = mutableListOf<String>()
        val friendFeedRequests = mutableListOf<FriendFeedRequest>()
        val receivedStatuses = mutableListOf<AssignmentFeedStatus>()
        val sentStatuses = mutableListOf<AssignmentFeedStatus>()
        val decidedBundleIds = mutableListOf<String>()
        val decidedItems = mutableListOf<Map<String, AssignmentDecision>>()
        val completedTodoIds = mutableListOf<String>()
        val deletedTodoIds = mutableListOf<String>()
        val canceledTodoIds = mutableListOf<String>()

        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>
        ): Result<AssignmentBundle> {
            createdReceiverUserIds += receiverUserId
            createdItems += items
            return createResult
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
            return friendTodosResult
        }

        override suspend fun getReceivedAssignedTodos(
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> {
            receivedStatuses += status
            return receivedResult
        }

        override suspend fun getSentAssignedTodos(
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> {
            sentStatuses += status
            return sentResult
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

        override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> {
            deletedTodoIds += assignedTodoId
            return deleteResult
        }

        override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> {
            canceledTodoIds += assignedTodoId
            return cancelResult
        }

        override suspend fun upsertAssignedTodoReminder(
            assignedTodoId: String,
            reminderAt: String,
            enabled: Boolean
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
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
        terminalReason = null,
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
