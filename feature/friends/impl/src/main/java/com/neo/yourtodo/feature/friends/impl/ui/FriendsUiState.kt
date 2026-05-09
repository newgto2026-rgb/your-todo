package com.neo.yourtodo.feature.friends.impl.ui

import androidx.annotation.StringRes
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.feature.friends.impl.R

data class FriendsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val profileInitial: String? = null,
    val friends: List<Friend> = emptyList(),
    val incomingRequests: List<FriendRequest> = emptyList(),
    val outgoingRequests: List<FriendRequest> = emptyList(),
    val addFriendExpanded: Boolean = false,
    val nicknameInput: String = "",
    val selectedFriend: Friend? = null,
    val assignmentTargetFriend: Friend? = null,
    val friendDetailLoading: Boolean = false,
    val friendAssignmentSummary: FriendAssignmentSummary? = null,
    val friendSentAssignedTodos: List<AssignedTodo> = emptyList(),
    val friendReceivedAssignedTodos: List<AssignedTodo> = emptyList(),
    val friendSentCompletedHistoryTodos: List<AssignedTodo> = emptyList(),
    val friendReceivedCompletedHistoryTodos: List<AssignedTodo> = emptyList(),
    val showFriendAssignmentHistory: Boolean = false,
    val selectedPendingAssignmentIds: Set<String> = emptySet(),
    val assignmentTitleInput: String = "",
    val assignmentDueDateInput: String = "",
    val assignmentDueTimeInput: String = "",
    val assignmentPriority: TodoPriority = TodoPriority.MEDIUM,
    val assignmentDraftItems: List<AssignmentDraftItem> = emptyList(),
    @StringRes val assignmentInputErrorMessageRes: Int? = null,
    val runningActionKey: String? = null,
    val error: FriendsError? = null
) {
    val canSendRequest: Boolean
        get() = nicknameInput.trim().isNotEmpty() && runningActionKey == null

    val assignmentDetail: FriendAssignmentDetailUiModel
        get() {
            val pending = friendReceivedAssignedTodos
                .pendingDecisionItems()
                .map {
                    it.toAssignmentTodoUiModel(
                        selected = it.id in selectedPendingAssignmentIds
                    )
                }
            return FriendAssignmentDetailUiModel(
                sentItems = friendSentAssignedTodos.map {
                    it.toAssignmentTodoUiModel()
                },
                pendingReceivedItems = pending,
                activeReceivedItems = friendReceivedAssignedTodos
                    .filterNot { it.status == AssignedTodoStatus.PENDING_ACCEPTANCE }
                    .map {
                        it.toAssignmentTodoUiModel()
                    },
                sentHistoryItems = friendSentCompletedHistoryTodos.map {
                    it.toAssignmentTodoUiModel()
                },
                receivedHistoryItems = friendReceivedCompletedHistoryTodos.map {
                    it.toAssignmentTodoUiModel()
                },
                showHistory = showFriendAssignmentHistory,
                pendingSelectedCount = pending.count { it.selected },
                pendingTotalCount = pending.size,
                isAllPendingSelected = pending.isNotEmpty() && pending.all { it.selected },
                hasPendingSelection = pending.any { it.selected },
                isDecisionRunning = runningActionKey == "assignment_decision"
            )
        }
}

data class FriendAssignmentDetailUiModel(
    val sentItems: List<AssignmentTodoUiModel> = emptyList(),
    val pendingReceivedItems: List<AssignmentTodoUiModel> = emptyList(),
    val activeReceivedItems: List<AssignmentTodoUiModel> = emptyList(),
    val sentHistoryItems: List<AssignmentTodoUiModel> = emptyList(),
    val receivedHistoryItems: List<AssignmentTodoUiModel> = emptyList(),
    val showHistory: Boolean = false,
    val pendingSelectedCount: Int = 0,
    val pendingTotalCount: Int = 0,
    val isAllPendingSelected: Boolean = false,
    val hasPendingSelection: Boolean = false,
    val isDecisionRunning: Boolean = false
)

data class AssignmentTodoUiModel(
    val id: String,
    val title: String,
    val progressPercent: Int,
    val showProgress: Boolean,
    @StringRes val statusLabelRes: Int,
    val statusStyle: AssignmentTodoStatusStyle,
    val selected: Boolean = false
)

enum class AssignmentTodoStatusStyle {
    PENDING,
    ACCEPTED,
    IN_PROGRESS,
    DONE,
    REJECTED,
    CANCELED
}

