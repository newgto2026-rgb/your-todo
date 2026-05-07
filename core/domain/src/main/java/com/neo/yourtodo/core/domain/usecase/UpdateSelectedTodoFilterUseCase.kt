package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import com.neo.yourtodo.core.model.TodoFilter
import javax.inject.Inject

class UpdateSelectedTodoFilterUseCase @Inject constructor(
    private val repository: TodoFilterRepository
) {
    suspend operator fun invoke(filter: TodoFilter): Result<Unit> = repository.setSelectedFilter(filter)
}
