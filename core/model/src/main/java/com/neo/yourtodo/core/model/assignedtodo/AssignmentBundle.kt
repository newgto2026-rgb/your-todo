package com.neo.yourtodo.core.model.assignedtodo

data class AssignmentBundle(
    val id: String,
    val sender: AssignedTodoUser,
    val receiver: AssignedTodoUser,
    val status: AssignmentBundleStatus,
    val summary: AssignmentSummary,
    val items: List<AssignedTodo>
)

enum class AssignmentBundleStatus {
    SENT,
    PARTIALLY_DECIDED,
    ACCEPTED,
    REJECTED,
    CANCELED,
    DONE
}

data class AssignmentSummary(
    val totalCount: Int,
    val pendingCount: Int,
    val acceptedCount: Int,
    val inProgressCount: Int,
    val doneCount: Int,
    val rejectedCount: Int,
    val canceledCount: Int,
    val progressPercent: Int
)

data class FriendAssignmentSummary(
    val friendUserId: String,
    val sent: AssignmentSummary,
    val received: AssignmentSummary
)

data class AssignmentDraftItem(
    val title: String,
    val description: String?,
    val dueDate: String?,
    val dueTimeMinutes: Int? = null,
    val priority: com.neo.yourtodo.core.model.TodoPriority,
    val category: String?
)

enum class AssignmentMode {
    REQUEST,
    DIRECT
}

enum class AssignmentDecision {
    ACCEPT,
    REJECT
}
