package com.example.myfirstapp.feature.todo.impl.model

import androidx.compose.runtime.Immutable
import com.example.myfirstapp.core.model.ReminderRepeatType
import com.example.myfirstapp.core.model.TodoPriority
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
    val priority: TodoPriority
)
