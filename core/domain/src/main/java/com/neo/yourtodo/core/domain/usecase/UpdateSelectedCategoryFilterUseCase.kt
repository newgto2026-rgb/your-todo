package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import javax.inject.Inject

class UpdateSelectedCategoryFilterUseCase @Inject constructor(
    private val repository: TodoFilterRepository
) {
    suspend operator fun invoke(categoryId: Long?): Result<Unit> =
        repository.setSelectedCategoryFilter(categoryId)
}
