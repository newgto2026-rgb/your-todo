package com.neo.yourtodo.core.data.mapper

import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoItem
import java.time.LocalDate

fun TodoEntity.toDomain(): TodoItem =
    TodoItem(
        id = id,
        title = title,
        isDone = isDone,
        dueDate = dueDateEpochDay?.let(LocalDate::ofEpochDay),
        dueTimeMinutes = dueTimeMinutes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        categoryId = categoryId,
        reminderAtEpochMillis = reminderAtEpochMillis,
        isReminderEnabled = isReminderEnabled,
        reminderRepeatType = ReminderRepeatType.valueOf(reminderRepeatType),
        reminderRepeatDaysMask = reminderRepeatDaysMask,
        reminderLeadMinutes = reminderLeadMinutes,
        priority = TodoPriority.entries.find { it.name == priority } ?: TodoPriority.MEDIUM
    )

fun TodoItem.toEntity(): TodoEntity =
    TodoEntity(
        id = id,
        title = title,
        isDone = isDone,
        dueDateEpochDay = dueDate?.toEpochDay(),
        dueTimeMinutes = dueTimeMinutes,
        reminderAtEpochMillis = reminderAtEpochMillis,
        isReminderEnabled = isReminderEnabled,
        reminderRepeatType = reminderRepeatType.name,
        reminderRepeatDaysMask = reminderRepeatDaysMask,
        reminderLeadMinutes = reminderLeadMinutes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        categoryId = categoryId,
        priority = priority.name
    )
