package com.neo.yourtodo.feature.friends.impl.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.repository.AuthRepository
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.usecase.CreateAssignmentBundleUseCase
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendAssignmentSummaryUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendRequestsUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.RemoveFriendUseCase
import com.neo.yourtodo.core.domain.usecase.RefreshWorkspaceUseCase
import com.neo.yourtodo.core.domain.usecase.RespondAssignmentBundleUseCase
import com.neo.yourtodo.core.domain.usecase.RespondFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.SendFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.WorkspaceSyncNotifier
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
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
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.auth.AuthUser
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendRequestStatus
import com.neo.yourtodo.core.model.friends.FriendUser
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import com.neo.yourtodo.feature.friends.impl.R
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class FriendsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialLoadShowsFriendsAndRequests() = runTest {
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
            incoming = listOf(request(id = "incoming-1"))
            outgoing = listOf(request(id = "outgoing-1", requesterId = "me", receiverId = "friend-2"))
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            val loaded = awaitItem()
            assertThat(loaded.isLoading).isFalse()
            assertThat(loaded.friends).hasSize(1)
            assertThat(loaded.incomingRequests).hasSize(1)
            assertThat(loaded.outgoingRequests).hasSize(1)
        }
    }

    @Test
    fun sendRequestClearsInputAndRefreshesOutgoingRequests() = runTest {
        val repository = FakeFriendRepository()
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnNicknameChanged("monday"))
            assertThat(awaitItem().nicknameInput).isEqualTo("monday")

            repository.outgoing = listOf(request(id = "sent-1", requesterId = "me", receiverId = "friend-1"))
            viewModel.onAction(FriendsAction.OnSendRequest)

            val running = awaitItem()
            assertThat(running.runningActionKey).isEqualTo("send")

            val clearedInput = awaitItem()
            assertThat(clearedInput.nicknameInput).isEmpty()

            val refreshed = awaitItem()
            assertThat(repository.lastSentNickname).isEqualTo("monday")
            assertThat(refreshed.nicknameInput).isEmpty()
            assertThat(refreshed.outgoingRequests).hasSize(1)
        }
    }

    @Test
    fun closeAddFriendCollapsesPanelAndClearsInput() = runTest {
        val repository = FakeFriendRepository()
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnToggleAddFriend)
            assertThat(awaitItem().addFriendExpanded).isTrue()

            viewModel.onAction(FriendsAction.OnNicknameChanged("monday"))
            assertThat(awaitItem().nicknameInput).isEqualTo("monday")

            viewModel.onAction(FriendsAction.OnCloseAddFriend)
            val closed = awaitItem()
            assertThat(closed.addFriendExpanded).isFalse()
            assertThat(closed.nicknameInput).isEmpty()
        }
    }

    @Test
    fun profileInitialComesFromAuthSessionNickname() = runTest {
        val authRepository = FakeAuthRepository().apply {
            authSession.value = authSession(nickname = "taeyunlive")
        }
        val viewModel = FakeFriendRepository().createViewModel(authRepository)

        val state = viewModel.uiState.first { it.profileInitial == "taeyunlive" }
        assertThat(state.profileInitial).isEqualTo("taeyunlive")
    }

    @Test
    fun manualRefreshUpdatesFriendListWithoutFeatureSnackbar() = runTest {
        val repository = FakeFriendRepository().apply {
            friends = emptyList()
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.first { !it.isLoading }

        repository.friends = listOf(friend())
        viewModel.onAction(FriendsAction.OnRefresh)
        val refreshed = viewModel.uiState.first { it.friends.isNotEmpty() }

        assertThat(refreshed.friends).hasSize(1)
    }

    @Test
    fun acceptRequestRefreshesFriendsAndRemovesIncomingRequest() = runTest {
        val repository = FakeFriendRepository().apply {
            incoming = listOf(request(id = "request-1"))
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            repository.incoming = emptyList()
            repository.friends = listOf(friend())
            viewModel.onAction(FriendsAction.OnAcceptRequest("request-1"))

            assertThat(awaitItem().runningActionKey).isEqualTo("accept:request-1")
            val refreshed = awaitItem()
            assertThat(repository.acceptedRequestId).isEqualTo("request-1")
            assertThat(refreshed.incomingRequests).isEmpty()
            assertThat(refreshed.friends).hasSize(1)
        }
    }

    @Test
    fun authRequiredFailureShowsAuthError() = runTest {
        val repository = FakeFriendRepository(
            getFriendsResult = Result.failure(AuthRequiredException())
        )
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(1)
            assertThat(awaitItem().error).isEqualTo(FriendsError.AUTH_REQUIRED)
        }
    }

    @Test
    fun acceptFailureKeepsPreviousStateAndShowsNetworkError() = runTest {
        val repository = FakeFriendRepository(
            acceptResult = Result.failure(IllegalStateException())
        ).apply {
            incoming = listOf(request(id = "request-1"))
            friends = emptyList()
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnAcceptRequest("request-1"))
            assertThat(awaitItem().runningActionKey).isEqualTo("accept:request-1")

            val failed = awaitItem()
            assertThat(failed.error).isEqualTo(FriendsError.NETWORK)
            assertThat(failed.incomingRequests).hasSize(1)
            assertThat(failed.friends).isEmpty()
        }
    }

    @Test
    fun acceptAuthFailureKeepsPreviousStateAndShowsAuthError() = runTest {
        val repository = FakeFriendRepository(
            acceptResult = Result.failure(AuthRequiredException())
        ).apply {
            incoming = listOf(request(id = "request-1"))
            friends = emptyList()
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnAcceptRequest("request-1"))
            assertThat(awaitItem().runningActionKey).isEqualTo("accept:request-1")

            val failed = awaitItem()
            assertThat(failed.error).isEqualTo(FriendsError.AUTH_REQUIRED)
            assertThat(failed.incomingRequests).hasSize(1)
            assertThat(failed.friends).isEmpty()
        }
    }

    @Test
    fun sendAuthFailureShowsAuthErrorWithoutClearingInput() = runTest {
        val repository = FakeFriendRepository(
            sendResult = Result.failure(AuthRequiredException())
        )
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onAction(FriendsAction.OnNicknameChanged("monday"))
            assertThat(awaitItem().nicknameInput).isEqualTo("monday")

            viewModel.onAction(FriendsAction.OnSendRequest)
            assertThat(awaitItem().runningActionKey).isEqualTo("send")

            val failed = awaitItem()
            assertThat(failed.error).isEqualTo(FriendsError.AUTH_REQUIRED)
            assertThat(failed.nicknameInput).isEqualTo("monday")
        }
    }

    @Test
    fun refreshAfterSuccessfulMutationMapsAuthFailureToAuthError() = runTest {
        val repository = FakeFriendRepository()
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onAction(FriendsAction.OnNicknameChanged("monday"))
            assertThat(awaitItem().nicknameInput).isEqualTo("monday")

            repository.getFriendsResult = Result.failure(AuthRequiredException())
            viewModel.onAction(FriendsAction.OnSendRequest)
            assertThat(awaitItem().runningActionKey).isEqualTo("send")

            var failedRefresh = awaitItem()
            while (failedRefresh.error != FriendsError.AUTH_REQUIRED) {
                failedRefresh = awaitItem()
            }
            assertThat(failedRefresh.runningActionKey).isNull()
            assertThat(failedRefresh.error).isEqualTo(FriendsError.AUTH_REQUIRED)
        }
    }

    @Test
    fun removeFailureKeepsFriendAndShowsNetworkError() = runTest {
        val repository = FakeFriendRepository(
            removeResult = Result.failure(IllegalStateException())
        ).apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnRemoveFriend("friendship-1"))
            assertThat(awaitItem().runningActionKey).isEqualTo("remove:friendship-1")

            val failed = awaitItem()
            assertThat(failed.error).isEqualTo(FriendsError.NETWORK)
            assertThat(failed.friends).hasSize(1)
        }
    }

    @Test
    fun removeAuthFailureKeepsFriendAndShowsAuthError() = runTest {
        val repository = FakeFriendRepository(
            removeResult = Result.failure(AuthRequiredException())
        ).apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnRemoveFriend("friendship-1"))
            assertThat(awaitItem().runningActionKey).isEqualTo("remove:friendship-1")

            val failed = awaitItem()
            assertThat(failed.error).isEqualTo(FriendsError.AUTH_REQUIRED)
            assertThat(failed.friends).hasSize(1)
        }
    }

    @Test
    fun friendClickLoadsAssignmentSummaryAndLists() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            sentItems = listOf(assignedTodo(id = "sent-1", title = "Sent"))
            receivedItems = listOf(assignedTodo(id = "received-1", title = "Received"))
        }
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnFriendClick(friend()))
            val loading = awaitItem()
            assertThat(loading.friendDetailLoading).isTrue()
            assertThat(loading.selectedFriend?.userId).isEqualTo("friend-1")

            val loaded = awaitItem()
            assertThat(loaded.selectedFriend?.userId).isEqualTo("friend-1")
            assertThat(loaded.friendAssignmentSummary).isNotNull()
            assertThat(loaded.friendSentAssignedTodos).hasSize(1)
            assertThat(loaded.friendReceivedAssignedTodos).hasSize(1)
        }
    }

    @Test
    fun friendClickLoadsPendingAndActiveAssignments() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            sentItems = listOf(assignedTodo(id = "sent-active", title = "Active sent"))
            sentPendingItems = listOf(
                assignedTodo(id = "sent-pending", title = "Pending sent", status = AssignedTodoStatus.PENDING_ACCEPTANCE)
            )
            receivedItems = listOf(assignedTodo(id = "received-active", title = "Active received"))
            receivedPendingItems = listOf(
                assignedTodo(id = "received-pending", title = "Pending received", status = AssignedTodoStatus.PENDING_ACCEPTANCE)
            )
        }
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnFriendClick(friend()))
            assertThat(awaitItem().friendDetailLoading).isTrue()

            val loaded = awaitItem()
            assertThat(loaded.friendSentAssignedTodos.map { it.id })
                .containsExactly("sent-pending", "sent-active")
                .inOrder()
            assertThat(loaded.friendReceivedAssignedTodos.map { it.id })
                .containsExactly("received-pending", "received-active")
                .inOrder()
        }
    }

    @Test
    fun friendClickKeepsOldCompletedAssignmentsInHistoryOnly() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            sentItems = listOf(assignedTodo(id = "sent-active", title = "Active sent"))
            sentHistoryItems = listOf(
                assignedTodo(
                    id = "sent-old-done",
                    title = "Old done",
                    status = AssignedTodoStatus.DONE,
                    completedAt = Instant.EPOCH
                )
            )
            receivedHistoryItems = listOf(
                assignedTodo(
                    id = "received-old-done",
                    title = "Old received done",
                    status = AssignedTodoStatus.DONE,
                    completedAt = Instant.EPOCH
                )
            )
        }
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnFriendClick(friend()))
            assertThat(awaitItem().friendDetailLoading).isTrue()

            val loaded = awaitItem()
            assertThat(loaded.friendSentAssignedTodos.map { it.id }).containsExactly("sent-active")
            assertThat(loaded.assignmentDetail.sentHistoryItems.map { it.id }).containsExactly("sent-old-done")
            assertThat(loaded.assignmentDetail.receivedHistoryItems.map { it.id })
                .containsExactly("received-old-done")

            viewModel.onAction(FriendsAction.OnToggleAssignmentHistory)
            assertThat(awaitItem().assignmentDetail.showHistory).isTrue()
        }
    }

    @Test
    fun acceptSelectedPendingAssignmentsGroupsDecisionsByBundle() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            receivedPendingItems = listOf(
                assignedTodo(id = "pending-1", title = "One", status = AssignedTodoStatus.PENDING_ACCEPTANCE),
                assignedTodo(id = "pending-2", title = "Two", status = AssignedTodoStatus.PENDING_ACCEPTANCE)
            )
        }
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onAction(FriendsAction.OnFriendClick(friend()))
            assertThat(awaitItem().friendDetailLoading).isTrue()
            awaitItem()

            viewModel.onAction(FriendsAction.OnTogglePendingAssignment("pending-1"))
            assertThat(awaitItem().selectedPendingAssignmentIds).containsExactly("pending-1")

            viewModel.onAction(FriendsAction.OnAcceptSelectedAssignments)
            assertThat(awaitItem().runningActionKey).isEqualTo("assignment_decision")

            var refreshed = awaitItem()
            while (refreshed.runningActionKey != null) {
                refreshed = awaitItem()
            }
            assertThat(assignmentRepository.decisionsByBundle).containsEntry(
                "bundle-1",
                mapOf("pending-1" to AssignmentDecision.ACCEPT)
            )
            assertThat(refreshed.selectedPendingAssignmentIds).isEmpty()
        }
    }

    @Test
    fun assignmentDetailOnlyShowsActionablePendingItems() {
        val state = FriendsUiState(
            friendReceivedAssignedTodos = listOf(
                assignedTodo(
                    id = "actionable",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-1"
                ),
                assignedTodo(
                    id = "not-actionable",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = null
                ),
                assignedTodo(id = "active", status = AssignedTodoStatus.ACCEPTED)
            ),
            selectedPendingAssignmentIds = setOf("actionable", "not-actionable")
        )

        assertThat(state.assignmentDetail.pendingReceivedItems.map { it.id })
            .containsExactly("actionable")
        assertThat(state.assignmentDetail.activeReceivedItems.map { it.id })
            .containsExactly("active")
        assertThat(state.assignmentDetail.pendingSelectedCount).isEqualTo(1)
    }

    @Test
    fun toggleAssignmentSectionExpandsAndCollapsesSection() = runTest {
        val repository = FakeFriendRepository()
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnToggleAssignmentSection(FriendAssignmentSection.RECEIVED))
            assertThat(awaitItem().expandedAssignmentSections).containsExactly(FriendAssignmentSection.RECEIVED)

            viewModel.onAction(FriendsAction.OnToggleAssignmentSection(FriendAssignmentSection.RECEIVED))
            assertThat(awaitItem().expandedAssignmentSections).isEmpty()
        }
    }

    @Test
    fun acceptSelectedPendingAssignmentsRefreshesPartialSuccessWhenLaterBundleFails() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            receivedPendingItems = listOf(
                assignedTodo(
                    id = "pending-1",
                    title = "One",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-1"
                ),
                assignedTodo(
                    id = "pending-2",
                    title = "Two",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-2"
                )
            )
            failedBundleIds = setOf("bundle-2")
        }
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onAction(FriendsAction.OnFriendClick(friend()))
            assertThat(awaitItem().friendDetailLoading).isTrue()
            awaitItem()

            viewModel.onAction(FriendsAction.OnToggleAllPendingAssignments)
            assertThat(awaitItem().selectedPendingAssignmentIds)
                .containsExactly("pending-1", "pending-2")

            viewModel.onAction(FriendsAction.OnAcceptSelectedAssignments)
            assertThat(awaitItem().runningActionKey).isEqualTo("assignment_decision")

            var failed = awaitItem()
            while (failed.runningActionKey != null) {
                failed = awaitItem()
            }
            assertThat(assignmentRepository.decisionsByBundle).containsEntry(
                "bundle-1",
                mapOf("pending-1" to AssignmentDecision.ACCEPT)
            )
            assertThat(assignmentRepository.decisionsByBundle).doesNotContainKey("bundle-2")
            assertThat(failed.selectedPendingAssignmentIds).containsExactly("pending-2")
            assertThat(failed.friendReceivedAssignedTodos.map { it.id }).containsExactly("pending-2", "pending-1")
            assertThat(failed.error).isEqualTo(FriendsError.NETWORK)
        }
    }

    @Test
    fun sendAssignmentNowCallsRepositoryAndClearsDraft() = runTest {
        val assignmentRepository = FakeAssignmentRepository()
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnOpenAssignmentEditor(friend()))
            assertThat(awaitItem().assignmentTargetFriend?.userId).isEqualTo("friend-1")

            viewModel.onAction(FriendsAction.OnAssignmentTitleChanged("Buy milk"))
            assertThat(awaitItem().assignmentTitleInput).isEqualTo("Buy milk")
            viewModel.onAction(FriendsAction.OnAssignmentDueDateChanged("2026-05-10"))
            assertThat(awaitItem().assignmentDueDateInput).isEqualTo("2026-05-10")
            viewModel.onAction(FriendsAction.OnAssignmentDueTimeChanged("14:30"))
            assertThat(awaitItem().assignmentDueTimeInput).isEqualTo("14:30")

            viewModel.onAction(FriendsAction.OnSendAssignmentNow)
            assertThat(awaitItem().runningActionKey).isEqualTo("assignment:friend-1")

            val sent = awaitItem()
            assertThat(assignmentRepository.lastReceiverUserId).isEqualTo("friend-1")
            assertThat(assignmentRepository.lastItems).hasSize(1)
            assertThat(assignmentRepository.lastItems.single().dueDate).isEqualTo("2026-05-10")
            assertThat(assignmentRepository.lastItems.single().dueTimeMinutes).isEqualTo(14 * 60 + 30)
            assertThat(sent.assignmentTitleInput).isEmpty()
            assertThat(sent.assignmentDueTimeInput).isEmpty()
            assertThat(sent.assignmentTargetFriend).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendAssignmentWithDueTimeRequiresDueDate() = runTest {
        val assignmentRepository = FakeAssignmentRepository()
        val viewModel = FakeFriendRepository().createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnOpenAssignmentEditor(friend()))
            assertThat(awaitItem().assignmentTargetFriend?.userId).isEqualTo("friend-1")
            viewModel.onAction(FriendsAction.OnAssignmentTitleChanged("Buy milk"))
            assertThat(awaitItem().assignmentTitleInput).isEqualTo("Buy milk")
            viewModel.onAction(FriendsAction.OnAssignmentDueTimeChanged("14:30"))
            assertThat(awaitItem().assignmentDueTimeInput).isEqualTo("14:30")

            viewModel.onAction(FriendsAction.OnSendAssignmentNow)
            val error = awaitItem()
            assertThat(error.assignmentInputErrorMessageRes)
                .isEqualTo(R.string.friends_assignment_error_due_time_requires_due_date)
            assertThat(assignmentRepository.lastItems).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearAssignmentDueDateAlsoClearsDueTime() = runTest {
        val viewModel = FakeFriendRepository().createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnOpenAssignmentEditor(friend()))
            assertThat(awaitItem().assignmentTargetFriend?.userId).isEqualTo("friend-1")
            viewModel.onAction(FriendsAction.OnAssignmentDueDateChanged("2026-05-10"))
            assertThat(awaitItem().assignmentDueDateInput).isEqualTo("2026-05-10")
            viewModel.onAction(FriendsAction.OnAssignmentDueTimeChanged("14:30"))
            assertThat(awaitItem().assignmentDueTimeInput).isEqualTo("14:30")

            viewModel.onAction(FriendsAction.OnAssignmentDueDateChanged(""))
            val cleared = awaitItem()
            assertThat(cleared.assignmentDueDateInput).isEmpty()
            assertThat(cleared.assignmentDueTimeInput).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendAssignmentDraftsIncludesSavedDraftAndCurrentInput() = runTest {
        val assignmentRepository = FakeAssignmentRepository()
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnOpenAssignmentEditor(friend()))
            assertThat(awaitItem().assignmentTargetFriend?.userId).isEqualTo("friend-1")

            viewModel.onAction(FriendsAction.OnAssignmentTitleChanged("Buy milk"))
            assertThat(awaitItem().assignmentTitleInput).isEqualTo("Buy milk")
            viewModel.onAction(FriendsAction.OnAssignmentDueDateChanged("2026-05-10"))
            assertThat(awaitItem().assignmentDueDateInput).isEqualTo("2026-05-10")
            viewModel.onAction(FriendsAction.OnAssignmentDueTimeChanged("09:00"))
            assertThat(awaitItem().assignmentDueTimeInput).isEqualTo("09:00")
            viewModel.onAction(FriendsAction.OnAddAssignmentDraft)
            val draftAdded = awaitItem()
            assertThat(draftAdded.assignmentDraftItems).hasSize(1)
            assertThat(draftAdded.assignmentDraftItems.single().dueTimeMinutes).isEqualTo(9 * 60)
            assertThat(draftAdded.assignmentTitleInput).isEmpty()

            viewModel.onAction(FriendsAction.OnAssignmentTitleChanged("Submit report"))
            assertThat(awaitItem().assignmentTitleInput).isEqualTo("Submit report")
            viewModel.onAction(FriendsAction.OnAssignmentDueDateChanged("2026-05-11"))
            assertThat(awaitItem().assignmentDueDateInput).isEqualTo("2026-05-11")
            viewModel.onAction(FriendsAction.OnAssignmentDueTimeChanged("18:15"))
            assertThat(awaitItem().assignmentDueTimeInput).isEqualTo("18:15")

            viewModel.onAction(FriendsAction.OnSendAssignmentDrafts)
            assertThat(awaitItem().runningActionKey).isEqualTo("assignment:friend-1")

            val sent = awaitItem()
            assertThat(assignmentRepository.lastReceiverUserId).isEqualTo("friend-1")
            assertThat(assignmentRepository.lastItems.map { it.title })
                .containsExactly("Buy milk", "Submit report")
                .inOrder()
            assertThat(assignmentRepository.lastItems.map { it.dueTimeMinutes })
                .containsExactly(9 * 60, 18 * 60 + 15)
                .inOrder()
            assertThat(sent.assignmentDraftItems).isEmpty()
            assertThat(sent.assignmentTargetFriend).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun FakeFriendRepository.createViewModel(
        authRepository: FakeAuthRepository = FakeAuthRepository(),
        assignmentRepository: FakeAssignmentRepository = FakeAssignmentRepository()
    ): FriendsViewModel {
        val workspaceSyncNotifier = WorkspaceSyncNotifier()
        return FriendsViewModel(
            getFriends = GetFriendsUseCase(this),
            getFriendRequests = GetFriendRequestsUseCase(this),
            sendFriendRequest = SendFriendRequestUseCase(this),
            respondFriendRequest = RespondFriendRequestUseCase(this),
            removeFriend = RemoveFriendUseCase(this),
            createAssignmentBundle = CreateAssignmentBundleUseCase(assignmentRepository),
            getFriendAssignmentSummary = GetFriendAssignmentSummaryUseCase(assignmentRepository),
            getAssignedTodos = GetAssignedTodosUseCase(assignmentRepository),
            respondAssignmentBundle = RespondAssignmentBundleUseCase(assignmentRepository),
            refreshWorkspaceUseCase = RefreshWorkspaceUseCase(
                todoRepository = SuccessfulTodoRepository(),
                friendRepository = this,
                assignmentRepository = assignmentRepository,
                calendarWidgetUpdater = RecordingCalendarWidgetUpdater(),
                syncNotifier = workspaceSyncNotifier
            ),
            workspaceSyncNotifier = workspaceSyncNotifier,
            observeAuthSession = ObserveAuthSessionUseCase(authRepository)
        )
    }

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

    private class RecordingCalendarWidgetUpdater : CalendarWidgetUpdater {
        override suspend fun updateCalendarWidgets(): Result<Unit> = Result.success(Unit)
    }

    private class FakeAuthRepository : AuthRepository {
        override val authSession = MutableStateFlow<AuthSession?>(null)

        override suspend fun signInWithGoogle(idToken: String): Result<AuthSession> =
            error("Not used.")

        override suspend fun completeNicknameOnboarding(nickname: String): Result<AuthSession> =
            error("Not used.")

        override suspend fun signOut() = Unit
    }

    private class FakeFriendRepository(
        var getFriendsResult: Result<List<Friend>>? = null,
        private val sendResult: Result<Unit> = Result.success(Unit),
        private val acceptResult: Result<Unit> = Result.success(Unit),
        private val removeResult: Result<Unit> = Result.success(Unit)
    ) : FriendRepository {
        var friends = emptyList<Friend>()
        var incoming = emptyList<FriendRequest>()
        var outgoing = emptyList<FriendRequest>()
        var lastSentNickname: String? = null
        var acceptedRequestId: String? = null

        override suspend fun getFriends(): Result<List<Friend>> =
            getFriendsResult ?: Result.success(friends)

        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> =
            Result.success(incoming)

        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> =
            Result.success(outgoing)

        override suspend fun sendRequest(nickname: String): Result<Unit> {
            lastSentNickname = nickname
            return sendResult
        }

        override suspend fun acceptRequest(requestId: String): Result<Unit> {
            acceptedRequestId = requestId
            return acceptResult
        }

        override suspend fun declineRequest(requestId: String): Result<Unit> = Result.success(Unit)

        override suspend fun removeFriend(friendshipId: String): Result<Unit> = removeResult
    }

    private class FakeAssignmentRepository : AssignmentRepository {
        var sentItems = emptyList<AssignedTodo>()
        var sentPendingItems = emptyList<AssignedTodo>()
        var sentHistoryItems = emptyList<AssignedTodo>()
        var receivedItems = emptyList<AssignedTodo>()
        var receivedPendingItems = emptyList<AssignedTodo>()
        var receivedHistoryItems = emptyList<AssignedTodo>()
        var lastReceiverUserId: String? = null
        var lastItems: List<AssignmentDraftItem> = emptyList()
        var failedBundleIds = emptySet<String>()
        val decisionsByBundle = mutableMapOf<String, Map<String, AssignmentDecision>>()

        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>
        ): Result<AssignmentBundle> {
            lastReceiverUserId = receiverUserId
            lastItems = items
            return Result.success(assignmentBundle(items))
        }

        override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> =
            Result.success(
                FriendAssignmentSummary(
                    friendUserId = friendUserId,
                    sent = assignmentSummary(totalCount = sentItems.size + sentPendingItems.size),
                    received = assignmentSummary(totalCount = receivedItems.size + receivedPendingItems.size)
                )
            )

        override suspend fun getFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> {
            val items = when (direction) {
                AssignmentDirection.SENT -> when (status) {
                    AssignmentFeedStatus.ACTIVE -> sentItems
                    AssignmentFeedStatus.PENDING -> sentPendingItems
                    AssignmentFeedStatus.HISTORY -> sentHistoryItems
                }
                AssignmentDirection.RECEIVED -> when (status) {
                    AssignmentFeedStatus.ACTIVE -> receivedItems
                    AssignmentFeedStatus.PENDING -> receivedPendingItems
                    AssignmentFeedStatus.HISTORY -> receivedHistoryItems
                }
            }
            return Result.success(items)
        }

        override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.success(if (status == AssignmentFeedStatus.PENDING) receivedPendingItems else receivedItems)

        override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.success(if (status == AssignmentFeedStatus.PENDING) sentPendingItems else sentItems)

        override suspend fun decideBundleItems(
            bundleId: String,
            decisions: Map<String, AssignmentDecision>
        ): Result<AssignmentBundle> {
            if (bundleId in failedBundleIds) {
                return Result.failure(IllegalStateException("Bundle decision failed"))
            }
            decisionsByBundle[bundleId] = decisions
            val acceptedIds = decisions.filterValues { it == AssignmentDecision.ACCEPT }.keys
            val rejectedIds = decisions.filterValues { it == AssignmentDecision.REJECT }.keys
            receivedItems = receivedItems + receivedPendingItems
                .filter { it.id in acceptedIds }
                .map { it.copy(status = AssignedTodoStatus.ACCEPTED) }
            receivedPendingItems = receivedPendingItems.filterNot { it.id in acceptedIds || it.id in rejectedIds }
            return Result.success(assignmentBundle(emptyList()))
        }

        override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.success(assignedTodo(assignedTodoId))

        override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.success(assignedTodo(assignedTodoId))

        override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.success(assignedTodo(assignedTodoId, status = AssignedTodoStatus.REJECTED))

        override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.success(assignedTodo(assignedTodoId, status = AssignedTodoStatus.CANCELED))

        override suspend fun upsertAssignedTodoReminder(
            assignedTodoId: String,
            reminderAt: String,
            enabled: Boolean
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }
}

