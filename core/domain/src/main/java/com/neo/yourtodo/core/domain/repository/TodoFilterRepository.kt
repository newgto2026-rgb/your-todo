package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSortOption
import kotlinx.coroutines.flow.Flow

interface TodoFilterRepository {
    fun observeSelectedFilter(): Flow<TodoFilter>
    suspend fun setSelectedFilter(filter: TodoFilter): Result<Unit>

    fun observeSelectedCategoryFilter(): Flow<Long?>
    suspend fun setSelectedCategoryFilter(categoryId: Long?): Result<Unit>

    fun observeSelectedPriorityFilter(): Flow<TodoPriorityFilter>
    suspend fun setSelectedPriorityFilter(filter: TodoPriorityFilter): Result<Unit>

    fun observeSelectedSortOption(): Flow<TodoSortOption>
    suspend fun setSelectedSortOption(option: TodoSortOption): Result<Unit>
}
