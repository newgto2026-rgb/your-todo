package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.data.mapper.toDomain
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.model.TodoItem

internal class TodoReminderReader(
    private val todoDao: TodoDao
) {
    suspend fun getTodosWithActiveReminder(): List<TodoItem> =
        todoDao.getTodosWithActiveReminder().map { it.toDomain() }
}
