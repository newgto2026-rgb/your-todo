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
import com.neo.yourtodo.core.domain.usecase.GetFriendRequestsUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.RemoveFriendUseCase
import com.neo.yourtodo.core.domain.usecase.RefreshWorkspaceUseCase
import com.neo.yourtodo.core.domain.usecase.RespondAssignmentBundleUseCase
import com.neo.yourtodo.core.domain.usecase.RespondFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.SendFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.SetDirectAssignmentOptInUseCase
import com.neo.yourtodo.core.domain.usecase.WorkspaceSyncNotifier
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
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.auth.AuthUser
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendRequestStatus
import com.neo.yourtodo.core.model.friends.FriendUser
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentState
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentSummary
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import com.neo.yourtodo.feature.friends.impl.R
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun closeFriendDetailWhileAssignmentDetailIsLoadingCancelsRefreshAndDismissesDialog() = runTest {
        val refreshGate = CompletableDeferred<Unit>()
        val assignmentRepository = FakeAssignmentRepository().apply {
            friendAssignedTodosGate = refreshGate
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

            viewModel.onAction(FriendsAction.OnCloseFriendDetail)
            val closed = awaitItem()
            assertThat(closed.selectedFriend).isNull()
            assertThat(closed.friendDetailLoading).isFalse()
            assertThat(closed.friendAssignmentSummary).isNull()
            assertThat(closed.friendSentAssignedTodos).isEmpty()
            assertThat(closed.friendReceivedAssignedTodos).isEmpty()

            refreshGate.complete(Unit)
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun friendClickDerivesAssignmentSummaryFromLoadedFeeds() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            sentItems = (1..7).map { index -> assignedTodo(id = "sent-active-$index") }
            sentHistoryItems = (1..16).map { index ->
                assignedTodo(id = "sent-done-$index", status = AssignedTodoStatus.DONE)
            }
            receivedItems = (1..3).map { index -> assignedTodo(id = "received-active-$index") }
            receivedHistoryItems = listOf(
                assignedTodo(id = "received-done-1", status = AssignedTodoStatus.DONE)
            )
            friendSummaryResult = Result.success(
                FriendAssignmentSummary(
                    friendUserId = "friend-1",
                    sent = assignmentSummary(totalCount = 4),
                    received = assignmentSummary(totalCount = 11)
                )
            )
        }
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.first { !it.isLoading && it.friends.isNotEmpty() }
        viewModel.onAction(FriendsAction.OnFriendClick(friend()))

        val loaded = viewModel.uiState.first {
            it.selectedFriend?.userId == "friend-1" &&
                !it.friendDetailLoading &&
                it.friendAssignmentSummary != null
        }
        val summary = loaded.friendAssignmentSummary!!
        assertThat(assignmentRepository.friendSummaryCalls).isEqualTo(0)
        assertThat(summary.sent.totalCount).isEqualTo(23)
        assertThat(summary.sent.doneCount).isEqualTo(16)
        assertThat(summary.received.totalCount).isEqualTo(4)
        assertThat(summary.received.doneCount).isEqualTo(1)
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
            assertThat(loaded.assignmentDetail.sentItems.single { it.id == "sent-active" }.statusLabelRes)
                .isEqualTo(R.string.friends_assignment_status_accepted)
            assertThat(loaded.assignmentDetail.activeReceivedItems.single { it.id == "received-active" }.statusLabelRes)
                .isEqualTo(R.string.friends_assignment_status_accepted_by_me)
        }
    }

    @Test
    fun friendClickKeepsTerminalAssignmentsInHistoryOnly() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            sentItems = listOf(assignedTodo(id = "sent-active", title = "Active sent"))
            sentHistoryItems = listOf(
                assignedTodo(
                    id = "sent-old-done",
                    title = "Old done",
                    status = AssignedTodoStatus.DONE,
                    completedAt = Instant.EPOCH
                ),
                assignedTodo(
                    id = "sent-rejected",
                    title = "Rejected sent",
                    status = AssignedTodoStatus.REJECTED,
                    terminalReason = AssignedTodoTerminalReason.REJECTED_BY_RECEIVER,
                    createdAt = Instant.parse("2026-05-04T00:00:00Z")
                ),
                assignedTodo(
                    id = "sent-canceled",
                    title = "Canceled sent",
                    status = AssignedTodoStatus.CANCELED,
                    terminalReason = AssignedTodoTerminalReason.CANCELED_BY_SENDER,
                    createdAt = Instant.parse("2026-05-03T00:00:00Z")
                )
            )
            receivedItems = listOf(assignedTodo(id = "received-active", title = "Active received"))
            receivedHistoryItems = listOf(
                assignedTodo(
                    id = "received-old-done",
                    title = "Old received done",
                    status = AssignedTodoStatus.DONE,
                    completedAt = Instant.EPOCH
                ),
                assignedTodo(
                    id = "received-deleted-after-done",
                    title = "Old deleted after done",
                    status = AssignedTodoStatus.REJECTED,
                    terminalReason = AssignedTodoTerminalReason.DELETED_BY_RECEIVER,
                    completedAt = Instant.parse("2026-05-03T00:00:00Z")
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
            assertThat(loaded.friendReceivedAssignedTodos.map { it.id }).containsExactly("received-active")
            assertThat(loaded.assignmentDetail.sentHistoryItems.map { it.id })
                .containsExactly("sent-rejected", "sent-canceled", "sent-old-done")
                .inOrder()
            assertThat(loaded.assignmentDetail.receivedHistoryItems.map { it.id })
                .containsExactly("received-deleted-after-done", "received-old-done")
                .inOrder()
            assertThat(loaded.assignmentDetail.sentHistoryItems.first().statusStyle)
                .isEqualTo(AssignmentTodoStatusStyle.REJECTED)
            assertThat(loaded.assignmentDetail.sentHistoryItems.first().statusLabelRes)
                .isEqualTo(R.string.friends_assignment_status_rejected)
            assertThat(loaded.assignmentDetail.sentHistoryItems[1].statusStyle)
                .isEqualTo(AssignmentTodoStatusStyle.CANCELED)
            assertThat(loaded.assignmentDetail.sentHistoryItems[1].statusLabelRes)
                .isEqualTo(R.string.friends_assignment_status_canceled_by_me)
            assertThat(loaded.assignmentDetail.receivedHistoryItems.first().statusStyle)
                .isEqualTo(AssignmentTodoStatusStyle.DONE)
            assertThat(loaded.assignmentDetail.receivedHistoryItems.first().statusLabelRes)
                .isEqualTo(R.string.friends_assignment_status_done_by_me)

            viewModel.onAction(FriendsAction.OnToggleAssignmentHistory)
            assertThat(awaitItem().assignmentDetail.showHistory).isTrue()
        }
    }

    @Test
    fun incomingAssignmentRouteOpensFriendDetailAndSelectsBundleItems() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            receivedPendingItems = listOf(
                assignedTodo(
                    id = "bundle-target-a",
                    title = "Target A",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-target"
                ),
                assignedTodo(
                    id = "bundle-other",
                    title = "Other",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-other"
                ),
                assignedTodo(
                    id = "bundle-target-b",
                    title = "Target B",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-target"
                )
            )
        }
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(
                FriendsAction.OnOpenIncomingAssignment(
                    friendUserId = "friend-1",
                    bundleId = "bundle-target"
                )
            )
            assertThat(awaitItem().friendDetailLoading).isTrue()

            val loaded = awaitItem()
            assertThat(loaded.selectedFriend?.userId).isEqualTo("friend-1")
            assertThat(loaded.assignmentDetail.pendingReceivedItems.map { it.id })
                .containsExactly("bundle-target-a", "bundle-target-b", "bundle-other")
            assertThat(loaded.selectedPendingAssignmentIds)
                .containsExactly("bundle-target-a", "bundle-target-b")
        }
    }

    @Test
    fun incomingAssignmentRouteSelectsBundleItemsWhenTheyArriveAfterDetailOpened() = runTest {
        val assignmentRepository = FakeAssignmentRepository()
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(
                FriendsAction.OnOpenIncomingAssignment(
                    friendUserId = "friend-1",
                    bundleId = "bundle-target"
                )
            )
            assertThat(awaitItem().friendDetailLoading).isTrue()

            val loadedWithoutBundle = awaitItem()
            assertThat(loadedWithoutBundle.selectedFriend?.userId).isEqualTo("friend-1")
            assertThat(loadedWithoutBundle.selectedPendingAssignmentIds).isEmpty()

            assignmentRepository.receivedPendingItems = listOf(
                assignedTodo(
                    id = "bundle-target-a",
                    title = "Target A",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-target"
                ),
                assignedTodo(
                    id = "bundle-other",
                    title = "Other",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-other"
                ),
                assignedTodo(
                    id = "bundle-target-b",
                    title = "Target B",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-target"
                )
            )

            val loadedWithBundle = awaitItem()
            assertThat(loadedWithBundle.assignmentDetail.pendingReceivedItems.map { it.id })
                .containsExactly("bundle-target-a", "bundle-target-b", "bundle-other")
            assertThat(loadedWithBundle.selectedPendingAssignmentIds)
                .containsExactly("bundle-target-a", "bundle-target-b")
        }
    }

    @Test
    fun incomingAssignmentRouteResolvesFriendFromBundleWhenActorIsMissingOrStale() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            receivedPendingItems = listOf(
                assignedTodo(
                    id = "bundle-target-a",
                    title = "Target A",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-target"
                ),
                assignedTodo(
                    id = "bundle-other",
                    title = "Other",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-other"
                )
            )
        }
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(
                FriendsAction.OnOpenIncomingAssignment(
                    friendUserId = "stale-user-id",
                    bundleId = "bundle-target"
                )
            )
            assertThat(awaitItem().friendDetailLoading).isTrue()

            val loaded = awaitItem()
            assertThat(loaded.selectedFriend?.userId).isEqualTo("friend-1")
            assertThat(loaded.selectedPendingAssignmentIds).containsExactly("bundle-target-a")
        }
    }

    @Test
    fun incomingAssignmentRouteWithoutIdentifiersOpensFirstPendingAssignment() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            receivedPendingItems = listOf(
                assignedTodo(
                    id = "latest-pending",
                    title = "Pending from push",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-target"
                )
            )
        }
        val repository = FakeFriendRepository().apply {
            friends = emptyList()
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(
                FriendsAction.OnOpenIncomingAssignment(
                    friendUserId = null,
                    bundleId = null
                )
            )
            assertThat(awaitItem().friendDetailLoading).isTrue()

            val loaded = awaitItem()
            assertThat(loaded.selectedFriend?.userId).isEqualTo("friend-1")
            assertThat(loaded.selectedFriend?.nickname).isEqualTo("monday")
            assertThat(loaded.selectedPendingAssignmentIds).containsExactly("latest-pending")
        }
    }

    @Test
    fun incomingAssignmentRouteOpensFromBundleSenderWhenFriendsAreNotLoadedYet() = runTest {
        val assignmentRepository = FakeAssignmentRepository().apply {
            receivedPendingItems = listOf(
                assignedTodo(
                    id = "bundle-target-a",
                    title = "Target A",
                    status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                    bundleId = "bundle-target"
                )
            )
        }
        val repository = FakeFriendRepository().apply {
            friends = emptyList()
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(
                FriendsAction.OnOpenIncomingAssignment(
                    friendUserId = "friend-1",
                    bundleId = "bundle-target"
                )
            )
            assertThat(awaitItem().friendDetailLoading).isTrue()

            val loaded = awaitItem()
            assertThat(loaded.selectedFriend?.userId).isEqualTo("friend-1")
            assertThat(loaded.selectedFriend?.nickname).isEqualTo("monday")
            assertThat(loaded.selectedPendingAssignmentIds).containsExactly("bundle-target-a")
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
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun acceptSelectedPendingAssignmentsKeepsFriendDetailSummaryAfterWorkspaceSync() = runTest {
        val fullHistory = (1..17).map { index ->
            assignedTodo(id = "received-done-$index", status = AssignedTodoStatus.DONE)
        } + listOf(
            assignedTodo(
                id = "received-rejected-1",
                status = AssignedTodoStatus.REJECTED,
                terminalReason = AssignedTodoTerminalReason.REJECTED_BY_RECEIVER
            ),
            assignedTodo(
                id = "received-rejected-2",
                status = AssignedTodoStatus.REJECTED,
                terminalReason = AssignedTodoTerminalReason.REJECTED_BY_RECEIVER
            )
        )
        val taskSurfaceHistory = fullHistory.filter { item ->
            item.status == AssignedTodoStatus.DONE
        }.take(5) + fullHistory.filter { item ->
            item.status == AssignedTodoStatus.REJECTED
        }
        val activeBeforeAccept = (1..10).map { index ->
            assignedTodo(id = "received-active-$index", status = AssignedTodoStatus.ACCEPTED)
        }
        val pending = assignedTodo(
            id = "pending-1",
            title = "One",
            status = AssignedTodoStatus.PENDING_ACCEPTANCE,
            bundleId = "bundle-1"
        )
        val activeAfterAccept = activeBeforeAccept + pending.copy(status = AssignedTodoStatus.ACCEPTED)
        val assignmentRepository = FakeAssignmentRepository().apply {
            receivedItems = activeBeforeAccept
            receivedPendingItems = listOf(pending)
            receivedHistoryItems = fullHistory
            friendReceivedItemsResponseAfterDecision = activeAfterAccept
            friendReceivedPendingItemsResponseAfterDecision = emptyList()
            friendReceivedHistoryItemsResponseAfterDecision = fullHistory
            workspaceReceivedItems = activeAfterAccept
            workspaceReceivedPendingItems = emptyList()
            workspaceReceivedHistoryItems = taskSurfaceHistory
        }
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        advanceUntilIdle()
        viewModel.onAction(FriendsAction.OnFriendClick(friend()))
        advanceUntilIdle()
        viewModel.onAction(FriendsAction.OnTogglePendingAssignment("pending-1"))
        assertThat(viewModel.uiState.value.selectedPendingAssignmentIds).containsExactly("pending-1")

        viewModel.onAction(FriendsAction.OnAcceptSelectedAssignments)
        advanceUntilIdle()

        val refreshed = viewModel.uiState.value
        val received = refreshed.friendAssignmentSummary!!.received
        assertThat(received.doneCount).isEqualTo(17)
        assertThat(received.totalCount).isEqualTo(30)
        assertThat(refreshed.friendReceivedAssignedTodos.map { it.id }).contains("pending-1")
        assertThat(refreshed.friendReceivedCompletedHistoryTodos).hasSize(19)
    }

    @Test
    fun acceptSelectedPendingAssignmentsNeverEmitsTaskSurfaceHistorySummary() = runTest {
        val fullHistory = (1..17).map { index ->
            assignedTodo(id = "received-done-$index", status = AssignedTodoStatus.DONE)
        } + listOf(
            assignedTodo(
                id = "received-rejected-1",
                status = AssignedTodoStatus.REJECTED,
                terminalReason = AssignedTodoTerminalReason.REJECTED_BY_RECEIVER
            ),
            assignedTodo(
                id = "received-rejected-2",
                status = AssignedTodoStatus.REJECTED,
                terminalReason = AssignedTodoTerminalReason.REJECTED_BY_RECEIVER
            )
        )
        val taskSurfaceHistory = fullHistory.filter { item ->
            item.status == AssignedTodoStatus.DONE
        }.take(5) + fullHistory.filter { item ->
            item.status == AssignedTodoStatus.REJECTED
        }
        val activeBeforeAccept = (1..10).map { index ->
            assignedTodo(id = "received-active-$index", status = AssignedTodoStatus.ACCEPTED)
        }
        val pending = assignedTodo(
            id = "pending-1",
            title = "One",
            status = AssignedTodoStatus.PENDING_ACCEPTANCE,
            bundleId = "bundle-1"
        )
        val activeAfterAccept = activeBeforeAccept + pending.copy(status = AssignedTodoStatus.ACCEPTED)
        val assignmentRepository = FakeAssignmentRepository().apply {
            receivedItems = activeBeforeAccept
            receivedPendingItems = listOf(pending)
            receivedHistoryItems = fullHistory
            friendReceivedItemsResponseAfterDecision = activeAfterAccept
            friendReceivedPendingItemsResponseAfterDecision = emptyList()
            friendReceivedHistoryItemsResponseAfterDecision = fullHistory
            workspaceReceivedItems = activeAfterAccept
            workspaceReceivedPendingItems = emptyList()
            workspaceReceivedHistoryItems = taskSurfaceHistory
        }
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onAction(FriendsAction.OnFriendClick(friend()))
            assertThat(awaitItem().friendDetailLoading).isTrue()
            var opened = awaitItem()
            while (opened.friendDetailLoading || opened.friendAssignmentSummary == null) {
                opened = awaitItem()
            }
            assertThat(opened.friendAssignmentSummary!!.received.doneCount).isEqualTo(17)
            assertThat(opened.friendAssignmentSummary!!.received.totalCount).isEqualTo(30)

            viewModel.onAction(FriendsAction.OnTogglePendingAssignment("pending-1"))
            assertThat(awaitItem().selectedPendingAssignmentIds).containsExactly("pending-1")

            viewModel.onAction(FriendsAction.OnAcceptSelectedAssignments)
            val emittedSummaries = mutableListOf<AssignmentSummary>()
            var current = awaitItem()
            emittedSummaries += current.friendAssignmentSummary!!.received
            while (current.runningActionKey != null) {
                current = awaitItem()
                current.friendAssignmentSummary?.received?.let(emittedSummaries::add)
            }

            assertThat(emittedSummaries).isNotEmpty()
            emittedSummaries.forEach { summary ->
                assertThat(summary.doneCount).isEqualTo(17)
                assertThat(summary.totalCount).isEqualTo(30)
            }
            cancelAndIgnoreRemainingEvents()
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
            cancelAndIgnoreRemainingEvents()
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
            assertThat(assignmentRepository.lastAssignmentMode).isEqualTo(AssignmentMode.REQUEST)
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
    fun sendDirectAssignmentUsesDirectModeWhenFriendGrantedPermission() = runTest {
        val assignmentRepository = FakeAssignmentRepository()
        val directFriend = friend(
            directAssignment = DirectAssignmentConsentSummary(
                grantedToMe = DirectAssignmentConsentState.ACTIVE
            )
        )
        val repository = FakeFriendRepository().apply {
            friends = listOf(directFriend)
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnOpenAssignmentEditor(directFriend))
            assertThat(awaitItem().assignmentMode).isEqualTo(AssignmentMode.DIRECT)
            viewModel.onAction(FriendsAction.OnAssignmentTitleChanged("Direct task"))
            assertThat(awaitItem().assignmentTitleInput).isEqualTo("Direct task")

            viewModel.onAction(FriendsAction.OnSendAssignmentNow)
            assertThat(awaitItem().runningActionKey).isEqualTo("assignment:friend-1")

            awaitItem()
            assertThat(assignmentRepository.lastAssignmentMode).isEqualTo(AssignmentMode.DIRECT)
            assertThat(assignmentRepository.lastItems.single().title).isEqualTo("Direct task")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sendAssignmentFallsBackToRequestWhenOnlyIAllowFriend() = runTest {
        val assignmentRepository = FakeAssignmentRepository()
        val inversePermissionFriend = friend(
            directAssignment = DirectAssignmentConsentSummary(
                grantedByMe = DirectAssignmentConsentState.ACTIVE,
                grantedToMe = DirectAssignmentConsentState.NONE
            )
        )
        val viewModel = FakeFriendRepository().createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnOpenAssignmentEditor(inversePermissionFriend))
            assertThat(awaitItem().assignmentMode).isEqualTo(AssignmentMode.REQUEST)

            viewModel.onAction(FriendsAction.OnAssignmentTitleChanged("Request task"))
            assertThat(awaitItem().assignmentTitleInput).isEqualTo("Request task")
            viewModel.onAction(FriendsAction.OnSendAssignmentNow)
            assertThat(awaitItem().runningActionKey).isEqualTo("assignment:friend-1")

            awaitItem()
            assertThat(assignmentRepository.lastAssignmentMode).isEqualTo(AssignmentMode.REQUEST)
            assertThat(assignmentRepository.lastItems.single().title).isEqualTo("Request task")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun directPendingAssignedTodoIsNotShownInPendingDecisionItems() {
        val items = listOf(
            assignedTodo(
                id = "request-pending",
                status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                assignmentMode = AssignmentMode.REQUEST
            ),
            assignedTodo(
                id = "direct-pending",
                status = AssignedTodoStatus.PENDING_ACCEPTANCE,
                assignmentMode = AssignmentMode.DIRECT
            )
        )

        assertThat(items.pendingDecisionItems().map { it.id })
            .containsExactly("request-pending")
    }

    @Test
    fun enablingAutoAcceptRefreshesFriendState() = runTest {
        val assignmentRepository = FakeAssignmentRepository()
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            repository.friends = listOf(
                friend(
                    directAssignment = DirectAssignmentConsentSummary(
                        grantedByMe = DirectAssignmentConsentState.ACTIVE
                    )
                )
            )
            viewModel.onAction(FriendsAction.OnSetDirectAssignmentOptIn(friend(), true))
            assertThat(awaitItem().runningActionKey).isEqualTo("direct_assignment_opt_in:friend-1")

            val refreshed = awaitItem()
            assertThat(assignmentRepository.directAssignmentOptInRequests)
                .containsExactly("friend-1" to true)
            assertThat(refreshed.friends.single().directAssignment.grantedByMe)
                .isEqualTo(DirectAssignmentConsentState.ACTIVE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun disablingAutoAcceptRefreshesFriendState() = runTest {
        val assignmentRepository = FakeAssignmentRepository()
        val repository = FakeFriendRepository().apply {
            friends = listOf(
                friend(directAssignment = DirectAssignmentConsentSummary(grantedByMe = DirectAssignmentConsentState.ACTIVE))
            )
        }
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.uiState.test {
            skipItems(2)

            repository.friends = listOf(
                friend(
                    directAssignment = DirectAssignmentConsentSummary(
                        grantedByMe = DirectAssignmentConsentState.REVOKED
                    )
                )
            )
            viewModel.onAction(FriendsAction.OnSetDirectAssignmentOptIn(friend(), false))
            assertThat(awaitItem().runningActionKey).isEqualTo("direct_assignment_opt_in:friend-1")

            val refreshed = awaitItem()
            assertThat(assignmentRepository.directAssignmentOptInRequests)
                .containsExactly("friend-1" to false)
            assertThat(refreshed.friends.single().directAssignment.grantedByMe)
                .isEqualTo(DirectAssignmentConsentState.REVOKED)
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
            setDirectAssignmentOptIn = SetDirectAssignmentOptInUseCase(assignmentRepository),
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
        private val sentItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
        private val sentPendingItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
        private val sentHistoryItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
        private val receivedItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
        private val receivedPendingItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
        private val receivedHistoryItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
        private val friendReceivedItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
        private val friendReceivedPendingItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
        private val friendReceivedHistoryItemsState = MutableStateFlow<List<AssignedTodo>>(emptyList())
        var sentItems: List<AssignedTodo>
            get() = sentItemsState.value
            set(value) {
                sentItemsState.value = value
            }
        var sentPendingItems: List<AssignedTodo>
            get() = sentPendingItemsState.value
            set(value) {
                sentPendingItemsState.value = value
            }
        var sentHistoryItems: List<AssignedTodo>
            get() = sentHistoryItemsState.value
            set(value) {
                sentHistoryItemsState.value = value
            }
        var receivedItems: List<AssignedTodo>
            get() = receivedItemsState.value
            set(value) {
                receivedItemsState.value = value
                friendReceivedItemsState.value = value
            }
        var receivedPendingItems: List<AssignedTodo>
            get() = receivedPendingItemsState.value
            set(value) {
                receivedPendingItemsState.value = value
                friendReceivedPendingItemsState.value = value
            }
        var receivedHistoryItems: List<AssignedTodo>
            get() = receivedHistoryItemsState.value
            set(value) {
                receivedHistoryItemsState.value = value
                friendReceivedHistoryItemsState.value = value
            }
        var friendReceivedItemsResponse: List<AssignedTodo>? = null
        var friendReceivedPendingItemsResponse: List<AssignedTodo>? = null
        var friendReceivedHistoryItemsResponse: List<AssignedTodo>? = null
        var friendReceivedItemsResponseAfterDecision: List<AssignedTodo>? = null
        var friendReceivedPendingItemsResponseAfterDecision: List<AssignedTodo>? = null
        var friendReceivedHistoryItemsResponseAfterDecision: List<AssignedTodo>? = null
        var workspaceReceivedItems: List<AssignedTodo>? = null
        var workspaceReceivedPendingItems: List<AssignedTodo>? = null
        var workspaceReceivedHistoryItems: List<AssignedTodo>? = null
        var lastReceiverUserId: String? = null
        var lastItems: List<AssignmentDraftItem> = emptyList()
        var lastAssignmentMode: AssignmentMode = AssignmentMode.REQUEST
        var consentSummary = DirectAssignmentConsentSummary()
        val directAssignmentOptInRequests = mutableListOf<Pair<String, Boolean>>()
        var failedBundleIds = emptySet<String>()
        var friendSummaryCalls = 0
        var friendSummaryResult: Result<FriendAssignmentSummary>? = null
        var friendAssignedTodosGate: CompletableDeferred<Unit>? = null
        val decisionsByBundle = mutableMapOf<String, Map<String, AssignmentDecision>>()

        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>
        ): Result<AssignmentBundle> {
            lastReceiverUserId = receiverUserId
            lastItems = items
            lastAssignmentMode = AssignmentMode.REQUEST
            return Result.success(assignmentBundle(items))
        }

        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>,
            assignmentMode: AssignmentMode
        ): Result<AssignmentBundle> {
            lastReceiverUserId = receiverUserId
            lastItems = items
            lastAssignmentMode = assignmentMode
            return Result.success(assignmentBundle(items))
        }

        override suspend fun setDirectAssignmentOptIn(
            friendUserId: String,
            enabled: Boolean
        ): Result<DirectAssignmentConsentSummary> {
            directAssignmentOptInRequests += friendUserId to enabled
            return Result.success(consentSummary)
        }

        override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> =
            (friendSummaryResult ?: Result.success(
                FriendAssignmentSummary(
                    friendUserId = friendUserId,
                    sent = assignmentSummary(totalCount = sentItems.size + sentPendingItems.size),
                    received = assignmentSummary(totalCount = receivedItems.size + receivedPendingItems.size)
                )
            )).also { friendSummaryCalls += 1 }

        override suspend fun getFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> {
            friendAssignedTodosGate?.await()
            val items = when (direction) {
                AssignmentDirection.SENT -> when (status) {
                    AssignmentFeedStatus.ACTIVE -> sentItems
                    AssignmentFeedStatus.PENDING -> sentPendingItems
                    AssignmentFeedStatus.HISTORY -> sentHistoryItems
                }
                AssignmentDirection.RECEIVED -> when (status) {
                    AssignmentFeedStatus.ACTIVE -> friendReceivedItemsResponse ?: receivedItems
                    AssignmentFeedStatus.PENDING -> friendReceivedPendingItemsResponse ?: receivedPendingItems
                    AssignmentFeedStatus.HISTORY -> friendReceivedHistoryItemsResponse ?: receivedHistoryItems
                }
            }
            if (direction == AssignmentDirection.RECEIVED) {
                when (status) {
                    AssignmentFeedStatus.ACTIVE -> friendReceivedItemsState.value = items
                    AssignmentFeedStatus.PENDING -> friendReceivedPendingItemsState.value = items
                    AssignmentFeedStatus.HISTORY -> friendReceivedHistoryItemsState.value = items
                }
            }
            return Result.success(items)
        }

        override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> {
            val items = when (status) {
                AssignmentFeedStatus.ACTIVE -> workspaceReceivedItems ?: receivedItems
                AssignmentFeedStatus.PENDING -> workspaceReceivedPendingItems ?: receivedPendingItems
                AssignmentFeedStatus.HISTORY -> workspaceReceivedHistoryItems ?: receivedHistoryItems
            }
            when (status) {
                AssignmentFeedStatus.ACTIVE -> receivedItemsState.value = items
                AssignmentFeedStatus.PENDING -> receivedPendingItemsState.value = items
                AssignmentFeedStatus.HISTORY -> receivedHistoryItemsState.value = items
            }
            return Result.success(items)
        }

        override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.success(if (status == AssignmentFeedStatus.PENDING) sentPendingItems else sentItems)

        override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            when (status) {
                AssignmentFeedStatus.ACTIVE -> receivedItemsState
                AssignmentFeedStatus.PENDING -> receivedPendingItemsState
                AssignmentFeedStatus.HISTORY -> receivedHistoryItemsState
            }

        override fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            when (status) {
                AssignmentFeedStatus.ACTIVE -> sentItemsState
                AssignmentFeedStatus.PENDING -> sentPendingItemsState
                AssignmentFeedStatus.HISTORY -> sentHistoryItemsState
            }

        override fun observeFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Flow<List<AssignedTodo>> = when (direction) {
            AssignmentDirection.SENT -> observeSentAssignedTodos(status)
            AssignmentDirection.RECEIVED -> when (status) {
                AssignmentFeedStatus.ACTIVE -> friendReceivedItemsState
                AssignmentFeedStatus.PENDING -> friendReceivedPendingItemsState
                AssignmentFeedStatus.HISTORY -> friendReceivedHistoryItemsState
            }
        }

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
            friendReceivedItemsResponseAfterDecision?.let { friendReceivedItemsResponse = it }
            friendReceivedPendingItemsResponseAfterDecision?.let { friendReceivedPendingItemsResponse = it }
            friendReceivedHistoryItemsResponseAfterDecision?.let { friendReceivedHistoryItemsResponse = it }
            return Result.success(assignmentBundle(emptyList()))
        }

        override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.success(assignedTodo(assignedTodoId))

        override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.success(assignedTodo(assignedTodoId))

        override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.success(assignedTodo(assignedTodoId, status = AssignedTodoStatus.REJECTED))

        override suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit> =
            Result.success(Unit)

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

private fun friend(
    directAssignment: DirectAssignmentConsentSummary = DirectAssignmentConsentSummary()
) = Friend(
    friendshipId = "friendship-1",
    userId = "friend-1",
    nickname = "monday",
    status = FriendshipStatus.ACTIVE,
    createdAt = "2026-05-09T00:00:00Z",
    removedAt = null,
    directAssignment = directAssignment
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
    assignmentMode: AssignmentMode = AssignmentMode.REQUEST,
    terminalReason: AssignedTodoTerminalReason? = null,
    bundleId: String? = "bundle-1",
    createdAt: Instant? = null,
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
    terminalReason = terminalReason,
    progressPercent = if (status == AssignedTodoStatus.DONE) 100 else 0,
    sender = AssignedTodoUser(id = "friend-1", nickname = "monday"),
    receiver = AssignedTodoUser(id = "me", nickname = "tester"),
    assignmentMode = assignmentMode,
    reminder = null,
    checklist = emptyList(),
    createdAt = createdAt,
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
