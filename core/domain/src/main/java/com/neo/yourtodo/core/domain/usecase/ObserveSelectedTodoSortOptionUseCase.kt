package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import com.neo.yourtodo.core.model.TodoSortOption
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSelectedTodoSortOptionUseCase @Inject constructor(
    private val repository: TodoFilterRepository
) {
    operator fun invoke(): Flow<TodoSortOption> = repository.observeSelectedSortOption()
}
