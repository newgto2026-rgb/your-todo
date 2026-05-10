package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoTerminalReason
import java.time.Instant

internal fun visibleTaskSurfaceAssignedTodos(
    vararg groups: List<AssignedTodo>
): List<AssignedTodo> =
    groups
        .asSequence()
        .flatten()
        .filter { it.status.isVisibleOnTaskSurfaces() }
        .distinctBy { it.id }
        .sortedWith(
            compareBy<AssignedTodo> { it.status.sortRank() }
                .thenBy { it.dueDate }
                .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
                .thenBy { it.title }
        )
        .toList()

internal fun visibleFriendDetailAssignedTodos(
    vararg groups: List<AssignedTodo>
): List<AssignedTodo> =
    groups
        .asSequence()
        .flatten()
        .filter { it.isVisibleOnFriendDetail() }
        .distinctBy { it.id }
        .sortedWith(
            compareBy<AssignedTodo> { it.status.sortRank() }
                .thenByDescending { it.createdAt ?: Instant.EPOCH }
                .thenBy { it.dueDate }
                .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
                .thenBy { it.title }
        )
        .toList()

internal fun completedFriendDetailHistoryAssignedTodos(
    vararg groups: List<AssignedTodo>
): List<AssignedTodo> =
    groups
        .asSequence()
        .flatten()
        .filter { it.isVisibleOnCompletedHistory() }
        .map { it.asCompletedHistoryTodo() }
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<AssignedTodo> { it.completedAt ?: it.createdAt ?: Instant.EPOCH }
                .thenBy { it.title }
        )
        .toList()

private fun AssignedTodoStatus.isVisibleOnTaskSurfaces(): Boolean = when (this) {
    AssignedTodoStatus.ACCEPTED,
    AssignedTodoStatus.IN_PROGRESS,
    AssignedTodoStatus.DONE -> true

    AssignedTodoStatus.PENDING_ACCEPTANCE,
    AssignedTodoStatus.REJECTED,
    AssignedTodoStatus.CANCELED -> false
}

private fun AssignedTodo.isVisibleOnFriendDetail(): Boolean = when (status) {
    AssignedTodoStatus.PENDING_ACCEPTANCE,
    AssignedTodoStatus.ACCEPTED,
    AssignedTodoStatus.IN_PROGRESS -> true

    AssignedTodoStatus.DONE,
    AssignedTodoStatus.REJECTED,
    AssignedTodoStatus.CANCELED -> false
}

private fun AssignedTodo.isVisibleOnCompletedHistory(): Boolean = when (status) {
    AssignedTodoStatus.DONE,
    AssignedTodoStatus.CANCELED -> true

    AssignedTodoStatus.REJECTED ->
        terminalReason == AssignedTodoTerminalReason.REJECTED_BY_RECEIVER ||
            (
                terminalReason == AssignedTodoTerminalReason.DELETED_BY_RECEIVER &&
                    completedAt != null
                )

    AssignedTodoStatus.PENDING_ACCEPTANCE,
    AssignedTodoStatus.ACCEPTED,
    AssignedTodoStatus.IN_PROGRESS -> false
}

private fun AssignedTodo.asCompletedHistoryTodo(): AssignedTodo =
    if (
        status == AssignedTodoStatus.REJECTED &&
        terminalReason == AssignedTodoTerminalReason.DELETED_BY_RECEIVER &&
        completedAt != null
    ) {
        copy(
            status = AssignedTodoStatus.DONE,
            terminalReason = null,
            progressPercent = 100
        )
    } else {
        this
    }

private fun AssignedTodoStatus.sortRank(): Int = when (this) {
    AssignedTodoStatus.PENDING_ACCEPTANCE -> 0
    AssignedTodoStatus.ACCEPTED,
    AssignedTodoStatus.IN_PROGRESS -> 1

    AssignedTodoStatus.DONE -> 2
    AssignedTodoStatus.REJECTED,
    AssignedTodoStatus.CANCELED -> 3
}
