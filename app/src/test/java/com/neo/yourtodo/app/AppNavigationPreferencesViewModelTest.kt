package com.neo.yourtodo.app

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import com.neo.yourtodo.core.domain.usecase.ObserveSelectedTodoFilterUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateSelectedTodoFilterUseCase
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSortOption
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigationPreferencesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rememberSelectedTodoFilter_cancelsPendingWriteAndKeepsLatestSelection() = runTest {
        val repository = RecordingTodoFilterRepository()
        val viewModel = AppNavigationPreferencesViewModel(
            observeSelectedTodoFilterUseCase = ObserveSelectedTodoFilterUseCase(repository),
            updateSelectedTodoFilterUseCase = UpdateSelectedTodoFilterUseCase(repository)
        )

        viewModel.rememberSelectedTodoFilter(TodoFilter.TODAY)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        viewModel.rememberSelectedTodoFilter(TodoFilter.COMPLETED)
        advanceUntilIdle()

        assertThat(repository.selectedFilter).isEqualTo(TodoFilter.COMPLETED)

        repository.releaseDelayedTodayWrite()
        advanceUntilIdle()

        assertThat(repository.selectedFilter).isEqualTo(TodoFilter.COMPLETED)
        assertThat(repository.writeRequests).containsExactly(
            TodoFilter.TODAY,
            TodoFilter.COMPLETED
        ).inOrder()
    }

    private class RecordingTodoFilterRepository : TodoFilterRepository {
        private val selectedFilterFlow = MutableStateFlow(TodoFilter.ALL)
        private val selectedPriorityFilterFlow = MutableStateFlow(TodoPriorityFilter.ALL)
        private val selectedSortOptionFlow = MutableStateFlow(TodoSortOption.DEFAULT)
        private val delayedTodayWrite = CompletableDeferred<Unit>()

        val selectedFilter: TodoFilter
            get() = selectedFilterFlow.value
        val writeRequests = mutableListOf<TodoFilter>()

        override fun observeSelectedFilter(): Flow<TodoFilter> =
            selectedFilterFlow.asStateFlow()

        override suspend fun setSelectedFilter(filter: TodoFilter): Result<Unit> {
            writeRequests += filter
            if (filter == TodoFilter.TODAY) {
                delayedTodayWrite.await()
            }
            selectedFilterFlow.value = filter
            return Result.success(Unit)
        }

        fun releaseDelayedTodayWrite() {
            delayedTodayWrite.complete(Unit)
        }

        override fun observeSelectedCategoryFilter(): Flow<Long?> =
            MutableStateFlow(null).asStateFlow()

        override suspend fun setSelectedCategoryFilter(categoryId: Long?): Result<Unit> =
            Result.success(Unit)

        override fun observeSelectedPriorityFilter(): Flow<TodoPriorityFilter> =
            selectedPriorityFilterFlow.asStateFlow()

        override suspend fun setSelectedPriorityFilter(filter: TodoPriorityFilter): Result<Unit> {
            selectedPriorityFilterFlow.value = filter
            return Result.success(Unit)
        }

        override fun observeSelectedSortOption(): Flow<TodoSortOption> =
            selectedSortOptionFlow.asStateFlow()

        override suspend fun setSelectedSortOption(option: TodoSortOption): Result<Unit> {
            selectedSortOptionFlow.value = option
            return Result.success(Unit)
        }
    }
}
