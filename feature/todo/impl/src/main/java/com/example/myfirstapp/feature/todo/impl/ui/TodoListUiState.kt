package com.example.myfirstapp.feature.todo.impl.ui

import androidx.compose.runtime.Immutable
import androidx.annotation.StringRes
import com.example.myfirstapp.core.model.ReminderRepeatType
import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.model.TodoItem
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.core.model.TodoPriorityFilter
import com.example.myfirstapp.feature.todo.impl.model.TodoEditModel
import com.example.myfirstapp.feature.todo.impl.model.TodoItemUiModel

@Immutable
data class TodoListUiState(
    val items: List<TodoItemUiModel> = emptyList(),
    val selectedFilter: TodoFilter = TodoFilter.ALL,
    val selectedPriorityFilter: TodoPriorityFilter = TodoPriorityFilter.ALL,
    val isLoading: Boolean = false,
    val isEditDialogVisible: Boolean = false,
    val editingItem: TodoEditModel? = null,
    val draftPriority: TodoPriority = TodoPriority.MEDIUM,
    val draftTitle: String = "",
    val draftDueDateInput: String = "",
    val draftDueTimeInput: String = "",
    val draftReminderEnabled: Boolean = false,
    val draftReminderLeadMinutes: Int? = null,
    val draftReminderRepeatType: ReminderRepeatType = ReminderRepeatType.NONE,
    val pendingUndoTodo: TodoItem? = null,
    @StringRes val errorMessageRes: Int? = null
)
