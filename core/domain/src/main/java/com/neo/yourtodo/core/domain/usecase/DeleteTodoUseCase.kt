package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import javax.inject.Inject

class DeleteTodoUseCase @Inject constructor(
    private val repository: TodoItemRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> = repository.deleteTodo(id)
}
