package com.neo.yourtodo.feature.friends.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.usecase.CreateAssignmentBundleUseCase
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendRequestsUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendAssignmentSummaryUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.RemoveFriendUseCase
import com.neo.yourtodo.core.domain.usecase.RespondFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.SendFriendRequestUseCase
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
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
                    assignmentDraftItems = emptyList(),
                    assignmentTitleInput = "",
                    assignmentDescriptionInput = "",
                    assignmentDueDateInput = ""
                )
            }
            is FriendsAction.OnAssignmentTitleChanged -> mutableUiState.update {
                it.copy(assignmentTitleInput = action.value.take(MaxAssignmentTitleLength), error = null)
            }
            is FriendsAction.OnAssignmentDescriptionChanged -> mutableUiState.update {
                it.copy(assignmentDescriptionInput = action.value.take(MaxAssignmentDescriptionLength), error = null)
            }
            is FriendsAction.OnAssignmentDueDateChanged -> mutableUiState.update {
                it.copy(assignmentDueDateInput = action.value.take(MaxDueDateLength), error = null)
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

            val friends = getFriends()
            val incoming = getFriendRequests.incoming()
            val outgoing = getFriendRequests.outgoing()

            if (friends.isSuccess && incoming.isSuccess && outgoing.isSuccess) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        friends = friends.getOrThrow(),
                        incomingRequests = incoming.getOrThrow(),
                        outgoingRequests = outgoing.getOrThrow(),
                        error = null
                    )
                }
            } else {
                val failure = listOf(friends, incoming, outgoing)
                    .firstOrNull { it.isFailure }
                    ?.exceptionOrNull()
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = failure.toUiError()
                    )
                }
            }
        }
    }

    private fun openFriendDetail(friend: com.neo.yourtodo.core.model.friends.Friend) {
        mutableUiState.update {
            it.copy(
                selectedFriend = friend,
                friendDetailLoading = true,
                error = null
            )
        }
        viewModelScope.launch {
            refreshFriendDetail(friend.userId)
        }
    }

    private suspend fun refreshFriendDetail(friendUserId: String) {
        val summary = getFriendAssignmentSummary(friendUserId)
        val sent = getAssignedTodos.byFriend(
            friendUserId = friendUserId,
            direction = AssignmentDirection.SENT,
            status = AssignmentFeedStatus.ACTIVE
        )
        val received = getAssignedTodos.byFriend(
            friendUserId = friendUserId,
            direction = AssignmentDirection.RECEIVED,
            status = AssignmentFeedStatus.ACTIVE
        )

        mutableUiState.update {
            if (summary.isSuccess && sent.isSuccess && received.isSuccess) {
                it.copy(
                    friendDetailLoading = false,
                    friendAssignmentSummary = summary.getOrThrow(),
                    friendSentAssignedTodos = sent.getOrThrow(),
                    friendReceivedAssignedTodos = received.getOrThrow(),
                    error = null
                )
            } else {
                val failure = listOf(summary, sent, received)
                    .firstOrNull { result -> result.isFailure }
                    ?.exceptionOrNull()
                it.copy(
                    friendDetailLoading = false,
                    error = failure.toUiError()
                )
            }
        }
    }

    private fun addAssignmentDraft() {
        val item = currentAssignmentDraftOrNull() ?: return
        mutableUiState.update {
            it.copy(
                assignmentDraftItems = it.assignmentDraftItems + item,
                assignmentTitleInput = "",
                assignmentDescriptionInput = "",
                assignmentDueDateInput = "",
                error = null
            )
        }
    }

    private fun sendAssignment(includeDrafts: Boolean) {
        val state = uiState.value
        val friend = state.selectedFriend ?: return
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
                        assignmentDraftItems = emptyList(),
                        assignmentTitleInput = "",
                        assignmentDescriptionInput = "",
                        assignmentDueDateInput = ""
                    )
                }
            }
        ) {
            createAssignmentBundle(friend.userId, items).also { result ->
                if (result.isSuccess) refreshFriendDetail(friend.userId)
            }.map { Unit }
        }
    }

    private fun currentAssignmentDraftOrNull(): AssignmentDraftItem? {
        val state = uiState.value
        val title = state.assignmentTitleInput.trim()
        if (title.isBlank()) return null
        return AssignmentDraftItem(
            title = title,
            description = state.assignmentDescriptionInput.trim().takeIf { it.isNotBlank() },
            dueDate = state.assignmentDueDateInput.trim().takeIf { it.isNotBlank() },
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

    private fun Throwable?.toUiError(): FriendsError =
        if (this is AuthRequiredException) FriendsError.AUTH_REQUIRED else FriendsError.NETWORK

    private companion object {
        const val MaxNicknameLength = 12
        const val MaxAssignmentTitleLength = 80
        const val MaxAssignmentDescriptionLength = 240
        const val MaxDueDateLength = 10
    }
}
