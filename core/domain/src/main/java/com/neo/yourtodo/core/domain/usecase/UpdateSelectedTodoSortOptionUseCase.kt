package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import com.neo.yourtodo.core.model.TodoSortOption
import javax.inject.Inject

class UpdateSelectedTodoSortOptionUseCase @Inject constructor(
    private val repository: TodoFilterRepository
) {
    suspend operator fun invoke(option: TodoSortOption): Result<Unit> =
        repository.setSelectedSortOption(option)
}
