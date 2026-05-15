package com.neo.yourtodo.core.network.assignments

import kotlinx.serialization.Serializable

@Serializable
data class NetworkCreateAssignmentBundleRequest(
    val receiverUserId: String,
    val assignmentMode: String = "REQUEST",
    val items: List<NetworkCreateAssignmentItem>
)

@Serializable
data class NetworkCreateAssignmentItem(
    val clientItemId: String,
    val title: String,
    val description: String? = null,
    val dueDate: String? = null,
    val dueTimeMinutes: Int? = null,
    val priority: String,
    val category: String? = null
)

@Serializable
data class NetworkDecideAssignmentItemsRequest(
    val decisions: List<NetworkAssignmentDecision>
)

@Serializable
data class NetworkAssignmentDecision(
    val assignedTodoId: String,
    val decision: String
)

@Serializable
data class NetworkUpdateChecklistItemRequest(
    val completed: Boolean
)

@Serializable
data class NetworkUpsertAssignedTodoReminderRequest(
    val reminderAt: String,
    val enabled: Boolean = true
)

@Serializable
data class NetworkAssignmentBundleResponse(
    val bundle: NetworkAssignmentBundle,
    val items: List<NetworkAssignedTodo>
)

@Serializable
data class NetworkAssignedTodosResponse(
    val items: List<NetworkAssignedTodo>
)

@Serializable
data class NetworkAssignedTodoMutationResponse(
    val item: NetworkAssignedTodoMutationItem,
    val bundle: NetworkAssignedTodoMutationBundle? = null
)

@Serializable
data class NetworkAssignedTodoMutationBundle(
    val id: String? = null,
    val status: String? = null,
    val summary: NetworkAssignmentSummary? = null
)

@Serializable
data class NetworkAssignedTodoMutationItem(
    val id: String,
    val bundleId: String? = null,
    val source: String? = null,
    val assignmentMode: String? = null,
    val sender: NetworkAssignmentUser? = null,
    val receiver: NetworkAssignmentUser? = null,
    val title: String? = null,
    val description: String? = null,
    val dueDate: String? = null,
    val dueTimeMinutes: Int? = null,
    val priority: String? = null,
    val category: String? = null,
    val status: String? = null,
    val terminalReason: String? = null,
    val progressPercent: Int? = null,
    val checklist: List<NetworkAssignedTodoChecklistItem>? = null,
    val reminder: NetworkAssignedTodoReminder? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val completedAt: String? = null
)

@Serializable
data class NetworkAssignedTodoReminderResponse(
    val reminder: NetworkAssignedTodoReminder?
)

@Serializable
data class NetworkAssignmentBundle(
    val id: String,
    val sender: NetworkAssignmentUser,
    val receiver: NetworkAssignmentUser,
    val status: String,
    val summary: NetworkAssignmentSummary,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class NetworkAssignedTodo(
    val id: String,
    val bundleId: String? = null,
    val source: String? = null,
    val assignmentMode: String? = null,
    val sender: NetworkAssignmentUser? = null,
    val receiver: NetworkAssignmentUser? = null,
    val title: String,
    val description: String? = null,
    val dueDate: String? = null,
    val dueTimeMinutes: Int? = null,
    val priority: String,
    val category: String? = null,
    val status: String,
    val terminalReason: String? = null,
    val progressPercent: Int,
    val checklist: List<NetworkAssignedTodoChecklistItem> = emptyList(),
    val reminder: NetworkAssignedTodoReminder? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val completedAt: String? = null
)

@Serializable
data class NetworkAssignedTodoChecklistItem(
    val id: String,
    val title: String,
    val sortOrder: Int? = null,
    val completed: Boolean,
    val completedAt: String? = null
)

@Serializable
data class NetworkAssignedTodoReminder(
    val reminderAt: String,
    val enabled: Boolean
)

@Serializable
data class NetworkAssignmentUser(
    val id: String,
    val nickname: String
)

@Serializable
data class NetworkAssignmentSummary(
    val totalCount: Int,
    val pendingCount: Int,
    val acceptedCount: Int,
    val inProgressCount: Int = 0,
    val doneCount: Int,
    val rejectedCount: Int,
    val canceledCount: Int,
    val progressPercent: Int = 0
)

@Serializable
data class NetworkFriendAssignmentSummaryResponse(
    val friendUserId: String,
    val sent: NetworkAssignmentSummary,
    val received: NetworkAssignmentSummary
)

@Serializable
data class NetworkDirectAssignmentConsentSummaryResponse(
    val directAssignment: NetworkDirectAssignmentConsentSummary
)

@Serializable
data class NetworkDirectAssignmentConsentSummary(
    val grantedByMe: String = "NONE",
    val grantedToMe: String = "NONE"
)
