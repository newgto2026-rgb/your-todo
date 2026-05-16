package com.neo.yourtodo.feature.todo.impl.ui

import com.neo.yourtodo.core.domain.usecase.AssignedTaskSurfaceOverrides
import com.neo.yourtodo.core.domain.usecase.BuildTaskSurfaceListUseCase
import com.neo.yourtodo.core.domain.usecase.TaskSurfaceItem
import com.neo.yourtodo.core.domain.usecase.TaskSurfaceSection
import com.neo.yourtodo.core.domain.usecase.TaskSurfaceSectionKey
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel
import java.time.format.DateTimeFormatter

internal fun buildTodoListUiState(
    localState: TodoListUiState,
    items: List<TodoItem>,
    assignedItems: List<AssignedTodo>,
    selectedFilter: TodoFilter,
    selectedPriorityFilter: TodoPriorityFilter,
    profileInitial: String?,
    buildTaskSurfaceListUseCase: BuildTaskSurfaceListUseCase
): TodoListUiState {
    val taskSurface = buildTaskSurfaceListUseCase(
        localTodos = items,
        assignedTodos = assignedItems,
        selectedFilter = selectedFilter,
        selectedPriorityFilter = selectedPriorityFilter,
        selectedSortOption = localState.selectedSortOption,
        assignedOverrides = AssignedTaskSurfaceOverrides(
            completedIds = localState.optimisticCompletedAssignedTodoIds,
            activeIds = localState.optimisticActiveAssignedTodoIds,
            hiddenIds = localState.optimisticDeletedAssignedTodoIds
        )
    )

    return localState.copy(
        profileInitial = profileInitial,
        items = taskSurface.items.map { it.toUiModel() },
        sections = taskSurface.sections.map { it.toUiSection() },
        completedTodoIds = taskSurface.completedLocalTodoIds,
        completedAssignedTodoIds = taskSurface.completedAssignedTodoIds,
        selectedFilter = selectedFilter,
        selectedPriorityFilter = selectedPriorityFilter,
        isLoading = false
    )
}

private fun TaskSurfaceSection.toUiSection(): TodoListSection =
    TodoListSection(
        key = key.toUiSectionKey(),
        items = items.map { it.toUiModel() }
    )

private fun TaskSurfaceSectionKey.toUiSectionKey(): TodoListSectionKey =
    when (this) {
        TaskSurfaceSectionKey.Open -> TodoListSectionKey.Open
        TaskSurfaceSectionKey.Completed -> TodoListSectionKey.Completed
        is TaskSurfaceSectionKey.Priority -> TodoListSectionKey.Priority(priority)
        is TaskSurfaceSectionKey.DueDate -> TodoListSectionKey.DueDate(date)
        is TaskSurfaceSectionKey.Friend -> TodoListSectionKey.Friend(nickname)
        TaskSurfaceSectionKey.Self -> TodoListSectionKey.Self
    }

private fun TaskSurfaceItem.toUiModel(): TodoItemUiModel =
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
        priority = priority,
        assignedTodoId = assignedTodoId,
        senderNickname = senderNickname,
        assignmentMode = assignmentMode
    )