internal fun AssignedTodo.toAssignmentTodoUiModel(
    selected: Boolean = false
): AssignmentTodoUiModel = AssignmentTodoUiModel(
    id = id,
    title = title,
    progressPercent = progressPercent.coerceIn(0, 100),
    showProgress = checklist.isNotEmpty(),
    statusLabelRes = status.statusLabelRes(),
    statusStyle = status.statusStyle(),
    selected = selected
)

@StringRes
internal fun AssignedTodoStatus.statusLabelRes(): Int = when (this) {
    AssignedTodoStatus.PENDING_ACCEPTANCE -> R.string.friends_assignment_status_pending
    AssignedTodoStatus.ACCEPTED -> R.string.friends_assignment_status_accepted
    AssignedTodoStatus.IN_PROGRESS -> R.string.friends_assignment_status_in_progress
    AssignedTodoStatus.DONE -> R.string.friends_assignment_status_done
    AssignedTodoStatus.REJECTED -> R.string.friends_assignment_status_rejected
    AssignedTodoStatus.CANCELED -> R.string.friends_assignment_status_canceled
}

internal fun AssignedTodoStatus.statusStyle(): AssignmentTodoStatusStyle = when (this) {
    AssignedTodoStatus.PENDING_ACCEPTANCE -> AssignmentTodoStatusStyle.PENDING
    AssignedTodoStatus.ACCEPTED -> AssignmentTodoStatusStyle.ACCEPTED
    AssignedTodoStatus.IN_PROGRESS -> AssignmentTodoStatusStyle.IN_PROGRESS
    AssignedTodoStatus.DONE -> AssignmentTodoStatusStyle.DONE
    AssignedTodoStatus.REJECTED -> AssignmentTodoStatusStyle.REJECTED
    AssignedTodoStatus.CANCELED -> AssignmentTodoStatusStyle.CANCELED
}

sealed interface FriendsAction {
    data object OnRefresh : FriendsAction
    data object OnToggleAddFriend : FriendsAction
    data object OnCloseAddFriend : FriendsAction
    data class OnNicknameChanged(val value: String) : FriendsAction
    data object OnSendRequest : FriendsAction
    data class OnAcceptRequest(val requestId: String) : FriendsAction
    data class OnDeclineRequest(val requestId: String) : FriendsAction
    data class OnRemoveFriend(val friendshipId: String) : FriendsAction
    data class OnFriendClick(val friend: Friend) : FriendsAction
    data object OnCloseFriendDetail : FriendsAction
    data object OnToggleAssignmentHistory : FriendsAction
    data class OnTogglePendingAssignment(val assignedTodoId: String) : FriendsAction
    data object OnToggleAllPendingAssignments : FriendsAction
    data object OnAcceptSelectedAssignments : FriendsAction
    data object OnRejectSelectedAssignments : FriendsAction
    data class OnOpenAssignmentEditor(val friend: Friend) : FriendsAction
    data object OnCloseAssignmentEditor : FriendsAction
    data class OnAssignmentTitleChanged(val value: String) : FriendsAction
    data class OnAssignmentDueDateChanged(val value: String) : FriendsAction
    data class OnAssignmentDueTimeChanged(val value: String) : FriendsAction
    data class OnAssignmentPriorityChanged(val value: TodoPriority) : FriendsAction
    data object OnAddAssignmentDraft : FriendsAction
    data class OnRemoveAssignmentDraft(val index: Int) : FriendsAction
    data object OnSendAssignmentNow : FriendsAction
    data object OnSendAssignmentDrafts : FriendsAction
    data object OnErrorShown : FriendsAction
}

sealed interface FriendsSideEffect {
    data class ShowSnackbar(@StringRes val messageRes: Int) : FriendsSideEffect
}

enum class FriendsError(@StringRes val messageRes: Int) {
    AUTH_REQUIRED(R.string.friends_error_auth_required),
    NETWORK(R.string.friends_error_network)
}

enum class FriendsMessage(@StringRes val messageRes: Int) {
    REQUEST_SENT(R.string.friends_request_sent),
    REQUEST_ACCEPTED(R.string.friends_request_accepted),
    REQUEST_DECLINED(R.string.friends_request_declined),
    FRIEND_REMOVED(R.string.friends_removed),
    ASSIGNMENT_SENT(R.string.friends_assignment_sent),
    ASSIGNMENT_ACCEPTED(R.string.friends_assignment_accepted),
    ASSIGNMENT_REJECTED(R.string.friends_assignment_rejected)
}
