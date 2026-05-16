package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.STATUS_ACTIVE
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.STATUS_COMPLETED
import com.neo.yourtodo.core.data.sync.TodoSyncPayload
import com.neo.yourtodo.core.database.entity.TodoEntity
import java.time.LocalDate

internal object TodoSyncFieldPolicy {
    val syncedPayloadFields: List<String> = listOf(
        "title",
        "description",
        "dueDate",
        "status",
        "priority"
    )

    val localOnlyTodoFields: List<String> = listOf(
        "categoryId",
        "dueTimeMinutes",
        "reminderAtEpochMillis",
        "isReminderEnabled",
        "reminderRepeatType",
        "reminderRepeatDaysMask",
        "reminderLeadMinutes"
    )

    fun createPayload(todo: TodoEntity): TodoSyncPayload =
        TodoSyncPayload(
            title = todo.title,
            dueDate = todo.dueDateEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
            status = if (todo.isDone) STATUS_COMPLETED else STATUS_ACTIVE,
            priority = todo.priority
        )
}
