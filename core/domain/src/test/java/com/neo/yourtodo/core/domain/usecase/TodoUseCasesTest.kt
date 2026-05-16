package com.neo.yourtodo.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.error.AppError
import com.neo.yourtodo.core.domain.error.appErrorOrNull
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoSortOption
import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class TodoUseCasesTest {

    @Test
    fun `add use case returns failure when title is longer than server limit`() = runTest {
        val repository = FakeTodoRepository()
        val useCase = AddTodoUseCase(repository)

        val result = useCase("a".repeat(201), null, null)

        assertThat(result.isFailure).isTrue()
        assertThat(result.appErrorOrNull()).isEqualTo(AppError.ValidationError("Title must be 200 characters or less"))
    }

    @Test
    fun `update use case returns failure on blank title`() = runTest {
        val repository = FakeTodoRepository()
        val useCase = UpdateTodoUseCase(repository)

        val result = useCase(1L, "   ", null, null)

        assertThat(result.isFailure).isTrue()
        assertThat(result.appErrorOrNull()).isEqualTo(AppError.ValidationError("Title must not be blank"))
    }

    @Test
    fun `update use case returns failure when title is longer than server limit`() = runTest {
        val repository = FakeTodoRepository()
        val id = repository.addTodo("before", null, null).getOrNull()!!
        val useCase = UpdateTodoUseCase(repository)

        val result = useCase(id, "a".repeat(201), null, null)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `update use case trims title before update`() = runTest {
        val repository = FakeTodoRepository()
        val addResult = repository.addTodo("before", null, null)
        val id = addResult.getOrNull()!!
        val useCase = UpdateTodoUseCase(repository)

        val result = useCase(id, "  after  ", LocalDate.of(2026, 4, 10), null)

        assertThat(result.isSuccess).isTrue()
        val updated = repository.getTodo(id)
        assertThat(updated?.title).isEqualTo("after")
        assertThat(updated?.dueDate).isEqualTo(LocalDate.of(2026, 4, 10))
    }

    @Test
    fun `delete use case removes todo`() = runTest {
        val repository = FakeTodoRepository()
        val id = repository.addTodo("todo", null, null).getOrNull()!!
        val useCase = DeleteTodoUseCase(repository)

        val result = useCase(id)

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.getTodo(id)).isNull()
    }

    @Test
    fun `toggle use case changes done state`() = runTest {
        val repository = FakeTodoRepository()
        val id = repository.addTodo("todo", null, null).getOrNull()!!
        val useCase = ToggleTodoDoneUseCase(repository)

        val result = useCase(id)

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.getTodo(id)?.isDone).isTrue()
    }

    @Test
    fun `observe todos use case emits repository values`() = runTest {
        val repository = FakeTodoRepository()
        val observeTodosUseCase = ObserveTodosUseCase(repository)

        observeTodosUseCase().test {
            assertThat(awaitItem()).isEmpty()

            repository.addTodo("todo", null, null)
            val updated = awaitItem()
            assertThat(updated).hasSize(1)
            assertThat(updated.first().title).isEqualTo("todo")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe selected filter and update selected filter work together`() = runTest {
        val repository = FakeTodoRepository()
        val observeFilterUseCase = ObserveSelectedTodoFilterUseCase(repository)
        val updateFilterUseCase = UpdateSelectedTodoFilterUseCase(repository)

        observeFilterUseCase().test {
            assertThat(awaitItem()).isEqualTo(TodoFilter.ALL)

            val result = updateFilterUseCase(TodoFilter.COMPLETED)
            assertThat(result.isSuccess).isTrue()
            assertThat(awaitItem()).isEqualTo(TodoFilter.COMPLETED)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe selected sort option and update selected sort option work together`() = runTest {
        val repository = FakeTodoRepository()
        val observeSortOptionUseCase = ObserveSelectedTodoSortOptionUseCase(repository)
        val updateSortOptionUseCase = UpdateSelectedTodoSortOptionUseCase(repository)

        observeSortOptionUseCase().test {
            assertThat(awaitItem()).isEqualTo(TodoSortOption.DEFAULT)

            val result = updateSortOptionUseCase(TodoSortOption.DUE_DATE)
            assertThat(result.isSuccess).isTrue()
            assertThat(awaitItem()).isEqualTo(TodoSortOption.DUE_DATE)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sync use case updates calendar widgets after successful sync`() = runTest {
        val repository = FakeTodoRepository()
        val calendarWidgetUpdater = RecordingCalendarWidgetUpdater()
        val useCase = SyncTodosUseCase(repository, calendarWidgetUpdater)

        val result = useCase()

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.syncCount).isEqualTo(1)
        assertThat(calendarWidgetUpdater.updateCount).isEqualTo(1)
    }

    @Test
    fun `sync use case keeps sync result successful when calendar widget update fails`() = runTest {
        val repository = FakeTodoRepository()
        val useCase = SyncTodosUseCase(repository, ThrowingCalendarWidgetUpdater)

        val result = useCase()

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.syncCount).isEqualTo(1)
    }

    private class RecordingCalendarWidgetUpdater : CalendarWidgetUpdater {
        var updateCount: Int = 0
            private set

        override suspend fun updateCalendarWidgets(): Result<Unit> {
            updateCount += 1
            return Result.success(Unit)
        }
    }

    private object ThrowingCalendarWidgetUpdater : CalendarWidgetUpdater {
        override suspend fun updateCalendarWidgets(): Result<Unit> = error("Widget update failed")
    }
}
