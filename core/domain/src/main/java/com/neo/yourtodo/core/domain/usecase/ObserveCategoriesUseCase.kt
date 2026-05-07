package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoCategoryRepository
import com.neo.yourtodo.core.model.Category
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCategoriesUseCase @Inject constructor(
    private val repository: TodoCategoryRepository
) {
    operator fun invoke(): Flow<List<Category>> = repository.observeCategories()
}
