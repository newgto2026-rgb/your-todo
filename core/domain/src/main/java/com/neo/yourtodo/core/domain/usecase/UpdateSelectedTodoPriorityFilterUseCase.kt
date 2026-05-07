package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import com.neo.yourtodo.core.model.TodoPriorityFilter
import javax.inject.Inject

class UpdateSelectedTodoPriorityFilterUseCase @Inject constructor(
    private val repository: TodoFilterRepository
) {
    suspend operator fun invoke(filter: TodoPriorityFilter): Result<Unit> =
        repository.setSelectedPriorityFilter(filter)
}
