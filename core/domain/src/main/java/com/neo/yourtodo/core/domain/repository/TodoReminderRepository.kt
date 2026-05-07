package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.TodoItem

interface TodoReminderRepository {
    suspend fun getTodosWithActiveReminder(): List<TodoItem>
}
