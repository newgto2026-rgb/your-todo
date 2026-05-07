package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoReminderRepository
import com.neo.yourtodo.core.model.TodoItem
import javax.inject.Inject

class GetActiveTodoRemindersUseCase @Inject constructor(
    private val repository: TodoReminderRepository
) {
    suspend operator fun invoke(): List<TodoItem> = repository.getTodosWithActiveReminder()
}
