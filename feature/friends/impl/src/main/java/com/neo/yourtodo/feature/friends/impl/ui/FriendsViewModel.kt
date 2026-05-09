package com.neo.yourtodo.feature.friends.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.usecase.CreateAssignmentBundleUseCase
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendRequestsUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendAssignmentSummaryUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.RemoveFriendUseCase
import com.neo.yourtodo.core.domain.usecase.RefreshWorkspaceUseCase
import com.neo.yourtodo.core.domain.usecase.RespondAssignmentBundleUseCase
import com.neo.yourtodo.core.domain.usecase.RespondFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.SendFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.WorkspaceSyncNotifier
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.feature.friends.impl.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val getFriends: GetFriendsUseCase,
    private val getFriendRequests: GetFriendRequestsUseCase,
    private val sendFriendRequest: SendFriendRequestUseCase,
    private val respondFriendRequest: RespondFriendRequestUseCase,
    private val removeFriend: RemoveFriendUseCase,
    private val createAssignmentBundle: CreateAssignmentBundleUseCase,
    private val getFriendAssignmentSummary: GetFriendAssignmentSummaryUseCase,
    private val getAssignedTodos: GetAssignedTodosUseCase,
    private val respondAssignmentBundle: RespondAssignmentBundleUseCase,
    private val refreshWorkspaceUseCase: RefreshWorkspaceUseCase,
    private val workspaceSyncNotifier: WorkspaceSyncNotifier = WorkspaceSyncNotifier(),
    observeAuthSession: ObserveAuthSessionUseCase
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = mutableUiState

    private val mutableSideEffect = MutableSharedFlow<FriendsSideEffect>()
    val sideEffect: SharedFlow<FriendsSideEffect> = mutableSideEffect

    init {
        viewModelScope.launch {
            observeAuthSession().collect { session ->
                mutableUiState.update { it.copy(profileInitial = session?.user?.nickname) }
            }
        }
        observeWorkspaceSync()
        refresh(initial = true)
    }

    fun onAction(action: FriendsAction) {
        when (action) {
            FriendsAction.OnRefresh -> refresh(initial = false)
            FriendsAction.OnToggleAddFriend -> mutableUiState.update {
                it.copy(addFriendExpanded = !it.addFriendExpanded, error = null)
            }
            FriendsAction.OnCloseAddFriend -> mutableUiState.update {
                it.copy(addFriendExpanded = false, nicknameInput = "", error = null)
            }
            is FriendsAction.OnNicknameChanged -> mutableUiState.update {
                it.copy(nicknameInput = action.value.take(MaxNicknameLength), error = null)
            }
            FriendsAction.OnSendRequest -> sendRequest()
            is FriendsAction.OnAcceptRequest -> runMutation(
                key = "accept:${action.requestId}",
                successMessage = FriendsMessage.REQUEST_ACCEPTED
            ) { respondFriendRequest.accept(action.requestId) }
            is FriendsAction.OnDeclineRequest -> runMutation(
                key = "decline:${action.requestId}",
                successMessage = FriendsMessage.REQUEST_DECLINED
            ) { respondFriendRequest.decline(action.requestId) }
            is FriendsAction.OnRemoveFriend -> runMutation(
                key = "remove:${action.friendshipId}",
                successMessage = FriendsMessage.FRIEND_REMOVED
            ) { removeFriend(action.friendshipId) }
            is FriendsAction.OnFriendClick -> openFriendDetail(action.friend)
            FriendsAction.OnCloseFriendDetail -> mutableUiState.update {
                it.copy(
                    selectedFriend = null,
                    friendAssignmentSummary = null,
                    friendSentAssignedTodos = emptyList(),
                    friendReceivedAssignedTodos = emptyList(),
                    friendSentCompletedHistoryTodos = emptyList(),
                    friendReceivedCompletedHistoryTodos = emptyList(),
                    showFriendAssignmentHistory = false,
                    expandedAssignmentSections = emptySet(),
                    selectedPendingAssignmentIds = emptySet()
                )
            }
            FriendsAction.OnToggleAssignmentHistory -> mutableUiState.update {
                it.copy(
                    showFriendAssignmentHistory = !it.showFriendAssignmentHistory,
                    expandedAssignmentSections = emptySet()
                )
            }
            is FriendsAction.OnToggleAssignmentSection -> toggleAssignmentSection(action.section)
            is FriendsAction.OnTogglePendingAssignment -> togglePendingAssignment(action.assignedTodoId)
            FriendsAction.OnToggleAllPendingAssignments -> toggleAllPendingAssignments()
            FriendsAction.OnAcceptSelectedAssignments -> decideSelectedAssignments(
                decision = AssignmentDecision.ACCEPT,
                successMessage = FriendsMessage.ASSIGNMENT_ACCEPTED
            )
            FriendsAction.OnRejectSelectedAssignments -> decideSelectedAssignments(
                decision = AssignmentDecision.REJECT,
                successMessage = FriendsMessage.ASSIGNMENT_REJECTED
            )
            is FriendsAction.OnOpenAssignmentEditor -> mutableUiState.update {
                it.copy(
                    assignmentTargetFriend = action.friend,
                    assignmentDraftItems = emptyList(),
                    assignmentTitleInput = "",
                    assignmentDueDateInput = "",
                    assignmentDueTimeInput = "",
                    assignmentPriority = TodoPriority.MEDIUM,
                    assignmentInputErrorMessageRes = null,
                    error = null
                )
            }
            FriendsAction.OnCloseAssignmentEditor -> mutableUiState.update {
                it.copy(
                    assignmentTargetFriend = null,
                    assignmentDraftItems = emptyList(),
                    assignmentTitleInput = "",
                    assignmentDueDateInput = "",
                    assignmentDueTimeInput = "",
                    assignmentInputErrorMessageRes = null
                )
            }
            is FriendsAction.OnAssignmentTitleChanged -> mutableUiState.update {
                it.copy(
                    assignmentTitleInput = action.value.take(MaxAssignmentTitleLength),
                    assignmentInputErrorMessageRes = null,
                    error = null
                )
            }
            is FriendsAction.OnAssignmentDueDateChanged -> mutableUiState.update {
                val dueDate = action.value.take(MaxDueDateLength)
                it.copy(
                    assignmentDueDateInput = dueDate,
                    assignmentDueTimeInput = if (dueDate.isBlank()) "" else it.assignmentDueTimeInput,
                    assignmentInputErrorMessageRes = null,
                    error = null
                )
            }
            is FriendsAction.OnAssignmentDueTimeChanged -> mutableUiState.update {
                it.copy(
                    assignmentDueTimeInput = action.value.take(MaxDueTimeLength),
                    assignmentInputErrorMessageRes = null,
                    error = null
                )
            }
            is FriendsAction.OnAssignmentPriorityChanged -> mutableUiState.update {
                it.copy(assignmentPriority = action.value)
            }
            FriendsAction.OnAddAssignmentDraft -> addAssignmentDraft()
            is FriendsAction.OnRemoveAssignmentDraft -> mutableUiState.update {
                it.copy(assignmentDraftItems = it.assignmentDraftItems.filterIndexed { index, _ ->
                    index != action.index
                })
            }
            FriendsAction.OnSendAssignmentNow -> sendAssignment(includeDrafts = false)
            FriendsAction.OnSendAssignmentDrafts -> sendAssignment(includeDrafts = true)
            FriendsAction.OnErrorShown -> mutableUiState.update { it.copy(error = null) }
        }
    }

    private fun sendRequest() {
        val nickname = uiState.value.nicknameInput.trim()
        if (nickname.isBlank() || uiState.value.runningActionKey != null) return
        runMutation(
            key = "send",
            successMessage = FriendsMessage.REQUEST_SENT,
            onSuccess = {
                mutableUiState.update {
                    it.copy(
                        nicknameInput = "",
                        addFriendExpanded = false
                    )
                }
            }
        ) {
            sendFriendRequest(nickname)
        }
    }

    private fun refresh(initial: Boolean) {
        if (uiState.value.isRefreshing) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLoading = initial,
                    isRefreshing = !initial,
                    error = null
                )
            }

            val friendsResult = getFriends()
            val incomingRequestsResult = getFriendRequests.incoming()
            val outgoingRequestsResult = getFriendRequests.outgoing()
            val requiredFailure = listOf(
                friendsResult,
                incomingRequestsResult,
                outgoingRequestsResult
            ).firstOrNull { it.isFailure }?.exceptionOrNull()

            if (requiredFailure == null) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        friends = friendsResult.getOrDefault(emptyList()),
                        incomingRequests = incomingRequestsResult.getOrDefault(emptyList()),
                        outgoingRequests = outgoingRequestsResult.getOrDefault(emptyList()),
                        error = null
                    )
                }
            } else {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = requiredFailure.toUiError()
                    )
                }
            }
        }
    }

    private fun observeWorkspaceSync() {
        viewModelScope.launch {
            workspaceSyncNotifier.snapshots.collect { snapshot ->
                if (snapshot != null) {
                    mutableUiState.update {
                        it.copy(
                            friends = snapshot.friends,
                            incomingRequests = snapshot.incomingRequests,
                            outgoingRequests = snapshot.outgoingRequests,
                            isRefreshing = false
                        )
                    }
                }
            }
        }
    }

    private fun openFriendDetail(friend: com.neo.yourtodo.core.model.friends.Friend) {
        mutableUiState.update {
            it.copy(
                selectedFriend = friend,
                friendDetailLoading = true,
                friendAssignmentSummary = null,
                friendSentAssignedTodos = emptyList(),
                friendReceivedAssignedTodos = emptyList(),
                friendSentCompletedHistoryTodos = emptyList(),
                friendReceivedCompletedHistoryTodos = emptyList(),
                showFriendAssignmentHistory = false,
                expandedAssignmentSections = emptySet(),
                selectedPendingAssignmentIds = emptySet(),
                error = null
            )
        }
        viewModelScope.launch {
            refreshFriendDetail(friend)
        }
    }

    private suspend fun refreshFriendDetail(friend: com.neo.yourtodo.core.model.friends.Friend) {
        val friendUserId = friend.userId
        val summary = getFriendAssignmentSummary(friendUserId)
        val sent = getActiveAndPendingAssignedTodos(
            friendUserId = friendUserId,
            direction = AssignmentDirection.SENT
        )
        val received = getActiveAndPendingAssignedTodos(
            friendUserId = friendUserId,
            direction = AssignmentDirection.RECEIVED
        )
        val sentHistory = getCompletedHistoryAssignedTodos(
            friendUserId = friendUserId,
            direction = AssignmentDirection.SENT
        )
        val receivedHistory = getCompletedHistoryAssignedTodos(
            friendUserId = friendUserId,
            direction = AssignmentDirection.RECEIVED
        )

        mutableUiState.update {
            if (
                summary.isSuccess &&
                sent.isSuccess &&
                received.isSuccess &&
                sentHistory.isSuccess &&
                receivedHistory.isSuccess
            ) {
                val receivedItems = received.getOrThrow()
                val validPendingIds = receivedItems.pendingDecisionItems().map { item -> item.id }.toSet()
                it.copy(
                    selectedFriend = friend,
                    friendDetailLoading = false,
                    friendAssignmentSummary = summary.getOrThrow(),
                    friendSentAssignedTodos = sent.getOrThrow(),
                    friendReceivedAssignedTodos = receivedItems,
                    friendSentCompletedHistoryTodos = sentHistory.getOrThrow(),
                    friendReceivedCompletedHistoryTodos = receivedHistory.getOrThrow(),
                    selectedPendingAssignmentIds = it.selectedPendingAssignmentIds.intersect(validPendingIds),
                    error = null
                )
            } else {
                val failure = listOf(summary, sent, received, sentHistory, receivedHistory)
                    .firstOrNull { result -> result.isFailure }
                    ?.exceptionOrNull()
                it.copy(
                    friendDetailLoading = false,
                    error = failure.toUiError()
                )
            }
        }
    }

    private suspend fun getActiveAndPendingAssignedTodos(
        friendUserId: String,
        direction: AssignmentDirection
    ): Result<List<AssignedTodo>> =
        getAssignedTodos.visibleByFriend(
            friendUserId = friendUserId,
            direction = direction
        )

    private suspend fun getCompletedHistoryAssignedTodos(
        friendUserId: String,
        direction: AssignmentDirection
    ): Result<List<AssignedTodo>> =
        getAssignedTodos.completedHistoryByFriend(
            friendUserId = friendUserId,
            direction = direction
        )

    private fun toggleAssignmentSection(section: FriendAssignmentSection) {
        mutableUiState.update {
            val sections = if (section in it.expandedAssignmentSections) {
                it.expandedAssignmentSections - section
            } else {
                it.expandedAssignmentSections + section
            }
            it.copy(expandedAssignmentSections = sections)
        }
    }

    private fun togglePendingAssignment(assignedTodoId: String) {
        val pendingIds = uiState.value.decisionPendingAssignedTodos().map { it.id }.toSet()
        if (assignedTodoId !in pendingIds) return
        mutableUiState.update {
            val selectedIds = if (assignedTodoId in it.selectedPendingAssignmentIds) {
                it.selectedPendingAssignmentIds - assignedTodoId
            } else {
                it.selectedPendingAssignmentIds + assignedTodoId
            }
            it.copy(selectedPendingAssignmentIds = selectedIds)
        }
    }

    private fun toggleAllPendingAssignments() {
        val pendingIds = uiState.value.decisionPendingAssignedTodos().map { it.id }.toSet()
        if (pendingIds.isEmpty()) return
        mutableUiState.update {
            val selectedIds = if (it.selectedPendingAssignmentIds.containsAll(pendingIds)) {
                it.selectedPendingAssignmentIds - pendingIds
            } else {
                it.selectedPendingAssignmentIds + pendingIds
            }
            it.copy(selectedPendingAssignmentIds = selectedIds)
        }
    }

    private fun decideSelectedAssignments(
        decision: AssignmentDecision,
        successMessage: FriendsMessage
    ) {
        val state = uiState.value
        if (state.runningActionKey != null) return
        val friend = state.selectedFriend ?: return
        val selectedItems = state.decisionPendingAssignedTodos()
            .filter { item -> item.id in state.selectedPendingAssignmentIds }
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            mutableUiState.update { it.copy(runningActionKey = "assignment_decision", error = null) }
            val succeededItemIds = mutableSetOf<String>()
            val result = runCatching {
                selectedItems
                    .groupBy { checkNotNull(it.bundleId) }
                    .forEach { (bundleId, items) ->
                        respondAssignmentBundle(
                            bundleId = bundleId,
                            decisions = items.associate { item -> item.id to decision }
                        ).getOrThrow()
                        succeededItemIds += items.map { item -> item.id }
                    }
            }

            if (result.isSuccess) {
                mutableUiState.update { it.copy(selectedPendingAssignmentIds = emptySet()) }
                refreshFriendDetail(friend)
                refreshWorkspaceAfterAssignmentDecision()
                mutableSideEffect.emit(FriendsSideEffect.ShowSnackbar(successMessage.messageRes))
            } else {
                if (succeededItemIds.isNotEmpty()) {
                    mutableUiState.update {
                        it.copy(selectedPendingAssignmentIds = it.selectedPendingAssignmentIds - succeededItemIds)
                    }
                    refreshFriendDetail(friend)
                    refreshWorkspaceUseCase()
                }
                mutableUiState.update {
                    it.copy(
                        runningActionKey = null,
                        error = result.exceptionOrNull().toUiError()
                    )
                }
            }
        }
    }

    private fun addAssignmentDraft() {
        val item = currentAssignmentDraftOrNull() ?: return
        mutableUiState.update {
            it.copy(
                assignmentDraftItems = it.assignmentDraftItems + item,
                assignmentTitleInput = "",
                assignmentDueDateInput = "",
                assignmentDueTimeInput = "",
                assignmentInputErrorMessageRes = null,
                error = null
            )
        }
    }

    private fun sendAssignment(includeDrafts: Boolean) {
        val state = uiState.value
        val friend = state.assignmentTargetFriend ?: return
        val currentDraft = currentAssignmentDraftOrNull()
        val items = if (includeDrafts) {
            state.assignmentDraftItems + listOfNotNull(currentDraft)
        } else {
            listOfNotNull(currentDraft)
        }
        if (items.isEmpty() || state.runningActionKey != null) return

        runMutation(
            key = "assignment:${friend.userId}",
            successMessage = FriendsMessage.ASSIGNMENT_SENT,
            onSuccess = {
                mutableUiState.update {
                    it.copy(
                        assignmentTargetFriend = null,
                        assignmentDraftItems = emptyList(),
                        assignmentTitleInput = "",
                        assignmentDueDateInput = "",
                        assignmentDueTimeInput = "",
                        assignmentInputErrorMessageRes = null
                    )
                }
            }
        ) {
            createAssignmentBundle(friend.userId, items).also { result ->
                val selectedFriend = uiState.value.selectedFriend
                if (result.isSuccess && selectedFriend?.userId == friend.userId) {
                    refreshFriendDetail(selectedFriend)
                }
            }.map { Unit }
        }
    }

    private fun currentAssignmentDraftOrNull(): AssignmentDraftItem? {
        val state = uiState.value
        val title = state.assignmentTitleInput.trim()
        if (title.isBlank()) return null
        val dueTimeMinutes = dueTimeTextToMinutes(state.assignmentDueTimeInput)
        if (state.assignmentDueTimeInput.isNotBlank() && state.assignmentDueDateInput.isBlank()) {
            mutableUiState.update {
                it.copy(assignmentInputErrorMessageRes = R.string.friends_assignment_error_due_time_requires_due_date)
            }
            return null
        }
        return AssignmentDraftItem(
            title = title,
            description = null,
            dueDate = state.assignmentDueDateInput.trim().takeIf { it.isNotBlank() },
            dueTimeMinutes = dueTimeMinutes,
            priority = state.assignmentPriority,
            category = null
        )
    }

    private fun runMutation(
        key: String,
        successMessage: FriendsMessage,
        onSuccess: () -> Unit = {},
        block: suspend () -> Result<Unit>
    ) {
        if (uiState.value.runningActionKey != null) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(runningActionKey = key, error = null) }
            val result = block()
            if (result.isSuccess) {
                onSuccess()
                refreshAfterMutation()
                mutableSideEffect.emit(FriendsSideEffect.ShowSnackbar(successMessage.messageRes))
            } else {
                mutableUiState.update {
                    it.copy(
                        runningActionKey = null,
                        error = result.exceptionOrNull().toUiError()
                    )
                }
            }
        }
    }

    private suspend fun refreshAfterMutation() {
        val friends = getFriends()
        val incoming = getFriendRequests.incoming()
        val outgoing = getFriendRequests.outgoing()
        mutableUiState.update {
            if (friends.isSuccess && incoming.isSuccess && outgoing.isSuccess) {
                it.copy(
                    friends = friends.getOrThrow(),
                    incomingRequests = incoming.getOrThrow(),
                    outgoingRequests = outgoing.getOrThrow(),
                    runningActionKey = null,
                    error = null
                )
            } else {
                val failure = listOf(friends, incoming, outgoing)
                    .firstOrNull { result -> result.isFailure }
                    ?.exceptionOrNull()
                it.copy(
                    runningActionKey = null,
                    error = failure.toUiError()
                )
            }
        }
    }

    private suspend fun refreshWorkspaceAfterAssignmentDecision() {
        refreshWorkspaceUseCase()
        refreshAfterMutation()
    }

    private fun Throwable?.toUiError(): FriendsError =
        if (this is AuthRequiredException) FriendsError.AUTH_REQUIRED else FriendsError.NETWORK

    private companion object {
        const val MaxNicknameLength = 12
        const val MaxAssignmentTitleLength = 80
        const val MaxDueDateLength = 10
        const val MaxDueTimeLength = 5
    }
}

internal fun FriendsUiState.decisionPendingAssignedTodos(): List<AssignedTodo> =
    friendReceivedAssignedTodos.pendingDecisionItems()

internal fun List<AssignedTodo>.pendingDecisionItems(): List<AssignedTodo> =
    filter { item ->
        item.status == AssignedTodoStatus.PENDING_ACCEPTANCE && item.bundleId != null
    }

internal fun dueTimeTextToMinutes(value: String): Int? {
    if (value.isBlank()) return null
    val parts = value.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
