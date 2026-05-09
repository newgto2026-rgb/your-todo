package com.neo.yourtodo.feature.todo.impl.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.feature.todo.impl.model.TodoEditModel
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel

@Immutable
data class TodoListUiState(
    val profileInitial: String? = null,
    val items: List<TodoItemUiModel> = emptyList(),
    val completedTodoIds: List<Long> = emptyList(),
    val completedAssignedTodoIds: List<String> = emptyList(),
    val selectedFilter: TodoFilter = TodoFilter.ALL,
    val selectedPriorityFilter: TodoPriorityFilter = TodoPriorityFilter.ALL,
    val selectedSortOption: TodoSortOption = TodoSortOption.DEFAULT,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isEditDialogVisible: Boolean = false,
    val deleteConfirmation: TodoDeleteConfirmation? = null,
    val editingItem: TodoEditModel? = null,
    val editingAssignedTodoId: String? = null,
    val draftPriority: TodoPriority = TodoPriority.MEDIUM,
    val draftTitle: String = "",
    val draftDueDateInput: String = "",
    val draftDueTimeInput: String = "",
    val draftReminderEnabled: Boolean = false,
    val draftReminderLeadMinutes: Int? = null,
    val draftReminderRepeatType: ReminderRepeatType = ReminderRepeatType.NONE,
    val isQuickAddVisible: Boolean = false,
    val quickAddTitle: String = "",
    @StringRes val quickAddErrorMessageRes: Int? = null,
    val pendingUndoTodo: TodoItem? = null,
    val pendingAssignedDeleteId: String? = null,
    val optimisticCompletedAssignedTodoIds: Set<String> = emptySet(),
    @StringRes val errorMessageRes: Int? = null
) {
    val hasClearableCompletedItems: Boolean
        get() = completedTodoIds.isNotEmpty() || completedAssignedTodoIds.isNotEmpty()
}

@Immutable
sealed interface TodoDeleteConfirmation {
    val itemCount: Int

    data class Single(val id: Long) : TodoDeleteConfirmation {
        override val itemCount: Int = 1
    }

    data class Completed(
        val todoIds: List<Long>,
        val assignedTodoIds: List<String>
    ) : TodoDeleteConfirmation {
        override val itemCount: Int = todoIds.size + assignedTodoIds.size
    }
}

enum class TodoSortOption {
    DEFAULT,
    DUE_DATE,
    PRIORITY
}
