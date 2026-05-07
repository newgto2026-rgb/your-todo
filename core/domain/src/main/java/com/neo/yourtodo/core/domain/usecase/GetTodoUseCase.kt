package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.model.TodoItem
import javax.inject.Inject

class GetTodoUseCase @Inject constructor(
    private val repository: TodoItemRepository
) {
    suspend operator fun invoke(id: Long): TodoItem? = repository.getTodo(id)
}
