package com.neo.yourtodo.feature.friends.impl.ui

import androidx.annotation.StringRes
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
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
    ASSIGNMENT_SENT(R.string.friends_assignment_sent)
}
