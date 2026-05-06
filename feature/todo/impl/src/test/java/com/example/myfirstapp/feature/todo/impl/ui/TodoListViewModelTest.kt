package com.example.myfirstapp.feature.todo.impl.ui

import com.example.myfirstapp.core.domain.scheduler.TodoReminderScheduler
import com.example.myfirstapp.core.domain.usecase.AddTodoUseCase
import com.example.myfirstapp.core.domain.usecase.DeleteTodoUseCase
import com.example.myfirstapp.core.domain.usecase.GetTodoUseCase
import com.example.myfirstapp.core.domain.usecase.ObserveSelectedTodoFilterUseCase
import com.example.myfirstapp.core.domain.usecase.ObserveSelectedTodoPriorityFilterUseCase
import com.example.myfirstapp.core.domain.usecase.ObserveTodosUseCase
import com.example.myfirstapp.core.domain.usecase.ToggleTodoDoneUseCase
import com.example.myfirstapp.core.domain.usecase.UpdateSelectedTodoFilterUseCase
import com.example.myfirstapp.core.domain.usecase.UpdateSelectedTodoPriorityFilterUseCase
import com.example.myfirstapp.core.domain.usecase.UpdateTodoUseCase
import com.example.myfirstapp.core.model.ReminderRepeatType
import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.model.TodoItem
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.core.model.TodoPriorityFilter
import com.example.myfirstapp.core.testing.repository.FakeTodoRepository
import com.example.myfirstapp.core.testing.rule.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeTodoRepository
    private lateinit var reminderScheduler: RecordingReminderScheduler
    private lateinit var viewModel: TodoListViewModel

    @Before
    fun setUp() {
        repository = FakeTodoRepository()
        reminderScheduler = RecordingReminderScheduler()
        viewModel = TodoListViewModel(
            observeTodosUseCase = ObserveTodosUseCase(repository),
            observeSelectedTodoFilterUseCase = ObserveSelectedTodoFilterUseCase(repository),
            observeSelectedTodoPriorityFilterUseCase = ObserveSelectedTodoPriorityFilterUseCase(repository),
            addTodoUseCase = AddTodoUseCase(repository),
            updateTodoUseCase = UpdateTodoUseCase(repository),
            deleteTodoUseCase = DeleteTodoUseCase(repository),
            toggleTodoDoneUseCase = ToggleTodoDoneUseCase(repository),
            updateSelectedTodoFilterUseCase = UpdateSelectedTodoFilterUseCase(repository),
            updateSelectedTodoPriorityFilterUseCase = UpdateSelectedTodoPriorityFilterUseCase(repository),
            getTodoUseCase = GetTodoUseCase(repository),
            todoReminderScheduler = reminderScheduler
        )
    }

    @Test
    fun addClickOpensEditSheet() = runTest {
        viewModel.onAction(TodoListAction.OnAddClick)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isEditDialogVisible).isTrue()
        assertThat(viewModel.uiState.value.draftPriority).isEqualTo(TodoPriority.MEDIUM)
    }

    @Test
    fun saveCreatesTodoWithSelectedPriority() = runTest {
        viewModel.onAction(TodoListAction.OnAddClick)
        viewModel.onAction(TodoListAction.OnTitleChange("Pay electricity"))
        viewModel.onAction(TodoListAction.OnPrioritySelectedInEditor(TodoPriority.HIGH))

        viewModel.onAction(TodoListAction.OnSaveClick)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isEditDialogVisible).isFalse()
        assertThat(state.items).hasSize(1)
        assertThat(state.items.first().priority).isEqualTo(TodoPriority.HIGH)
    }

    @Test
    fun priorityFilterShowsOnlyMatchingItems() = runTest {
        repository.addTodo(title = "Low", dueDate = null, categoryId = null, priority = TodoPriority.LOW)
        repository.addTodo(title = "Medium", dueDate = null, categoryId = null, priority = TodoPriority.MEDIUM)
        repository.addTodo(title = "High", dueDate = null, categoryId = null, priority = TodoPriority.HIGH)

        viewModel.onAction(TodoListAction.OnPriorityFilterChange(TodoPriorityFilter.HIGH))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedPriorityFilter).isEqualTo(TodoPriorityFilter.HIGH)
        assertThat(state.items.map { it.title }).containsExactly("High")
    }

    @Test
    fun todayFilterIncludesOverdueTodayAndHighPriorityOpenItems() = runTest {
        val today = LocalDate.now()
        repository.addTodo(title = "Overdue", dueDate = today.minusDays(1), categoryId = null)
        repository.addTodo(title = "Today", dueDate = today, categoryId = null)
        repository.addTodo(
            title = "High no date",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.HIGH
        )
        val completedId = repository.addTodo(title = "Done today", dueDate = today, categoryId = null)
            .getOrThrow()
        repository.toggleTodoDone(completedId)

        viewModel.onAction(TodoListAction.OnFilterChange(TodoFilter.TODAY))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.items.map { it.title })
            .containsExactly("Overdue", "Today", "High no date")
    }

    @Test
    fun moveToTomorrowUpdatesDueDateAndCanUndo() = runTest {
        val today = LocalDate.now()
        val id = repository.addTodo(
            title = "Plan review",
            dueDate = today,
            categoryId = null,
            dueTimeMinutes = 9 * 60,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        ).getOrThrow()

        viewModel.onAction(TodoListAction.OnMoveToTomorrow(id))
        advanceUntilIdle()

        assertThat(repository.getTodo(id)?.dueDate).isEqualTo(today.plusDays(1))

        viewModel.onAction(TodoListAction.OnUndoLastQuickAction)
        advanceUntilIdle()

        assertThat(repository.getTodo(id)?.dueDate).isEqualTo(today)
    }

    @Test
    fun clearScheduleClearsDateTimeAndReminder() = runTest {
        val id = repository.addTodo(
            title = "Timed reminder",
            dueDate = LocalDate.now(),
            categoryId = null,
            dueTimeMinutes = 9 * 60,
            reminderAtEpochMillis = System.currentTimeMillis() + 3_600_000,
            isReminderEnabled = true,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = 10
        ).getOrThrow()

        viewModel.onAction(TodoListAction.OnClearSchedule(id))
        advanceUntilIdle()

        val todo = checkNotNull(repository.getTodo(id))
        assertThat(todo.dueDate).isNull()
        assertThat(todo.dueTimeMinutes).isNull()
        assertThat(todo.isReminderEnabled).isFalse()
        assertThat(todo.reminderAtEpochMillis).isNull()
    }

    @Test
    fun undoAfterDeletingCompletedTaskRestoresCompletedState() = runTest {
        val id = repository.addTodo(
            title = "Done task",
            dueDate = null,
            categoryId = null,
            dueTimeMinutes = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        ).getOrThrow()
        repository.toggleTodoDone(id)
        advanceUntilIdle()

        viewModel.onAction(TodoListAction.OnDeleteRequest(id))
        viewModel.onAction(TodoListAction.OnDeleteConfirm)
        advanceUntilIdle()
        assertThat(repository.getTodo(id)).isNull()

        viewModel.onAction(TodoListAction.OnUndoLastQuickAction)
        advanceUntilIdle()

        val restored = repository.observeTodos().first { todos -> todos.isNotEmpty() }.first()
        assertThat(restored.title).isEqualTo("Done task")
        assertThat(restored.isDone).isTrue()
    }

    @Test
    fun editFlowUpdatesPriority() = runTest {
        val id = repository.addTodo(
            title = "Design review",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.LOW
        ).getOrThrow()
        advanceUntilIdle()

        viewModel.onAction(TodoListAction.OnEditClick(id))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isEditDialogVisible).isTrue()
        assertThat(viewModel.uiState.value.draftPriority).isEqualTo(TodoPriority.LOW)

        viewModel.onAction(TodoListAction.OnPrioritySelectedInEditor(TodoPriority.HIGH))
        viewModel.onAction(TodoListAction.OnSaveClick)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.items.first().priority).isEqualTo(TodoPriority.HIGH)
    }

    @Test
    fun dismissClearsDraftState() = runTest {
        viewModel.onAction(TodoListAction.OnAddClick)
        viewModel.onAction(TodoListAction.OnTitleChange("Temp"))
        viewModel.onAction(TodoListAction.OnPrioritySelectedInEditor(TodoPriority.HIGH))

        viewModel.onAction(TodoListAction.OnDismissDialog)

        val state = viewModel.uiState.value
        assertThat(state.isEditDialogVisible).isFalse()
        assertThat(state.draftTitle).isEmpty()
        assertThat(state.draftPriority).isEqualTo(TodoPriority.MEDIUM)
    }

    @Test
    fun deleteRequestOpensConfirmationAndCancelKeepsItem() = runTest {
        val id = repository.addTodo(
            title = "Keep me",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.MEDIUM
        ).getOrThrow()
        advanceUntilIdle()

        viewModel.onAction(TodoListAction.OnDeleteRequest(id))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.deleteConfirmation)
            .isEqualTo(TodoDeleteConfirmation.Single(id))

        viewModel.onAction(TodoListAction.OnDeleteCancel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.deleteConfirmation).isNull()
        assertThat(state.items.map { it.id }).contains(id)
    }

    @Test
    fun deleteConfirmRemovesItemCancelsReminderAndCanUndo() = runTest {
        val id = repository.addTodo(
            title = "Remove me",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.MEDIUM
        ).getOrThrow()
        advanceUntilIdle()

        viewModel.onAction(TodoListAction.OnDeleteRequest(id))
        viewModel.onAction(TodoListAction.OnDeleteConfirm)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.deleteConfirmation).isNull()
        assertThat(state.items.map { it.id }).doesNotContain(id)
        assertThat(reminderScheduler.cancelledTodoIds).containsExactly(id)

        viewModel.onAction(TodoListAction.OnUndoLastQuickAction)
        advanceUntilIdle()

        assertThat(repository.observeTodos().first().map { it.title }).contains("Remove me")
    }

    @Test
    fun editDeleteConfirmClosesEditorAndRemovesItem() = runTest {
        val id = repository.addTodo(
            title = "Edit delete",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.LOW
        ).getOrThrow()
        advanceUntilIdle()

        viewModel.onAction(TodoListAction.OnEditClick(id))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isEditDialogVisible).isTrue()

        viewModel.onAction(TodoListAction.OnDeleteRequest(id))
        viewModel.onAction(TodoListAction.OnDeleteConfirm)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isEditDialogVisible).isFalse()
        assertThat(state.editingItem).isNull()
        assertThat(state.items.map { it.id }).doesNotContain(id)
    }

    @Test
    fun clearCompletedDeletesAllCompletedItemsIgnoringPriorityFilter() = runTest {
        val lowCompletedId = repository.addTodo(
            title = "Low done",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.LOW
        ).getOrThrow()
        val highCompletedId = repository.addTodo(
            title = "High done",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.HIGH
        ).getOrThrow()
        val activeHighId = repository.addTodo(
            title = "High active",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.HIGH
        ).getOrThrow()
        repository.toggleTodoDone(lowCompletedId)
        repository.toggleTodoDone(highCompletedId)
        advanceUntilIdle()

        viewModel.onAction(TodoListAction.OnFilterChange(TodoFilter.COMPLETED))
        viewModel.onAction(TodoListAction.OnPriorityFilterChange(TodoPriorityFilter.HIGH))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items.map { it.id }).containsExactly(highCompletedId)

        viewModel.onAction(TodoListAction.OnClearCompletedClick)
        advanceUntilIdle()
        val confirmation = viewModel.uiState.value.deleteConfirmation
        assertThat(confirmation).isInstanceOf(TodoDeleteConfirmation.Completed::class.java)
        assertThat(checkNotNull(confirmation).ids)
            .containsExactly(lowCompletedId, highCompletedId)

        viewModel.onAction(TodoListAction.OnDeleteConfirm)
        advanceUntilIdle()

        assertThat(repository.getTodo(lowCompletedId)).isNull()
        assertThat(repository.getTodo(highCompletedId)).isNull()
        assertThat(repository.getTodo(activeHighId)).isNotNull()
        assertThat(reminderScheduler.cancelledTodoIds)
            .containsExactly(lowCompletedId, highCompletedId)
    }

    private class RecordingReminderScheduler : TodoReminderScheduler {
        val cancelledTodoIds = mutableListOf<Long>()

        override suspend fun schedule(todo: TodoItem) = Unit
        override suspend fun cancel(todoId: Long) {
            cancelledTodoIds += todoId
        }

        override suspend fun rescheduleAll() = Unit
    }
}
