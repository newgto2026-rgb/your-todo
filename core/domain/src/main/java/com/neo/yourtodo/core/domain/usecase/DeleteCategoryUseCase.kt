package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoCategoryRepository
import javax.inject.Inject

class DeleteCategoryUseCase @Inject constructor(
    private val repository: TodoCategoryRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> = repository.deleteCategory(id)
}
