package com.example.myfirstapp.feature.todo.impl.ui

import com.example.myfirstapp.core.model.ReminderRepeatType
import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.core.model.TodoPriorityFilter
import java.time.LocalDate

sealed interface TodoListAction {
    data object OnAddClick : TodoListAction
    data class OnAddForDateClick(val dueDate: LocalDate) : TodoListAction
    data object OnQuickAddClick : TodoListAction
    data class OnQuickAddTitleChange(val value: String) : TodoListAction
    data object OnQuickAddSubmit : TodoListAction
    data object OnQuickAddDismiss : TodoListAction
    data class OnTitleChange(val value: String) : TodoListAction
    data class OnDueDateInputChange(val value: String) : TodoListAction
    data class OnDueTimeInputChange(val value: String) : TodoListAction
    data class OnReminderEnabledChange(val value: Boolean) : TodoListAction
    data class OnReminderLeadMinutesChange(val value: Int) : TodoListAction
    data class OnReminderRepeatTypeChange(val value: ReminderRepeatType) : TodoListAction
    data class OnPrioritySelectedInEditor(val priority: TodoPriority) : TodoListAction
    data object OnSaveClick : TodoListAction
    data class OnToggleDone(val id: Long) : TodoListAction
    data class OnMoveToTomorrow(val id: Long) : TodoListAction
    data class OnClearSchedule(val id: Long) : TodoListAction
    data object OnUndoLastQuickAction : TodoListAction
    data class OnEditClick(val id: Long) : TodoListAction
    data class OnDeleteRequest(val id: Long) : TodoListAction
    data object OnDeleteCancel : TodoListAction
    data object OnDeleteConfirm : TodoListAction
    data object OnClearCompletedClick : TodoListAction
    data class OnFilterChange(val filter: TodoFilter) : TodoListAction
    data class OnPriorityFilterChange(val filter: TodoPriorityFilter) : TodoListAction
    data class OnSortOptionChange(val option: TodoSortOption) : TodoListAction
    data object OnDismissDialog : TodoListAction
}
