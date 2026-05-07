package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import com.neo.yourtodo.core.model.TodoPriorityFilter
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSelectedTodoPriorityFilterUseCase @Inject constructor(
    private val repository: TodoFilterRepository
) {
    operator fun invoke(): Flow<TodoPriorityFilter> = repository.observeSelectedPriorityFilter()
}