private fun authSession(nickname: String) = AuthSession(
    accessToken = "access",
    refreshToken = "refresh",
    user = AuthUser(
        id = "me",
        nickname = nickname,
        email = "me@example.com",
        onboardingRequired = false
    )
)

private fun friend() = Friend(
    friendshipId = "friendship-1",
    userId = "friend-1",
    nickname = "monday",
    status = FriendshipStatus.ACTIVE,
    createdAt = "2026-05-09T00:00:00Z",
    removedAt = null
)

private fun request(
    id: String,
    requesterId: String = "friend-1",
    receiverId: String = "me"
) = FriendRequest(
    id = id,
    requester = FriendUser(
        id = requesterId,
        nickname = if (requesterId == "me") "tester" else "monday"
    ),
    receiver = FriendUser(
        id = receiverId,
        nickname = if (receiverId == "me") "tester" else "monday"
    ),
    status = FriendRequestStatus.PENDING,
    createdAt = "2026-05-09T00:00:00Z",
    respondedAt = null
)

private fun assignedTodo(
    id: String = "assigned-1",
    title: String = "Shared todo",
    status: AssignedTodoStatus = AssignedTodoStatus.ACCEPTED,
    bundleId: String? = "bundle-1",
    completedAt: Instant? = null
) = AssignedTodo(
    id = id,
    bundleId = bundleId,
    title = title,
    description = null,
    dueDate = null,
    priority = TodoPriority.MEDIUM,
    category = null,
    status = status,
    terminalReason = null,
    progressPercent = if (status == AssignedTodoStatus.DONE) 100 else 0,
    sender = AssignedTodoUser(id = "friend-1", nickname = "monday"),
    receiver = AssignedTodoUser(id = "me", nickname = "tester"),
    reminder = null,
    checklist = emptyList(),
    completedAt = completedAt
)

private fun assignmentSummary(totalCount: Int = 0) = AssignmentSummary(
    totalCount = totalCount,
    pendingCount = 0,
    acceptedCount = totalCount,
    inProgressCount = 0,
    doneCount = 0,
    rejectedCount = 0,
    canceledCount = 0,
    progressPercent = 0
)

private fun assignmentBundle(items: List<AssignmentDraftItem>) = AssignmentBundle(
    id = "bundle-1",
    sender = AssignedTodoUser(id = "me", nickname = "tester"),
    receiver = AssignedTodoUser(id = "friend-1", nickname = "monday"),
    status = AssignmentBundleStatus.SENT,
    summary = assignmentSummary(totalCount = items.size),
    items = items.mapIndexed { index, item ->
        assignedTodo(id = "assigned-$index", title = item.title, status = AssignedTodoStatus.PENDING_ACCEPTANCE)
    }
)
