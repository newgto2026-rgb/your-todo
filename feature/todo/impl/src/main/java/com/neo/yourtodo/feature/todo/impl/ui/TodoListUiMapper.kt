package com.neo.yourtodo.feature.todo.impl.ui

import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun buildTodoListUiState(
    localState: TodoListUiState,
    items: List<TodoItem>,
    assignedItems: List<AssignedTodo>,
    selectedFilter: TodoFilter,
    selectedPriorityFilter: TodoPriorityFilter,
    profileInitial: String?
): TodoListUiState {
    val optimisticCompletedAssignedTodoIds = localState.optimisticCompletedAssignedTodoIds
    val optimisticActiveAssignedTodoIds = localState.optimisticActiveAssignedTodoIds
    val optimisticDeletedAssignedTodoIds = localState.optimisticDeletedAssignedTodoIds
    val uiItems = items.map { it.toUiModel() } + assignedItems.map {
        it.toUiModel(
            isOptimisticallyDone = it.id in optimisticCompletedAssignedTodoIds,
            isOptimisticallyActive = it.id in optimisticActiveAssignedTodoIds
        )
    }.filterNot { it.assignedTodoId in optimisticDeletedAssignedTodoIds }
    val filteredItems = uiItems
        .filterBy(selectedFilter)
        .filterByPriority(selectedPriorityFilter)
        .sortedWith(localState.selectedSortOption.comparatorFor(selectedFilter))

    return localState.copy(
        profileInitial = profileInitial,
        items = filteredItems,
        completedTodoIds = items.filter { it.isDone }.map { it.id },
        completedAssignedTodoIds = assignedItems
            .filter {
                it.id !in optimisticDeletedAssignedTodoIds &&
                    it.id !in optimisticActiveAssignedTodoIds &&
                    (it.isDone || it.id in optimisticCompletedAssignedTodoIds)
            }
            .map { it.id },
        selectedFilter = selectedFilter,
        selectedPriorityFilter = selectedPriorityFilter,
        isLoading = false
    )
}

private fun List<TodoItemUiModel>.filterBy(filter: TodoFilter): List<TodoItemUiModel> {
    val today = LocalDate.now()
    return when (filter) {
        TodoFilter.ALL -> this
        TodoFilter.TODAY -> filter {
            !it.isDone && (
                it.dueDate == today ||
                    it.dueDate?.isBefore(today) == true
                )
        }
        TodoFilter.COMPLETED -> filter { it.isDone }
    }
}

private fun List<TodoItemUiModel>.filterByPriority(filter: TodoPriorityFilter): List<TodoItemUiModel> = when (filter) {
    TodoPriorityFilter.ALL -> this
    TodoPriorityFilter.LOW -> filter { it.priority == TodoPriority.LOW }
    TodoPriorityFilter.MEDIUM -> filter { it.priority == TodoPriority.MEDIUM }
    TodoPriorityFilter.HIGH -> filter { it.priority == TodoPriority.HIGH }
}

private fun TodoSortOption.comparatorFor(filter: TodoFilter): Comparator<TodoItemUiModel> =
    if (filter == TodoFilter.ALL) {
        when (this) {
            TodoSortOption.DEFAULT -> defaultTodoComparator()
            TodoSortOption.DUE_DATE -> dueDateTodoComparator()
            TodoSortOption.PRIORITY -> priorityTodoComparator()
        }
    } else {
        contextualTodoComparator()
    }

private fun defaultTodoComparator(): Comparator<TodoItemUiModel> =
    compareBy<TodoItemUiModel> { it.isDone }
        .thenByDescending { it.id }

private fun contextualTodoComparator(): Comparator<TodoItemUiModel> =
    compareBy<TodoItemUiModel> { it.isDone }
        .thenByDescending { it.priority.sortRank() }
        .thenBy { it.id }

private fun dueDateTodoComparator(): Comparator<TodoItemUiModel> =
    compareBy<TodoItemUiModel> { it.isDone }
        .thenBy { it.dueDate == null }
        .thenBy { it.dueDate ?: LocalDate.MAX }
        .thenBy { it.dueTimeText == null }
        .thenBy { it.dueTimeText.orEmpty() }
        .thenByDescending { it.priority.sortRank() }
        .thenBy { it.id }

private fun priorityTodoComparator(): Comparator<TodoItemUiModel> =
    compareBy<TodoItemUiModel> { it.isDone }
        .thenByDescending { it.priority.sortRank() }
        .thenBy { it.dueDate == null }
        .thenBy { it.dueDate ?: LocalDate.MAX }
        .thenBy { it.dueTimeText == null }
        .thenBy { it.dueTimeText.orEmpty() }
        .thenBy { it.id }

private fun TodoPriority.sortRank(): Int = when (this) {
    TodoPriority.HIGH -> 3
    TodoPriority.MEDIUM -> 2
    TodoPriority.LOW -> 1
}

private fun TodoItem.toUiModel(): TodoItemUiModel =
    TodoItemUiModel(
        id = id,
        title = title,
        isDone = isDone,
        dueDate = dueDate,
        dueDateText = dueDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
        dueTimeText = dueTimeMinutes?.let(::minutesToDueTimeText),
        reminderAtEpochMillis = reminderAtEpochMillis,
        reminderDateTimeText = epochMillisToReminderDateTime(reminderAtEpochMillis),
        isReminderEnabled = isReminderEnabled,
        reminderLeadMinutes = reminderLeadMinutes,
        reminderRepeatType = reminderRepeatType,
        priority = priority
    )

private fun AssignedTodo.toUiModel(
    isOptimisticallyDone: Boolean,
    isOptimisticallyActive: Boolean
): TodoItemUiModel =
    reminderAtEpochMillis().let { reminderEpochMillis ->
        val reminderLeadMinutes = reminderLeadMinutes(reminderEpochMillis)
        TodoItemUiModel(
            id = stableAssignedRowId(id),
            title = title,
            isDone = !isOptimisticallyActive && (isDone || isOptimisticallyDone),
            dueDate = dueDate,
            dueDateText = dueDate?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            dueTimeText = dueTimeMinutes?.let(::minutesToDueTimeText),
            reminderAtEpochMillis = reminderEpochMillis,
            reminderDateTimeText = epochMillisToReminderDateTime(reminderEpochMillis),
            isReminderEnabled = reminder?.enabled == true,
            reminderLeadMinutes = reminderLeadMinutes,
            reminderRepeatType = com.neo.yourtodo.core.model.ReminderRepeatType.NONE,
            priority = priority,
            assignedTodoId = id,
            senderNickname = sender?.nickname
        )
    }

private fun AssignedTodo.reminderAtEpochMillis(): Long? =
    reminder
        ?.takeIf { it.enabled }
        ?.reminderAt
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

private fun AssignedTodo.reminderLeadMinutes(reminderEpochMillis: Long?): Int? {
    val dueDate = dueDate ?: return null
    val dueTimeMinutes = dueTimeMinutes ?: return null
    val reminderMillis = reminderEpochMillis ?: return null
    val dueMillis = dueDate
        .atTime(LocalTime.of(dueTimeMinutes / 60, dueTimeMinutes % 60))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val leadMinutes = ((dueMillis - reminderMillis) / 60_000L).toInt()
    return leadMinutes.takeIf { it in setOf(0, 5, 10, 30, 60) }
}

private fun stableAssignedRowId(id: String): Long {
    val positiveHash = id.hashCode().toLong().let { if (it == Long.MIN_VALUE) 0 else kotlin.math.abs(it) }
    return -positiveHash - 1
}
