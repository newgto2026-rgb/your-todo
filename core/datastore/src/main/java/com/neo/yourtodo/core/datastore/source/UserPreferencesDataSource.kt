package com.neo.yourtodo.core.datastore.source

import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import kotlinx.coroutines.flow.Flow

interface UserPreferencesDataSource {
    val authSession: Flow<AuthSessionData?>
    val selectedTodoFilter: Flow<TodoFilter>
    val selectedTodoCategoryFilter: Flow<Long?>
    val selectedTodoPriorityFilter: Flow<TodoPriorityFilter>
    val todoSyncCursor: Flow<String?>
    val todoSyncHaltReason: Flow<String?>
    suspend fun saveAuthSession(session: AuthSessionData)
    suspend fun clearAuthSession()
    suspend fun setSelectedTodoFilter(filter: TodoFilter)
    suspend fun setSelectedTodoCategoryFilter(categoryId: Long?)
    suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter)
    suspend fun setTodoSyncCursor(cursor: String?)
    suspend fun setTodoSyncHaltReason(reason: String?)
    suspend fun clearTodoSyncState()
}
