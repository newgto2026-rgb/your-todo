package com.neo.yourtodo.feature.friends.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
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
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.feature.friends.impl.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val setDirectAssignmentOptIn: SetDirectAssignmentOptInUseCase,
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
    private var friendDetailObservationJob: Job? = null
    private var friendDetailRefreshJob: Job? = null
    private var pendingIncomingAssignmentTarget: IncomingAssignmentTarget? = null
    private var pendingIncomingAssignmentResolutionJob: Job? = null
    private var pendingIncomingAssignmentBundleSelectionId: String? = null

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
            is FriendsAction.OnOpenIncomingAssignment -> openIncomingAssignment(
                friendUserId = action.friendUserId,
                friendNickname = action.friendNickname,
                bundleId = action.bundleId
            )
            FriendsAction.OnCloseFriendDetail -> closeFriendDetail()
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
            is FriendsAction.OnOpenAssignmentEditor -> openAssignmentEditor(action.friend)
            FriendsAction.OnCloseAssignmentEditor -> closeAssignmentEditor()
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
            is FriendsAction.OnRemoveAssignmentDraft -> removeAssignmentDraft(action.index)
            FriendsAction.OnSendAssignmentNow -> sendAssignment(includeDrafts = false)
            FriendsAction.OnSendAssignmentDrafts -> sendAssignment(includeDrafts = true)
            is FriendsAction.OnSetDirectAssignmentOptIn -> runDirectAssignmentOptInMutation(
                friend = action.friend,
                enabled = action.enabled
            )
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

    private fun closeFriendDetail() {
        friendDetailObservationJob?.cancel()
        friendDetailObservationJob = null
        friendDetailRefreshJob?.cancel()
        friendDetailRefreshJob = null
        pendingIncomingAssignmentBundleSelectionId = null
        mutableUiState.update {
            it.copy(
                selectedFriend = null,
                friendDetailLoading = false,
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
    }

    private fun openAssignmentEditor(friend: Friend) {
        mutableUiState.update {
            it.copy(
                assignmentTargetFriend = friend,
                assignmentDraftItems = emptyList(),
                assignmentTitleInput = "",
                assignmentDueDateInput = "",
                assignmentDueTimeInput = "",
                assignmentPriority = TodoPriority.MEDIUM,
                assignmentMode = friend.assignmentModeForNewBundle(),
                assignmentInputErrorMessageRes = null,
                error = null
            )
        }
    }

    private fun closeAssignmentEditor() {
        mutableUiState.update {
            it.copy(
                assignmentTargetFriend = null,
                assignmentDraftItems = emptyList(),
                assignmentTitleInput = "",
                assignmentDueDateInput = "",
                assignmentDueTimeInput = "",
                assignmentMode = AssignmentMode.REQUEST,
                assignmentInputErrorMessageRes = null
            )
        }
    }

    private fun removeAssignmentDraft(index: Int) {
        mutableUiState.update {
            it.copy(
                assignmentDraftItems = it.assignmentDraftItems.filterIndexed { itemIndex, _ ->
                    itemIndex != index
                }
            )
        }
    }

    private fun refresh(initial: Boolean) {
        if (uiState.value.isRefreshing) return
        viewModelScope.launch {
            mutableUiState.update {
                val blockingLoad = initial || !it.hasLoadedFriendsSnapshot
                it.copy(
                    isLoading = blockingLoad,
                    isRefreshing = !blockingLoad,
                    error = null
                )
            }

            try {
                val friendsResult = getFriends()
                val incomingRequestsResult = getFriendRequests.incoming()
                val outgoingRequestsResult = getFriendRequests.outgoing()
                val requiredFailure = listOf(
                    friendsResult,
                    incomingRequestsResult,
                    outgoingRequestsResult
                ).firstOrNull { it.isFailure }?.exceptionOrNull()
                requiredFailure.throwIfCancellation()

                if (requiredFailure == null) {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            friends = friendsResult.getOrDefault(emptyList()),
                            incomingRequests = incomingRequestsResult.getOrDefault(emptyList()),
                            outgoingRequests = outgoingRequestsResult.getOrDefault(emptyList()),
                            hasLoadedFriendsSnapshot = true,
                            friendsSnapshotError = null,
                            error = null
                        )
                    }
                    openPendingIncomingAssignmentIfReady()
                } else {
                    val uiError = requiredFailure.toUiError()
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            friendsSnapshotError = if (it.hasLoadedFriendsSnapshot) {
                                it.friendsSnapshotError
                            } else {
                                uiError
                            },
                            error = uiError
                        )
                    }
                }
            } finally {
                mutableUiState.update {
                    if (it.isLoading || it.isRefreshing) {
                        it.copy(isLoading = false, isRefreshing = false)
                    } else {
                        it
                    }
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
                            hasLoadedFriendsSnapshot = true,
                            friendsSnapshotError = null,
                            isRefreshing = false
                        )
                    }
                    openPendingIncomingAssignmentIfReady()
                }
            }
        }
    }

    private fun openIncomingAssignment(friendUserId: String?, friendNickname: String?, bundleId: String?) {
        pendingIncomingAssignmentTarget = IncomingAssignmentTarget(
            friendUserId = friendUserId?.takeIf { it.isNotBlank() },
            friendNickname = friendNickname?.takeIf { it.isNotBlank() },
            bundleId = bundleId?.takeIf { it.isNotBlank() }
        )
        openPendingIncomingAssignmentIfReady()
    }

    private fun openPendingIncomingAssignmentIfReady() {
        val target = pendingIncomingAssignmentTarget ?: return
        val friend = target.friendUserId
            ?.let { friendUserId ->
                uiState.value.friends.firstOrNull { it.userId == friendUserId }
                    ?: target.toIncomingAssignmentFriendOrNull()
            }
            ?: run {
                resolvePendingIncomingAssignmentByBundle(target)
                return
        }
        pendingIncomingAssignmentTarget = null
        openFriendDetail(friend, initialBundleId = target.bundleId)
    }

    private fun resolvePendingIncomingAssignmentByBundle(target: IncomingAssignmentTarget) {
        if (pendingIncomingAssignmentResolutionJob?.isActive == true) return
        pendingIncomingAssignmentResolutionJob = viewModelScope.launch {
            val bundleId = target.bundleId
            val pendingItem = getAssignedTodos.observeReceived(AssignmentFeedStatus.PENDING)
                .first()
                .firstPendingIncomingAssignment(bundleId)
                ?: getAssignedTodos.received(AssignmentFeedStatus.PENDING)
                    .getOrDefault(emptyList())
                    .firstPendingIncomingAssignment(bundleId)
            val sender = pendingItem?.sender
            val currentTarget = pendingIncomingAssignmentTarget
            if (sender == null || currentTarget?.matches(target) != true) return@launch
            pendingIncomingAssignmentTarget = null
            openFriendDetail(
                friend = uiState.value.friends.firstOrNull { it.userId == sender.id }
                    ?: sender.toIncomingAssignmentFriend(),
                initialBundleId = pendingItem.bundleId
            )
        }
    }

    private fun openFriendDetail(
        friend: com.neo.yourtodo.core.model.friends.Friend,
        initialBundleId: String? = null
    ) {
        pendingIncomingAssignmentBundleSelectionId = initialBundleId?.takeIf { it.isNotBlank() }
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
        observeFriendAssignmentCache(friend)
        friendDetailRefreshJob?.cancel()
        friendDetailRefreshJob = viewModelScope.launch {
            refreshFriendDetail(friend, initialBundleId = initialBundleId)
        }
    }

    private fun observeFriendAssignmentCache(friend: com.neo.yourtodo.core.model.friends.Friend) {
        friendDetailObservationJob?.cancel()
        friendDetailObservationJob = viewModelScope.launch {
            combine(
                getAssignedTodos.observeVisibleByFriend(
                    friendUserId = friend.userId,
                    direction = AssignmentDirection.SENT
                ),
                getAssignedTodos.observeVisibleByFriend(
                    friendUserId = friend.userId,
                    direction = AssignmentDirection.RECEIVED
                ),
                getAssignedTodos.observeCompletedHistoryByFriend(
                    friendUserId = friend.userId,
                    direction = AssignmentDirection.SENT
                ),
                getAssignedTodos.observeCompletedHistoryByFriend(
                    friendUserId = friend.userId,
                    direction = AssignmentDirection.RECEIVED
                )
            ) { sent, received, sentHistory, receivedHistory ->
                FriendAssignmentCacheSnapshot(
                    sent = sent,
                    received = received,
                    sentHistory = sentHistory,
                    receivedHistory = receivedHistory
                )
            }.collect { snapshot ->
                val bundleSelectedIds = pendingIncomingAssignmentBundleSelectionId
                    ?.let { bundleId -> snapshot.received.pendingBundleItemIds(bundleId) }
                    .orEmpty()
                if (bundleSelectedIds.isNotEmpty()) {
                    pendingIncomingAssignmentBundleSelectionId = null
                }
                mutableUiState.update {
                    if (it.selectedFriend?.userId != friend.userId) {
                        it
                    } else {
                        val validPendingIds = snapshot.received
                            .pendingDecisionItems()
                            .map { item -> item.id }
                            .toSet()
                        val summary = if (snapshot.hasItems() || !it.friendDetailLoading || it.friendAssignmentSummary != null) {
                            snapshot.toFriendAssignmentSummary(friend.userId)
                        } else {
                            it.friendAssignmentSummary
                        }
                        it.copy(
                            friendAssignmentSummary = summary,
                            friendSentAssignedTodos = snapshot.sent,
                            friendReceivedAssignedTodos = snapshot.received,
                            friendSentCompletedHistoryTodos = snapshot.sentHistory,
                            friendReceivedCompletedHistoryTodos = snapshot.receivedHistory,
                            selectedPendingAssignmentIds = if (bundleSelectedIds.isNotEmpty()) {
                                bundleSelectedIds
                            } else {
                                it.selectedPendingAssignmentIds.intersect(validPendingIds)
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun refreshFriendDetail(
        friend: com.neo.yourtodo.core.model.friends.Friend,
        initialBundleId: String? = null
    ) {
        val friendUserId = friend.userId
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
            if (it.selectedFriend?.userId != friend.userId) {
                it
            } else {
                if (
                    sent.isSuccess &&
                    received.isSuccess &&
                    sentHistory.isSuccess &&
                    receivedHistory.isSuccess
                ) {
                    val sentItems = sent.getOrThrow()
                    val receivedItems = received.getOrThrow()
                    val sentHistoryItems = sentHistory.getOrThrow()
                    val receivedHistoryItems = receivedHistory.getOrThrow()
                    val validPendingIds = receivedItems.pendingDecisionItems().map { item -> item.id }.toSet()
                    val initialSelectedIds = (initialBundleId ?: pendingIncomingAssignmentBundleSelectionId)
                        ?.let { bundleId ->
                            receivedItems.pendingBundleItemIds(bundleId)
                        }
                        .orEmpty()
                    if (initialSelectedIds.isNotEmpty()) {
                        pendingIncomingAssignmentBundleSelectionId = null
                    }
                    it.copy(
                        friendDetailLoading = false,
                        friendAssignmentSummary = buildFriendAssignmentSummary(
                            friendUserId = friendUserId,
                            sent = sentItems,
                            sentHistory = sentHistoryItems,
                            received = receivedItems,
                            receivedHistory = receivedHistoryItems
                        ),
                        friendSentAssignedTodos = sentItems,
                        friendReceivedAssignedTodos = receivedItems,
                        friendSentCompletedHistoryTodos = sentHistoryItems,
                        friendReceivedCompletedHistoryTodos = receivedHistoryItems,
                        selectedPendingAssignmentIds = if (initialSelectedIds.isNotEmpty()) {
                            initialSelectedIds
                        } else {
                            it.selectedPendingAssignmentIds.intersect(validPendingIds)
                        },
                        error = null
                    )
                } else {
                    val failure = listOf(sent, received, sentHistory, receivedHistory)
                        .firstOrNull { result -> result.isFailure }
                        ?.exceptionOrNull()
                    it.copy(
                        friendDetailLoading = false,
                        error = failure.toUiError()
                    )
                }
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
                refreshWorkspaceAfterAssignmentDecision()
                refreshFriendDetail(friend)
                mutableSideEffect.emit(FriendsSideEffect.ShowSnackbar(successMessage.messageRes))
            } else {
                if (succeededItemIds.isNotEmpty()) {
                    mutableUiState.update {
                        it.copy(selectedPendingAssignmentIds = it.selectedPendingAssignmentIds - succeededItemIds)
                    }
                    refreshWorkspaceUseCase()
                    refreshFriendDetail(friend)
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
        val assignmentMode = friend.assignmentModeForNewBundle()

        runMutation(
            key = "assignment:${friend.userId}",
            successMessage = if (assignmentMode == AssignmentMode.DIRECT) {
                FriendsMessage.ASSIGNMENT_DIRECT_SENT
            } else {
                FriendsMessage.ASSIGNMENT_SENT
            },
            onSuccess = {
                mutableUiState.update {
                    it.copy(
                        assignmentTargetFriend = null,
                        assignmentDraftItems = emptyList(),
                        assignmentTitleInput = "",
                        assignmentDueDateInput = "",
                        assignmentDueTimeInput = "",
                        assignmentMode = AssignmentMode.REQUEST,
                        assignmentInputErrorMessageRes = null
                    )
                }
            }
        ) {
            createAssignmentBundle(friend.userId, items, assignmentMode).also { result ->
                val selectedFriend = uiState.value.selectedFriend
                if (result.isSuccess && selectedFriend?.userId == friend.userId) {
                    refreshFriendDetail(selectedFriend)
                }
            }.map { Unit }
        }
    }

    private fun runDirectAssignmentOptInMutation(
        friend: Friend,
        enabled: Boolean
    ) {
        runMutation(
            key = "direct_assignment_opt_in:${friend.userId}",
            successMessage = if (enabled) {
                FriendsMessage.DIRECT_ASSIGNMENT_OPT_IN_ENABLED
            } else {
                FriendsMessage.DIRECT_ASSIGNMENT_OPT_IN_DISABLED
            },
            onSuccess = {
                val selectedFriend = uiState.value.selectedFriend
                if (selectedFriend?.userId == friend.userId) {
                    viewModelScope.launch { refreshFriendDetail(selectedFriend) }
                }
            },
        ) {
            setDirectAssignmentOptIn(friend.userId, enabled).map { Unit }
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
            try {
                val result = block()
                result.exceptionOrNull().throwIfCancellation()
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
            } finally {
                mutableUiState.update {
                    if (it.runningActionKey == key) {
                        it.copy(runningActionKey = null)
                    } else {
                        it
                    }
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
                val refreshedFriends = friends.getOrThrow()
                val refreshedAssignmentTarget = it.assignmentTargetFriend?.let { target ->
                    refreshedFriends.firstOrNull { friend -> friend.userId == target.userId } ?: target
                }
                it.copy(
                    friends = refreshedFriends,
                    incomingRequests = incoming.getOrThrow(),
                    outgoingRequests = outgoing.getOrThrow(),
                    hasLoadedFriendsSnapshot = true,
                    friendsSnapshotError = null,
                    selectedFriend = it.selectedFriend?.let { selected ->
                        refreshedFriends.firstOrNull { friend -> friend.userId == selected.userId } ?: selected
                    },
                    assignmentTargetFriend = refreshedAssignmentTarget,
                    assignmentMode = refreshedAssignmentTarget?.assignmentModeForNewBundle() ?: it.assignmentMode,
                    runningActionKey = null,
                    error = null
                )
            } else {
                val failure = listOf(friends, incoming, outgoing)
                    .firstOrNull { result -> result.isFailure }
                    ?.exceptionOrNull()
                failure.throwIfCancellation()
                val uiError = failure.toUiError()
                it.copy(
                    runningActionKey = null,
                    friendsSnapshotError = if (it.hasLoadedFriendsSnapshot) {
                        it.friendsSnapshotError
                    } else {
                        uiError
                    },
                    error = uiError
                )
            }
        }
    }

    private suspend fun refreshWorkspaceAfterAssignmentDecision() {
        refreshWorkspaceUseCase()
        refreshAfterMutation()
    }

    private fun Throwable?.throwIfCancellation() {
        if (this is CancellationException) throw this
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
