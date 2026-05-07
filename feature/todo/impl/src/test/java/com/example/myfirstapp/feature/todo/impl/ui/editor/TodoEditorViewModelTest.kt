package com.example.myfirstapp.feature.todo.impl.ui.editor

import com.example.myfirstapp.core.domain.scheduler.TodoReminderScheduler
import com.example.myfirstapp.core.domain.scheduler.CalendarWidgetUpdater
import com.example.myfirstapp.core.domain.usecase.AddTodoUseCase
import com.example.myfirstapp.core.domain.usecase.DeleteTodoUseCase
import com.example.myfirstapp.core.domain.usecase.GetTodoUseCase
import com.example.myfirstapp.core.domain.usecase.UpdateTodoUseCase
import com.example.myfirstapp.core.model.ReminderRepeatType
import com.example.myfirstapp.core.model.TodoItem
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.core.testing.repository.FakeTodoRepository
import com.example.myfirstapp.core.testing.rule.MainDispatcherRule
import com.example.myfirstapp.feature.todo.impl.R
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeTodoRepository
    private lateinit var reminderScheduler: RecordingReminderScheduler
    private lateinit var calendarWidgetUpdater: RecordingCalendarWidgetUpdater
    private lateinit var viewModel: TodoEditorViewModel

    @Before
    fun setUp() {
        repository = FakeTodoRepository()
        reminderScheduler = RecordingReminderScheduler()
        calendarWidgetUpdater = RecordingCalendarWidgetUpdater()
        viewModel = TodoEditorViewModel(
            addTodoUseCase = AddTodoUseCase(repository),
            updateTodoUseCase = UpdateTodoUseCase(repository),
            deleteTodoUseCase = DeleteTodoUseCase(repository),
            getTodoUseCase = GetTodoUseCase(repository),
            todoReminderScheduler = reminderScheduler,
            calendarWidgetUpdater = calendarWidgetUpdater
        )
    }

    @Test
    fun initializeForCreateSetsInitialDate() {
        viewModel.initialize(todoId = null, dueDate = "2026-05-05")

        val state = viewModel.uiState.value
        assertThat(state.isInitialized).isTrue()
        assertThat(state.dueDateInput).isEqualTo("2026-05-05")
    }

    @Test
    fun initializeForEditLoadsTodoState() = runTest {
        val id = repository.addTodo(
            title = "Read doc",
            dueDate = LocalDate.of(2026, 5, 6),
            categoryId = null,
            dueTimeMinutes = 9 * 60,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = null,
            priority = TodoPriority.HIGH
        ).getOrThrow()

        viewModel.initialize(todoId = id, dueDate = null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isInitialized).isTrue()
        assertThat(state.editingTodoId).isEqualTo(id)
        assertThat(state.title).isEqualTo("Read doc")
        assertThat(state.dueDateInput).isEqualTo("2026-05-06")
        assertThat(state.dueTimeInput).isEqualTo("09:00")
        assertThat(state.priority).isEqualTo(TodoPriority.HIGH)
    }

    @Test
    fun initializeWithMissingTodoEmitsExit() = runTest {
        val exitDeferred = CompletableDeferred<TodoEditorSideEffect>()
        backgroundScope.launch {
            exitDeferred.complete(viewModel.sideEffect.first())
        }

        viewModel.initialize(todoId = 999L, dueDate = null)
        advanceUntilIdle()

        assertThat(exitDeferred.await()).isEqualTo(TodoEditorSideEffect.Exit)
    }

    @Test
    fun saveWithBlankTitleSetsValidationError() = runTest {
        viewModel.initialize(todoId = null, dueDate = null)
        viewModel.onTitleChange("   ")

        viewModel.onSave()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessageRes).isEqualTo(R.string.todo_error_title_required)
    }

    @Test
    fun saveWithInvalidDateSetsValidationError() = runTest {
        viewModel.initialize(todoId = null, dueDate = null)
        viewModel.onTitleChange("Task")
        viewModel.onDateInputChange("2026-99-99")

        viewModel.onSave()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessageRes).isEqualTo(R.string.todo_error_due_date_format)
    }

    @Test
    fun saveWithReminderWithoutTimeSetsValidationError() = runTest {
        viewModel.initialize(todoId = null, dueDate = null)
        viewModel.onTitleChange("Task")
        viewModel.onDateInputChange("2026-05-07")
        viewModel.onReminderEnabledChange(true)

        viewModel.onSave()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessageRes).isEqualTo(R.string.todo_error_reminder_due_time_required)
    }

    @Test
    fun saveCreatesTodoAndEmitsExit() = runTest {
        val exitDeferred = CompletableDeferred<TodoEditorSideEffect>()
        backgroundScope.launch {
            exitDeferred.complete(viewModel.sideEffect.first())
        }

        viewModel.initialize(todoId = null, dueDate = "2026-05-09")
        viewModel.onTitleChange("Write tests")
        viewModel.onDueTimeInputChange("10:30")
        viewModel.onPrioritySelected(TodoPriority.HIGH)
        viewModel.onSave()
        advanceUntilIdle()

        val todos = repository.observeTodos().first()
        assertThat(todos).hasSize(1)
        assertThat(todos.first().title).isEqualTo("Write tests")
        assertThat(todos.first().priority).isEqualTo(TodoPriority.HIGH)
        assertThat(exitDeferred.await()).isEqualTo(TodoEditorSideEffect.Exit)
        assertThat(reminderScheduler.cancelledTodoIds).contains(todos.first().id)
        assertThat(calendarWidgetUpdater.updateCount).isEqualTo(1)
    }

    @Test
    fun saveEditUpdatesTodoAndSchedulesReminder() = runTest {
        val id = repository.addTodo(
            title = "Old",
            dueDate = LocalDate.of(2026, 5, 9),
            categoryId = null,
            dueTimeMinutes = 8 * 60,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = null,
            priority = TodoPriority.LOW
        ).getOrThrow()

        viewModel.initialize(todoId = id, dueDate = null)
        advanceUntilIdle()
        viewModel.onTitleChange("Updated")
        viewModel.onDueTimeInputChange("09:30")
        viewModel.onReminderEnabledChange(true)
        viewModel.onReminderLeadMinutesChange(10)
        viewModel.onSave()
        advanceUntilIdle()

        val updated = repository.getTodo(id)
        assertThat(updated?.title).isEqualTo("Updated")
        assertThat(updated?.isReminderEnabled).isTrue()
        assertThat(updated?.reminderAtEpochMillis).isNotNull()
        assertThat(reminderScheduler.scheduledTodos.map(TodoItem::id)).contains(id)
        assertThat(calendarWidgetUpdater.updateCount).isEqualTo(1)
    }

    @Test
    fun deleteRemovesTodoAndEmitsExit() = runTest {
        val id = repository.addTodo(
            title = "Delete me",
            dueDate = null,
            categoryId = null,
            dueTimeMinutes = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = null,
            priority = TodoPriority.MEDIUM
        ).getOrThrow()
        val exitDeferred = CompletableDeferred<TodoEditorSideEffect>()
        backgroundScope.launch {
            exitDeferred.complete(viewModel.sideEffect.first())
        }

        viewModel.initialize(todoId = id, dueDate = null)
        advanceUntilIdle()
        viewModel.onDelete()
        advanceUntilIdle()

        assertThat(repository.getTodo(id)).isNull()
        assertThat(reminderScheduler.cancelledTodoIds).contains(id)
        assertThat(exitDeferred.await()).isEqualTo(TodoEditorSideEffect.Exit)
        assertThat(calendarWidgetUpdater.updateCount).isEqualTo(1)
    }

    private class RecordingReminderScheduler : TodoReminderScheduler {
        val scheduledTodos = mutableListOf<TodoItem>()
        val cancelledTodoIds = mutableListOf<Long>()

        override suspend fun schedule(todo: TodoItem) {
            scheduledTodos += todo
        }

        override suspend fun cancel(todoId: Long) {
            cancelledTodoIds += todoId
        }

        override suspend fun rescheduleAll() = Unit
    }

    private class RecordingCalendarWidgetUpdater : CalendarWidgetUpdater {
        var updateCount: Int = 0
            private set

        override suspend fun updateCalendarWidgets(): Result<Unit> {
            updateCount += 1
            return Result.success(Unit)
        }
    }
}
