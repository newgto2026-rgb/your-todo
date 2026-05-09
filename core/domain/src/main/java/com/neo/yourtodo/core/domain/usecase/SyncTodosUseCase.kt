package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import javax.inject.Inject

class SyncTodosUseCase @Inject constructor(
    private val repository: TodoItemRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.syncTodos()
}
