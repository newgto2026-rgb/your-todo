package com.example.myfirstapp.feature.todo.impl.ui

import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.model.TodoItem
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.core.model.TodoPriorityFilter
import com.example.myfirstapp.feature.todo.impl.model.TodoItemUiModel
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
        .sortedWith(
            compareBy<TodoItem> { it.isDone }
                .thenByDescending { it.priority.sortRank() }
                .thenBy { it.id }
        )

    return localState.copy(
        items = filteredItems.map { it.toUiModel() },
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
                    it.dueDate?.isBefore(today) == true ||
                    it.priority == TodoPriority.HIGH
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
