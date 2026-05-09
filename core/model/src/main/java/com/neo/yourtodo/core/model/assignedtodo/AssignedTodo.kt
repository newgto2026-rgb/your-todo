package com.neo.yourtodo.core.model.assignedtodo

import com.neo.yourtodo.core.model.TodoPriority
import java.time.LocalDate

data class AssignedTodo(
    val id: String,
    val bundleId: String?,
    val title: String,
    val description: String?,
    val dueDate: LocalDate?,
    val dueTimeMinutes: Int? = null,
    val priority: TodoPriority,
    val category: String?,
    val status: AssignedTodoStatus,
    val terminalReason: AssignedTodoTerminalReason?,
    val progressPercent: Int,
    val sender: AssignedTodoUser?,
    val receiver: AssignedTodoUser?,
    val reminder: AssignedTodoReminder?,
    val checklist: List<AssignedTodoChecklistItem> = emptyList()
) {
    val isDone: Boolean
        get() = status == AssignedTodoStatus.DONE

    val isActive: Boolean
        get() = status == AssignedTodoStatus.ACCEPTED || status == AssignedTodoStatus.IN_PROGRESS
}

data class AssignedTodoUser(
    val id: String,
    val nickname: String
) {
    val initial: String
        get() = nickname.trim().firstOrNull()?.uppercase() ?: "?"
}

data class AssignedTodoChecklistItem(
    val id: String,
    val title: String,
    val completed: Boolean
)

data class AssignedTodoReminder(
    val reminderAt: String,
    val enabled: Boolean
)

enum class AssignedTodoStatus {
    PENDING_ACCEPTANCE,
    ACCEPTED,
    IN_PROGRESS,
    DONE,
    REJECTED,
    CANCELED
}

enum class AssignedTodoTerminalReason {
    REJECTED_BY_RECEIVER,
    DELETED_BY_RECEIVER,
    CANCELED_BY_SENDER
}
