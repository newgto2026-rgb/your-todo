package com.neo.yourtodo.core.datastore.source

import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface UserPreferencesDataSource {
    val authSession: Flow<AuthSessionData?>
    val selectedTodoFilter: Flow<TodoFilter>
    val selectedTodoCategoryFilter: Flow<Long?>
    val selectedTodoPriorityFilter: Flow<TodoPriorityFilter>
    val selectedTodoSortOption: Flow<TodoSortOption> get() = flowOf(TodoSortOption.DEFAULT)
    val todoSyncCursor: Flow<String?>
    val todoSyncHaltReason: Flow<String?>
    val pushCurrentToken: Flow<String?> get() = flowOf(null)
    val pushRegisteredToken: Flow<String?> get() = flowOf(null)
    suspend fun saveAuthSession(session: AuthSessionData)
    suspend fun clearAuthSession()
    suspend fun setSelectedTodoFilter(filter: TodoFilter)
    suspend fun setSelectedTodoCategoryFilter(categoryId: Long?)
    suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter)
    suspend fun setSelectedTodoSortOption(option: TodoSortOption) = Unit
    suspend fun setTodoSyncCursor(cursor: String?)
    suspend fun setTodoSyncHaltReason(reason: String?)
    suspend fun clearTodoSyncState()
    suspend fun setPushCurrentToken(token: String?) = Unit
    suspend fun setPushRegisteredToken(token: String?) = Unit
}
