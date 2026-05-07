package com.neo.yourtodo.feature.todo.impl.model

import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoPriority
import java.time.LocalDate

data class TodoEditModel(
    val id: Long?,
    val title: String,
    val dueDate: LocalDate?,
    val dueTimeMinutes: Int?,
    val priority: TodoPriority,
    val reminderAtEpochMillis: Long?,
    val isReminderEnabled: Boolean,
    val reminderRepeatType: ReminderRepeatType,
    val reminderLeadMinutes: Int?
)
