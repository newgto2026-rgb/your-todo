package com.example.myfirstapp.feature.todo.impl.ui

import com.example.myfirstapp.core.model.ReminderRepeatType
import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.core.model.TodoPriorityFilter
import java.time.LocalDate

sealed interface TodoListAction {
    data object OnAddClick : TodoListAction
    data class OnAddForDateClick(val dueDate: LocalDate) : TodoListAction
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
    data class OnDeleteClick(val id: Long) : TodoListAction
    data class OnFilterChange(val filter: TodoFilter) : TodoListAction
    data class OnPriorityFilterChange(val filter: TodoPriorityFilter) : TodoListAction
    data object OnDismissDialog : TodoListAction
}
