package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.model.TodoItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTodosUseCase @Inject constructor(
    private val repository: TodoItemRepository
) {
    operator fun invoke(): Flow<List<TodoItem>> = repository.observeTodos()
}
