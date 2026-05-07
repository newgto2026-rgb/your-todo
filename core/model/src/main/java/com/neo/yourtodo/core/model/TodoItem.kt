package com.neo.yourtodo.core.model

import java.time.LocalDate

data class TodoItem(
    val id: Long,
    val title: String,
    val isDone: Boolean,
    val dueDate: LocalDate?,
    val createdAt: Long,
    val updatedAt: Long,
    val categoryId: Long?,
    val reminderAtEpochMillis: Long? = null,
    val isReminderEnabled: Boolean = false,
    val reminderRepeatType: ReminderRepeatType = ReminderRepeatType.NONE,
    val reminderRepeatDaysMask: Int = 0,
    val dueTimeMinutes: Int? = null,
    val reminderLeadMinutes: Int? = null,
    val priority: TodoPriority = TodoPriority.MEDIUM
)
