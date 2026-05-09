package com.neo.yourtodo.feature.todo.impl.model

import androidx.compose.runtime.Immutable
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoPriority
import java.time.LocalDate

@Immutable
data class TodoItemUiModel(
    val id: Long,
    val title: String,
    val isDone: Boolean,
    val dueDate: LocalDate?,
    val dueDateText: String?,
    val dueTimeText: String?,
    val reminderAtEpochMillis: Long?,
    val reminderDateTimeText: String?,
    val isReminderEnabled: Boolean,
    val reminderLeadMinutes: Int?,
    val reminderRepeatType: ReminderRepeatType,
    val priority: TodoPriority,
    val assignedTodoId: String? = null,
    val senderNickname: String? = null
) {
    val isAssigned: Boolean
        get() = assignedTodoId != null
}
