package com.neo.yourtodo.di

import com.neo.yourtodo.core.datastore.di.DataStoreModule
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSortOption
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataStoreModule::class]
)
object TestDataStoreModule {

    @Provides
    @Singleton
    fun provideUserPreferencesDataSource(): UserPreferencesDataSource = InMemoryUserPreferencesDataSource()
}

private class InMemoryUserPreferencesDataSource : UserPreferencesDataSource {
    private val authSessionFlow = MutableStateFlow<AuthSessionData?>(testAuthSession())
    private val selectedFilter = MutableStateFlow(TodoFilter.ALL)
    private val selectedCategoryFilter = MutableStateFlow<Long?>(null)
    private val selectedPriorityFilter = MutableStateFlow(TodoPriorityFilter.ALL)
    private val selectedSortOption = MutableStateFlow(TodoSortOption.DEFAULT)
    private val todoSyncCursorFlow = MutableStateFlow<String?>(null)
    private val todoSyncHaltReasonFlow = MutableStateFlow<String?>(null)

    override val authSession: Flow<AuthSessionData?> = authSessionFlow
    override val selectedTodoFilter: Flow<TodoFilter> = selectedFilter
    override val selectedTodoCategoryFilter: Flow<Long?> = selectedCategoryFilter
    override val selectedTodoPriorityFilter: Flow<TodoPriorityFilter> = selectedPriorityFilter
    override val selectedTodoSortOption: Flow<TodoSortOption> = selectedSortOption
    override val todoSyncCursor: Flow<String?> = todoSyncCursorFlow
    override val todoSyncHaltReason: Flow<String?> = todoSyncHaltReasonFlow

    override suspend fun saveAuthSession(session: AuthSessionData) {
        authSessionFlow.value = session
    }

    override suspend fun clearAuthSession() {
        // Keep UI tests past AuthGate even when background sync uses placeholder test tokens.
        authSessionFlow.value = testAuthSession()
    }

    override suspend fun setSelectedTodoFilter(filter: TodoFilter) {
        selectedFilter.value = filter
    }

    override suspend fun setSelectedTodoCategoryFilter(categoryId: Long?) {
        selectedCategoryFilter.value = categoryId
    }

    override suspend fun setSelectedTodoPriorityFilter(filter: TodoPriorityFilter) {
        selectedPriorityFilter.value = filter
    }

    override suspend fun setSelectedTodoSortOption(option: TodoSortOption) {
        selectedSortOption.value = option
    }

    override suspend fun setTodoSyncCursor(cursor: String?) {
        todoSyncCursorFlow.value = cursor
    }

    override suspend fun setTodoSyncHaltReason(reason: String?) {
        todoSyncHaltReasonFlow.value = reason
    }

    override suspend fun clearTodoSyncState() {
        todoSyncCursorFlow.value = null
        todoSyncHaltReasonFlow.value = null
    }

    private fun testAuthSession() = AuthSessionData(
        accessToken = "android-test-access-token",
        refreshToken = "android-test-refresh-token",
        userId = "android-test-user",
        nickname = "tester",
        email = "tester@example.com",
        onboardingRequired = false
    )
}
