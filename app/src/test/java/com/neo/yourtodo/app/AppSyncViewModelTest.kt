package com.neo.yourtodo.app

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.R
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.usecase.RefreshWorkspaceUseCase
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AppSyncViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun automaticSync_doesNotShowSnackbarWhenPartiallySynced() = runTest {
        val viewModel = AppSyncViewModel(partialRefreshWorkspaceUseCase())

        viewModel.sideEffect.test {
            viewModel.syncWorkspace(notifyUser = false)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
            assertThat(viewModel.uiState.value.isSyncing).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun manualSync_showsPartialFailureSnackbarWhenPartiallySynced() = runTest {
        val viewModel = AppSyncViewModel(partialRefreshWorkspaceUseCase())

        viewModel.sideEffect.test {
            viewModel.syncWorkspace()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(
                AppSyncSideEffect.ShowSnackbar(R.string.app_sync_failed)
            )
            assertThat(viewModel.uiState.value.isSyncing).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun partialRefreshWorkspaceUseCase(): RefreshWorkspaceUseCase =
        RefreshWorkspaceUseCase(
            todoRepository = FakeTodoRepository(),
            friendRepository = SuccessFriendRepository(),
            assignmentRepository = PartialFailureAssignmentRepository(),
            calendarWidgetUpdater = NoOpCalendarWidgetUpdater()
        )

    private class SuccessFriendRepository : FriendRepository {
        override suspend fun getFriends(): Result<List<Friend>> = Result.success(emptyList())
        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> = Result.success(emptyList())
        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> = Result.success(emptyList())
        override suspend fun sendRequest(nickname: String): Result<Unit> = Result.failure(UnsupportedOperationException())
        override suspend fun acceptRequest(requestId: String): Result<Unit> = Result.failure(UnsupportedOperationException())
        override suspend fun declineRequest(requestId: String): Result<Unit> = Result.failure(UnsupportedOperationException())
        override suspend fun removeFriend(friendshipId: String): Result<Unit> = Result.failure(UnsupportedOperationException())
    }

    private class PartialFailureAssignmentRepository : AssignmentRepository {
        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>
        ): Result<AssignmentBundle> = Result.failure(UnsupportedOperationException())

        override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> = Result.failure(UnsupportedOperationException())

        override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            when (status) {
                AssignmentFeedStatus.PENDING -> Result.failure(IllegalStateException("pending sync failed"))
                AssignmentFeedStatus.ACTIVE,
                AssignmentFeedStatus.HISTORY -> Result.success(emptyList())
            }

        override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException())

        override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            flowOf(emptyList())

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

    private class NoOpCalendarWidgetUpdater : CalendarWidgetUpdater {
        override suspend fun updateCalendarWidgets(): Result<Unit> = Result.success(Unit)
    }
}
