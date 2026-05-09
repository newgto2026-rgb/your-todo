package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

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
    vararg groups: List<AssignedTodo>,
    now: Instant = Instant.now()
): List<AssignedTodo> =
    groups
        .asSequence()
        .flatten()
        .filter { it.isVisibleOnFriendDetail(now) }
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
        .filter { it.status == AssignedTodoStatus.DONE }
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<AssignedTodo> { it.completedAt ?: Instant.EPOCH }
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

private fun AssignedTodo.isVisibleOnFriendDetail(now: Instant): Boolean = when (status) {
    AssignedTodoStatus.PENDING_ACCEPTANCE,
    AssignedTodoStatus.ACCEPTED,
    AssignedTodoStatus.IN_PROGRESS -> true

    AssignedTodoStatus.DONE -> completedAt.isRecentCompletion(now)

    AssignedTodoStatus.REJECTED,
    AssignedTodoStatus.CANCELED -> false
}

private fun Instant?.isRecentCompletion(now: Instant): Boolean {
    val threshold = now.minus(COMPLETED_MONITORING_WINDOW_DAYS, ChronoUnit.DAYS)
    return this != null && !isBefore(threshold)
}

private fun AssignedTodoStatus.sortRank(): Int = when (this) {
    AssignedTodoStatus.PENDING_ACCEPTANCE -> 0
    AssignedTodoStatus.ACCEPTED,
    AssignedTodoStatus.IN_PROGRESS -> 1

    AssignedTodoStatus.DONE -> 2
    AssignedTodoStatus.REJECTED,
    AssignedTodoStatus.CANCELED -> 3
}

private const val COMPLETED_MONITORING_WINDOW_DAYS = 7L
