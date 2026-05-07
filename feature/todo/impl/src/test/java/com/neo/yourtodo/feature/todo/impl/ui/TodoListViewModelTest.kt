package com.neo.yourtodo.feature.todo.impl.ui

import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.scheduler.TodoReminderScheduler
import com.neo.yourtodo.core.domain.usecase.AddTodoUseCase
import com.neo.yourtodo.core.domain.usecase.DeleteTodoUseCase
import com.neo.yourtodo.core.domain.usecase.GetTodoUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ToggleTodoDoneUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateSelectedTodoPriorityFilterUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateTodoUseCase
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import com.neo.yourtodo.feature.todo.impl.R
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeTodoRepository
    private lateinit var reminderScheduler: RecordingReminderScheduler
    private lateinit var calendarWidgetUpdater: RecordingCalendarWidgetUpdater
    private lateinit var viewModel: TodoListViewModel
    private lateinit var uiStateCollectionJob: Job

    @Before
    fun setUp() {
        repository = FakeTodoRepository()
        reminderScheduler = RecordingReminderScheduler()
        calendarWidgetUpdater = RecordingCalendarWidgetUpdater()
        viewModel = TodoListViewModel(
            observeTodosUseCase = ObserveTodosUseCase(repository),
            addTodoUseCase = AddTodoUseCase(repository),
            updateTodoUseCase = UpdateTodoUseCase(repository),
            deleteTodoUseCase = DeleteTodoUseCase(repository),
            toggleTodoDoneUseCase = ToggleTodoDoneUseCase(repository),
            updateSelectedTodoPriorityFilterUseCase = UpdateSelectedTodoPriorityFilterUseCase(repository),
            getTodoUseCase = GetTodoUseCase(repository),
            todoReminderScheduler = reminderScheduler,
            calendarWidgetUpdater = calendarWidgetUpdater
        )
        uiStateCollectionJob = CoroutineScope(mainDispatcherRule.testDispatcher).launch {
            viewModel.uiState.collect()
        }
    }

    @After
    fun tearDown() {
        uiStateCollectionJob.cancel()
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
    fun quickAddClickOpensQuickAddSlot() = runTest {
        viewModel.onAction(TodoListAction.OnQuickAddClick)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isQuickAddVisible).isTrue()
        assertThat(state.quickAddErrorMessageRes).isNull()
    }

    @Test
    fun quickAddSubmitInAllCreatesUnscheduledTodoAndKeepsSlotOpen() = runTest {
        viewModel.onAction(TodoListAction.OnQuickAddClick)
        viewModel.onAction(TodoListAction.OnQuickAddTitleChange("  Buy milk  "))

        viewModel.onAction(TodoListAction.OnQuickAddSubmit)
        advanceUntilIdle()

        val created = repository.observeTodos().first().single()
        assertThat(created.title).isEqualTo("Buy milk")
        assertThat(created.dueDate).isNull()
        assertThat(created.priority).isEqualTo(TodoPriority.MEDIUM)

        val state = viewModel.uiState.value
        assertThat(state.isQuickAddVisible).isTrue()
        assertThat(state.quickAddTitle).isEmpty()
        assertThat(state.quickAddErrorMessageRes).isNull()
    }

    @Test
    fun quickAddSubmitInTodayCreatesTodoDueToday() = runTest {
        val today = LocalDate.now()
        viewModel.setRouteFilter(TodoFilter.TODAY)
        viewModel.onAction(TodoListAction.OnQuickAddClick)
        viewModel.onAction(TodoListAction.OnQuickAddTitleChange("Plan lunch"))

        viewModel.onAction(TodoListAction.OnQuickAddSubmit)
        advanceUntilIdle()

        val created = repository.observeTodos().first().single()
        assertThat(created.title).isEqualTo("Plan lunch")
        assertThat(created.dueDate).isEqualTo(today)
        assertThat(viewModel.uiState.value.items.map { it.title }).containsExactly("Plan lunch")
    }

    @Test
    fun quickAddSubmitUsesSelectedPriorityFilterForVisibility() = runTest {
        viewModel.onAction(TodoListAction.OnPriorityFilterChange(TodoPriorityFilter.HIGH))
        advanceUntilIdle()
        viewModel.onAction(TodoListAction.OnQuickAddClick)
        viewModel.onAction(TodoListAction.OnQuickAddTitleChange("Visible high task"))

        viewModel.onAction(TodoListAction.OnQuickAddSubmit)
        advanceUntilIdle()

        val created = repository.observeTodos().first().single()
        assertThat(created.priority).isEqualTo(TodoPriority.HIGH)
        assertThat(viewModel.uiState.value.items.map { it.title }).containsExactly("Visible high task")
    }

    @Test
    fun quickAddSubmitWithBlankTitleShowsErrorAndDoesNotCreateTodo() = runTest {
        viewModel.onAction(TodoListAction.OnQuickAddClick)
        viewModel.onAction(TodoListAction.OnQuickAddTitleChange("   "))

        viewModel.onAction(TodoListAction.OnQuickAddSubmit)
        advanceUntilIdle()

        assertThat(repository.observeTodos().first()).isEmpty()
        val state = viewModel.uiState.value
        assertThat(state.isQuickAddVisible).isTrue()
        assertThat(state.quickAddErrorMessageRes).isEqualTo(R.string.todo_error_title_required)
    }

    @Test
    fun quickAddDismissClearsDraftAndError() = runTest {
        viewModel.onAction(TodoListAction.OnQuickAddClick)
        viewModel.onAction(TodoListAction.OnQuickAddTitleChange("Temp"))
        viewModel.onAction(TodoListAction.OnQuickAddSubmit)
        advanceUntilIdle()
        viewModel.onAction(TodoListAction.OnQuickAddTitleChange("   "))
        viewModel.onAction(TodoListAction.OnQuickAddSubmit)
        advanceUntilIdle()

        viewModel.onAction(TodoListAction.OnQuickAddDismiss)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isQuickAddVisible).isFalse()
        assertThat(state.quickAddTitle).isEmpty()
        assertThat(state.quickAddErrorMessageRes).isNull()
    }

    @Test
    fun quickAddClickInCompletedShowsGuidanceSnackbar() = runTest {
        val sideEffect = async { viewModel.sideEffect.first() }
        viewModel.setRouteFilter(TodoFilter.COMPLETED)
        advanceUntilIdle()

        viewModel.onAction(TodoListAction.OnQuickAddClick)
        advanceUntilIdle()

        assertThat(sideEffect.await()).isEqualTo(
            TodoListSideEffect.ShowSnackbar(
                messageRes = R.string.todo_quick_add_completed_unavailable
            )
        )
        assertThat(viewModel.uiState.value.isQuickAddVisible).isFalse()
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
    fun defaultSortKeepsOpenItemsInRecentOrder() = runTest {
        repository.addTodo(title = "Low first", dueDate = null, categoryId = null, priority = TodoPriority.LOW)
        repository.addTodo(title = "High second", dueDate = null, categoryId = null, priority = TodoPriority.HIGH)
        repository.addTodo(title = "Medium third", dueDate = null, categoryId = null, priority = TodoPriority.MEDIUM)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedSortOption).isEqualTo(TodoSortOption.DEFAULT)
        assertThat(state.items.map { it.title }).containsExactly(
            "Medium third",
            "High second",
            "Low first"
        ).inOrder()
    }

    @Test
    fun allDefaultSortIncludesUnscheduledFutureTodayAndCompletedItems() = runTest {
        val today = LocalDate.now()
        val completedId = repository.addTodo(
            title = "Completed",
            dueDate = today,
            categoryId = null,
            priority = TodoPriority.HIGH
        ).getOrThrow()
        repository.addTodo(title = "Future", dueDate = today.plusDays(7), categoryId = null)
        repository.addTodo(title = "No date", dueDate = null, categoryId = null)
        repository.addTodo(title = "Today", dueDate = today, categoryId = null)
        repository.toggleTodoDone(completedId)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedFilter).isEqualTo(TodoFilter.ALL)
        assertThat(state.selectedPriorityFilter).isEqualTo(TodoPriorityFilter.ALL)
        assertThat(state.items.map { it.title }).containsAtLeast(
            "Completed",
            "Future",
            "No date",
            "Today"
        )
    }

    @Test
    fun persistedPriorityFilterDoesNotHideItemsOnFreshTodoScreen() = runTest {
        val today = LocalDate.now()
        repository.setSelectedPriorityFilter(TodoPriorityFilter.MEDIUM)
        repository.addTodo(
            title = "Today medium",
            dueDate = today,
            categoryId = null,
            priority = TodoPriority.MEDIUM
        )
        repository.addTodo(
            title = "Future high",
            dueDate = today.plusDays(2),
            categoryId = null,
            dueTimeMinutes = 2 * 60,
            priority = TodoPriority.HIGH
        )
        val completedId = repository.addTodo(
            title = "Completed medium",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.MEDIUM
        ).getOrThrow()
        repository.toggleTodoDone(completedId)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedPriorityFilter).isEqualTo(TodoPriorityFilter.ALL)
        assertThat(state.items.map { it.title }).containsExactly(
            "Future high",
            "Today medium",
            "Completed medium"
        ).inOrder()
    }

    @Test
    fun dueDateSortShowsDatedOpenItemsBeforeUnscheduledAndCompleted() = runTest {
        val today = LocalDate.now()
        repository.addTodo(
            title = "No date",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.HIGH
        )
        repository.addTodo(
            title = "Future",
            dueDate = today.plusDays(3),
            categoryId = null,
            priority = TodoPriority.HIGH
        )
        repository.addTodo(
            title = "Today late",
            dueDate = today,
            categoryId = null,
            dueTimeMinutes = 17 * 60,
            priority = TodoPriority.MEDIUM
        )
        repository.addTodo(
            title = "Today early",
            dueDate = today,
            categoryId = null,
            dueTimeMinutes = 9 * 60,
            priority = TodoPriority.LOW
        )
        val completedId = repository.addTodo(
            title = "Completed urgent",
            dueDate = today.minusDays(1),
            categoryId = null,
            priority = TodoPriority.HIGH
        ).getOrThrow()
        repository.toggleTodoDone(completedId)

        viewModel.onAction(TodoListAction.OnSortOptionChange(TodoSortOption.DUE_DATE))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedSortOption).isEqualTo(TodoSortOption.DUE_DATE)
        assertThat(state.items.map { it.title }).containsExactly(
            "Today early",
            "Today late",
            "Future",
            "No date",
            "Completed urgent"
        ).inOrder()
    }

    @Test
    fun prioritySortShowsHigherPriorityFirstWithDueDateTieBreaker() = runTest {
        val today = LocalDate.now()
        repository.addTodo(
            title = "Medium overdue",
            dueDate = today.minusDays(1),
            categoryId = null,
            priority = TodoPriority.MEDIUM
        )
        repository.addTodo(
            title = "High future",
            dueDate = today.plusDays(2),
            categoryId = null,
            priority = TodoPriority.HIGH
        )
        repository.addTodo(
            title = "High today",
            dueDate = today,
            categoryId = null,
            priority = TodoPriority.HIGH
        )
        repository.addTodo(
            title = "Low today",
            dueDate = today,
            categoryId = null,
            priority = TodoPriority.LOW
        )

        viewModel.onAction(TodoListAction.OnSortOptionChange(TodoSortOption.PRIORITY))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedSortOption).isEqualTo(TodoSortOption.PRIORITY)
        assertThat(state.items.map { it.title }).containsExactly(
            "High today",
            "High future",
            "Medium overdue",
            "Low today"
        ).inOrder()
    }

    @Test
    fun sortOptionChangeClearsPriorityFilterBeforeSortingAllItems() = runTest {
        val today = LocalDate.now()
        repository.addTodo(
            title = "High future",
            dueDate = today.plusDays(3),
            categoryId = null,
            priority = TodoPriority.HIGH
        )
        repository.addTodo(
            title = "Medium today",
            dueDate = today,
            categoryId = null,
            priority = TodoPriority.MEDIUM
        )
        repository.addTodo(
            title = "High today",
            dueDate = today,
            categoryId = null,
            priority = TodoPriority.HIGH
        )
        repository.addTodo(
            title = "Low tomorrow",
            dueDate = today.plusDays(1),
            categoryId = null,
            priority = TodoPriority.LOW
        )

        viewModel.onAction(TodoListAction.OnPriorityFilterChange(TodoPriorityFilter.HIGH))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items.map { it.title }).containsExactly(
            "High future",
            "High today"
        )

        viewModel.onAction(TodoListAction.OnSortOptionChange(TodoSortOption.DUE_DATE))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedPriorityFilter).isEqualTo(TodoPriorityFilter.ALL)
        assertThat(state.selectedSortOption).isEqualTo(TodoSortOption.DUE_DATE)
        assertThat(state.items.map { it.title }).containsExactly(
            "High today",
            "Medium today",
            "Low tomorrow",
            "High future"
        ).inOrder()
    }

    @Test
    fun sortOptionReselectKeepsPriorityFilter() = runTest {
        val today = LocalDate.now()
        repository.addTodo(
            title = "High future",
            dueDate = today.plusDays(2),
            categoryId = null,
            priority = TodoPriority.HIGH
        )
        repository.addTodo(
            title = "Medium today",
            dueDate = today,
            categoryId = null,
            priority = TodoPriority.MEDIUM
        )
        repository.addTodo(
            title = "High today",
            dueDate = today,
            categoryId = null,
            priority = TodoPriority.HIGH
        )

        viewModel.onAction(TodoListAction.OnSortOptionChange(TodoSortOption.DUE_DATE))
        advanceUntilIdle()
        viewModel.onAction(TodoListAction.OnPriorityFilterChange(TodoPriorityFilter.HIGH))
        advanceUntilIdle()

        viewModel.onAction(TodoListAction.OnSortOptionChange(TodoSortOption.DUE_DATE))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedSortOption).isEqualTo(TodoSortOption.DUE_DATE)
        assertThat(state.selectedPriorityFilter).isEqualTo(TodoPriorityFilter.HIGH)
        assertThat(state.items.map { it.title }).containsExactly(
            "High today",
            "High future"
        ).inOrder()
    }

    @Test
    fun sortOptionsKeepAllThreeItemsVisibleWhenPriorityAndDueDateDiffer() = runTest {
        val today = LocalDate.now()
        repository.addTodo(
            title = "High no date",
            dueDate = null,
            categoryId = null,
            priority = TodoPriority.HIGH
        )
        repository.addTodo(
            title = "Medium today",
            dueDate = today,
            categoryId = null,
            priority = TodoPriority.MEDIUM
        )
        repository.addTodo(
            title = "Low future",
            dueDate = today.plusDays(2),
            categoryId = null,
            priority = TodoPriority.LOW
        )

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items.map { it.title }).containsExactly(
            "Low future",
            "Medium today",
            "High no date"
        ).inOrder()

        viewModel.onAction(TodoListAction.OnSortOptionChange(TodoSortOption.DUE_DATE))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items.map { it.title }).containsExactly(
            "Medium today",
            "Low future",
            "High no date"
        ).inOrder()

        viewModel.onAction(TodoListAction.OnSortOptionChange(TodoSortOption.PRIORITY))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.items.map { it.title }).containsExactly(
            "High no date",
            "Medium today",
            "Low future"
        ).inOrder()
    }

    @Test
    fun todayFilterKeepsPlannerPriorityOrderingWhenAllSortChanges() = runTest {
        val today = LocalDate.now()
        repository.addTodo(
            title = "Low overdue",
            dueDate = today.minusDays(1),
            categoryId = null,
            priority = TodoPriority.LOW
        )
        repository.addTodo(
            title = "High today",
            dueDate = today,
            categoryId = null,
            priority = TodoPriority.HIGH
        )

        viewModel.onAction(TodoListAction.OnSortOptionChange(TodoSortOption.DUE_DATE))
        viewModel.onAction(TodoListAction.OnFilterChange(TodoFilter.TODAY))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedSortOption).isEqualTo(TodoSortOption.DUE_DATE)
        assertThat(state.items.map { it.title }).containsExactly(
            "High today",
            "Low overdue"
        ).inOrder()
    }

    @Test
    fun routeFilterAppliesPresetBeforeRenderingRouteState() = runTest {
        val today = LocalDate.now()
        repository.addTodo(title = "Today route item", dueDate = today, categoryId = null)
        repository.addTodo(title = "All route only item", dueDate = null, categoryId = null)

        viewModel.setRouteFilter(TodoFilter.TODAY)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedFilter).isEqualTo(TodoFilter.TODAY)
        assertThat(state.items.map { it.title }).containsExactly("Today route item")
    }

    @Test
    fun todayFilterIncludesOnlyOverdueAndTodayOpenItems() = runTest {
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
            .containsExactly("Overdue", "Today")
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

    private class RecordingCalendarWidgetUpdater : CalendarWidgetUpdater {
        var updateCount: Int = 0
            private set

        override suspend fun updateCalendarWidgets(): Result<Unit> {
            updateCount += 1
            return Result.success(Unit)
        }
    }
}
