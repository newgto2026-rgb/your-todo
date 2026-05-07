package com.neo.yourtodo.feature.todo.impl.ui

import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun buildTodoListUiState(
    localState: TodoListUiState,
    items: List<TodoItem>,
    selectedFilter: TodoFilter,
    selectedPriorityFilter: TodoPriorityFilter
): TodoListUiState {
    val filteredItems = items
        .filterBy(selectedFilter)
        .filterByPriority(selectedPriorityFilter)
        .sortedWith(localState.selectedSortOption.comparatorFor(selectedFilter))

    return localState.copy(
        items = filteredItems.map { it.toUiModel() },
        completedTodoIds = items.filter { it.isDone }.map { it.id },
        selectedFilter = selectedFilter,
        selectedPriorityFilter = selectedPriorityFilter,
        isLoading = false
    )
}

private fun List<TodoItem>.filterBy(filter: TodoFilter): List<TodoItem> {
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

private fun List<TodoItem>.filterByPriority(filter: TodoPriorityFilter): List<TodoItem> = when (filter) {
    TodoPriorityFilter.ALL -> this
    TodoPriorityFilter.LOW -> filter { it.priority == TodoPriority.LOW }
    TodoPriorityFilter.MEDIUM -> filter { it.priority == TodoPriority.MEDIUM }
    TodoPriorityFilter.HIGH -> filter { it.priority == TodoPriority.HIGH }
}

private fun TodoSortOption.comparatorFor(filter: TodoFilter): Comparator<TodoItem> =
    if (filter == TodoFilter.ALL) {
        when (this) {
            TodoSortOption.DEFAULT -> defaultTodoComparator()
            TodoSortOption.DUE_DATE -> dueDateTodoComparator()
            TodoSortOption.PRIORITY -> priorityTodoComparator()
        }
    } else {
        contextualTodoComparator()
    }

private fun defaultTodoComparator(): Comparator<TodoItem> =
    compareBy<TodoItem> { it.isDone }
        .thenByDescending { it.createdAt }
        .thenByDescending { it.id }

private fun contextualTodoComparator(): Comparator<TodoItem> =
    compareBy<TodoItem> { it.isDone }
        .thenByDescending { it.priority.sortRank() }
        .thenBy { it.id }

private fun dueDateTodoComparator(): Comparator<TodoItem> =
    compareBy<TodoItem> { it.isDone }
        .thenBy { it.dueDate == null }
        .thenBy { it.dueDate ?: LocalDate.MAX }
        .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
        .thenByDescending { it.priority.sortRank() }
        .thenBy { it.id }

private fun priorityTodoComparator(): Comparator<TodoItem> =
    compareBy<TodoItem> { it.isDone }
        .thenByDescending { it.priority.sortRank() }
        .thenBy { it.dueDate == null }
        .thenBy { it.dueDate ?: LocalDate.MAX }
        .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
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
