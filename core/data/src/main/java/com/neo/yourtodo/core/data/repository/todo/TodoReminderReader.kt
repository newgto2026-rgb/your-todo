package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.data.mapper.toDomain
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.model.TodoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TodoReminderReader @Inject constructor(
    private val todoDao: TodoDao
) {
    suspend fun getTodosWithActiveReminder(): List<TodoItem> =
        todoDao.getTodosWithActiveReminder().map { it.toDomain() }
}
