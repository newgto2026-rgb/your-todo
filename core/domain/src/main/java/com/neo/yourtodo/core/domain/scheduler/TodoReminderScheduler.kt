package com.neo.yourtodo.core.domain.scheduler

import com.neo.yourtodo.core.model.TodoItem

interface TodoReminderScheduler {
    suspend fun schedule(todo: TodoItem)
    suspend fun cancel(todoId: Long)
    suspend fun rescheduleAll()
}
