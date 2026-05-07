package com.neo.yourtodo.core.datastore.source

import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import kotlinx.coroutines.flow.Flow

interface UserPreferencesDataSource {
    val selectedTodoFilter: Flow<TodoFilter>
    val selectedTodoCategoryFilter: Flow<Long?>
    val selectedTodoPriorityFilter: Flow<TodoPriorityFilter>
    suspend fun setSelectedTodoFilter(filter: TodoFilter)
    suspend fun setSelectedTodoCategoryFilter(categoryId: Long?)
    suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter)
}
