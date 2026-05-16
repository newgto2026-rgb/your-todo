package com.neo.yourtodo.feature.friends.impl.ui

import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoUser
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.AssignmentSummary
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendshipStatus

internal data class FriendAssignmentCacheSnapshot(
    val sent: List<AssignedTodo>,
    val received: List<AssignedTodo>,
    val sentHistory: List<AssignedTodo>,
    val receivedHistory: List<AssignedTodo>
)

internal fun FriendAssignmentCacheSnapshot.hasItems(): Boolean =
    sent.isNotEmpty() || received.isNotEmpty() || sentHistory.isNotEmpty() || receivedHistory.isNotEmpty()

internal fun FriendAssignmentCacheSnapshot.toFriendAssignmentSummary(friendUserId: String): FriendAssignmentSummary =
    buildFriendAssignmentSummary(
        friendUserId = friendUserId,
        sent = sent,
        sentHistory = sentHistory,
        received = received,
        receivedHistory = receivedHistory
    )

internal fun buildFriendAssignmentSummary(
    friendUserId: String,
    sent: List<AssignedTodo>,
    sentHistory: List<AssignedTodo>,
    received: List<AssignedTodo>,
    receivedHistory: List<AssignedTodo>
): FriendAssignmentSummary =
    FriendAssignmentSummary(
        friendUserId = friendUserId,
        sent = buildAssignmentSummary(sent, sentHistory),
        received = buildAssignmentSummary(received, receivedHistory)
    )

private fun buildAssignmentSummary(
    currentItems: List<AssignedTodo>,
    historyItems: List<AssignedTodo>
): AssignmentSummary {
    val items = (currentItems + historyItems)
        .fold(linkedMapOf<String, AssignedTodo>()) { byId, item ->
            byId[item.id] = item
            byId
        }
        .values
    val totalCount = items.size
    val doneCount = items.count { it.status == AssignedTodoStatus.DONE }
    return AssignmentSummary(
        totalCount = totalCount,
        pendingCount = items.count { it.status == AssignedTodoStatus.PENDING_ACCEPTANCE },
        acceptedCount = items.count { it.status == AssignedTodoStatus.ACCEPTED },
        inProgressCount = items.count { it.status == AssignedTodoStatus.IN_PROGRESS },
        doneCount = doneCount,
        rejectedCount = items.count { it.status == AssignedTodoStatus.REJECTED },
        canceledCount = items.count { it.status == AssignedTodoStatus.CANCELED },
        progressPercent = if (totalCount == 0) 0 else doneCount * 100 / totalCount
    )
}

internal data class IncomingAssignmentTarget(
    val friendUserId: String?,
    val friendNickname: String?,
    val bundleId: String?
)

internal fun IncomingAssignmentTarget.matches(other: IncomingAssignmentTarget): Boolean =
    friendUserId == other.friendUserId &&
        friendNickname == other.friendNickname &&
        bundleId == other.bundleId

internal fun List<AssignedTodo>.firstPendingIncomingAssignment(bundleId: String?): AssignedTodo? =
    firstOrNull { item ->
        item.status == AssignedTodoStatus.PENDING_ACCEPTANCE &&
            item.assignmentMode == AssignmentMode.REQUEST &&
            item.bundleId != null &&
            (bundleId == null || item.bundleId == bundleId)
    }

internal fun IncomingAssignmentTarget.toIncomingAssignmentFriendOrNull(): Friend? {
    val userId = friendUserId ?: return null
    val nickname = friendNickname ?: return null
    return Friend(
        friendshipId = userId,
        userId = userId,
        nickname = nickname,
        status = FriendshipStatus.ACTIVE,
        createdAt = "",
        removedAt = null
    )
}

internal fun AssignedTodoUser.toIncomingAssignmentFriend(): Friend =
    Friend(
        friendshipId = id,
        userId = id,
        nickname = nickname,
        status = FriendshipStatus.ACTIVE,
        createdAt = "",
        removedAt = null
    )

internal fun FriendsUiState.decisionPendingAssignedTodos(): List<AssignedTodo> =
    friendReceivedAssignedTodos.pendingDecisionItems()

internal fun List<AssignedTodo>.pendingDecisionItems(): List<AssignedTodo> =
    filter { item ->
        item.status == AssignedTodoStatus.PENDING_ACCEPTANCE &&
            item.assignmentMode == AssignmentMode.REQUEST &&
            item.bundleId != null
    }

internal fun List<AssignedTodo>.pendingBundleItemIds(bundleId: String): Set<String> =
    pendingDecisionItems()
        .filter { item -> item.bundleId == bundleId }
        .map { item -> item.id }
        .toSet()

internal fun dueTimeTextToMinutes(value: String): Int? {
    if (value.isBlank()) return null
    val parts = value.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
