package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.model.TodoCategoryFilter
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSortOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
internal class TodoFilterPreferences @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val categoryStore: TodoCategoryStore
) {
    fun observeSelectedFilter(): Flow<TodoFilter> =
        userPreferencesDataSource.selectedTodoFilter

    suspend fun setSelectedFilter(filter: TodoFilter) {
        userPreferencesDataSource.setSelectedTodoFilter(filter)
    }

    fun observeSelectedCategoryFilter(): Flow<Long?> =
        userPreferencesDataSource.selectedTodoCategoryFilter

    suspend fun setSelectedCategoryFilter(categoryId: Long?) {
        val isUncategorized = categoryId == TodoCategoryFilter.UNCATEGORIZED_FILTER_ID
        if (categoryId != null && !isUncategorized) {
            categoryStore.requireCategoryExists(categoryId)
        }
        userPreferencesDataSource.setSelectedTodoCategoryFilter(categoryId)
    }

    fun observeSelectedPriorityFilter(): Flow<TodoPriorityFilter> =
        userPreferencesDataSource.selectedTodoPriorityFilter

    suspend fun setSelectedPriorityFilter(filter: TodoPriorityFilter) {
        userPreferencesDataSource.setSelectedTodoPriorityFilter(filter)
    }

    fun observeSelectedSortOption(): Flow<TodoSortOption> =
        userPreferencesDataSource.selectedTodoSortOption

    suspend fun setSelectedSortOption(option: TodoSortOption) {
        userPreferencesDataSource.setSelectedTodoSortOption(option)
    }
}
