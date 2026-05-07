package com.neo.yourtodo.feature.calendar.impl.ui

import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodoSummariesUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodosUseCase
import androidx.lifecycle.SavedStateHandle
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uiStateCollectionJobs = mutableListOf<Job>()

    @After
    fun tearDown() {
        uiStateCollectionJobs.forEach { job -> job.cancel() }
        uiStateCollectionJobs.clear()
    }

    @Test
    fun initialState_hasCurrentMonthAndSelectedDate() = runTest {
        val viewModel = createViewModel(FakeTodoRepository())

        val state = viewModel.uiState.value
        val today = LocalDate.now()

        assertThat(state.currentMonth).isEqualTo(java.time.YearMonth.from(today))
        assertThat(state.selectedDate).isEqualTo(today)
    }

    @Test
    fun nextMonthAction_movesMonthAndKeepsDayInRange() = runTest {
        val viewModel = createViewModel(FakeTodoRepository())
        val before = viewModel.uiState.value

        viewModel.onAction(CalendarAction.OnNextMonthClick)
        advanceUntilIdle()

        val after = viewModel.uiState.value
        val expectedMonth = before.currentMonth.plusMonths(1)
        val expectedDay = min(before.selectedDate.dayOfMonth, expectedMonth.lengthOfMonth())

        assertThat(after.currentMonth).isEqualTo(expectedMonth)
        assertThat(after.selectedDate).isEqualTo(expectedMonth.atDay(expectedDay))
    }

    @Test
    fun dateClickAction_updatesSelectedDate() = runTest {
        val viewModel = createViewModel(FakeTodoRepository())
        val targetDate = viewModel.uiState.value.currentMonth.atDay(15)

        viewModel.onAction(CalendarAction.OnDateClick(targetDate))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(targetDate)
    }

    @Test
    fun todoClickAction_emitsNavigateSideEffect() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val targetDate = viewModel.uiState.value.currentMonth.atDay(10)

        val todoId = repository.addTodo(
            title = "In month",
            dueDate = targetDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        ).getOrThrow()

        advanceUntilIdle()
        viewModel.onAction(CalendarAction.OnDateClick(targetDate))
        advanceUntilIdle()

        val emitted = async { viewModel.sideEffect.first() }
        viewModel.onAction(CalendarAction.OnTodoClick(todoId))
        advanceUntilIdle()

        assertThat(emitted.await()).isEqualTo(CalendarSideEffect.NavigateToTodoEdit(todoId))
    }

    @Test
    fun addTodoClickAction_emitsSelectedDateAddSideEffect() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val targetDate = viewModel.uiState.value.currentMonth.atDay(12)

        viewModel.onAction(CalendarAction.OnDateClick(targetDate))
        advanceUntilIdle()

        val emitted = async { viewModel.sideEffect.first() }
        viewModel.onAction(CalendarAction.OnAddTodoClick)
        advanceUntilIdle()

        assertThat(emitted.await()).isEqualTo(CalendarSideEffect.NavigateToTodoAdd(targetDate))
    }


    @Test
    fun selectedDateTodos_includeOnlySelectedDateTodos() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val currentMonth = viewModel.uiState.value.currentMonth
        val selectedDate = currentMonth.atDay(10)
        val anotherDate = currentMonth.atDay(11)

        repository.addTodo(
            title = "Selected date todo",
            dueDate = selectedDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        )
        repository.addTodo(
            title = "Another date todo",
            dueDate = anotherDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        )
        advanceUntilIdle()

        viewModel.onAction(CalendarAction.OnDateClick(selectedDate))
        advanceUntilIdle()

        val todos = viewModel.uiState.value.selectedDateTodos
        assertThat(todos).hasSize(1)
        assertThat(todos.first().title).isEqualTo("Selected date todo")
    }

    @Test
    fun summaries_includeOnlyCurrentMonthTodos() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val currentMonth = viewModel.uiState.value.currentMonth
        val inMonthDate = currentMonth.atDay(10)
        val outOfMonthDate = currentMonth.plusMonths(1).atDay(10)

        repository.addTodo(
            title = "In month",
            dueDate = inMonthDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        )
        repository.addTodo(
            title = "Out of month",
            dueDate = outOfMonthDate,
            categoryId = null,
            reminderAtEpochMillis = null,
            isReminderEnabled = false,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.summariesByDate.keys).contains(inMonthDate)
        assertThat(state.summariesByDate.keys).doesNotContain(outOfMonthDate)
        assertThat(state.summariesByDate[inMonthDate]?.indicatorCount).isEqualTo(1)
    }

    @Test
    fun selectedDateTodos_mapReminderFields() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(repository)
        val selectedDate = viewModel.uiState.value.currentMonth.atDay(8)

        repository.addTodo(
            title = "Reminder mapped",
            dueDate = selectedDate,
            categoryId = null,
            dueTimeMinutes = 9 * 60 + 30,
            reminderAtEpochMillis = 0L,
            isReminderEnabled = true,
            reminderRepeatType = ReminderRepeatType.NONE,
            reminderRepeatDaysMask = 0,
            reminderLeadMinutes = 10
        )
        advanceUntilIdle()

        viewModel.onAction(CalendarAction.OnDateClick(selectedDate))
        advanceUntilIdle()

        val todo = viewModel.uiState.value.selectedDateTodos.single()
        assertThat(todo.isReminderEnabled).isTrue()
        assertThat(todo.reminderLeadMinutes).isEqualTo(10)
        assertThat(todo.dueTimeLabel).isNotNull()
    }

    @Test
    fun buildMonthCells_includesAdjacentMonthDatesWithoutNullCells() {
        val yearMonth = YearMonth.of(2026, 4)
        val selectedDate = yearMonth.atDay(9)
        val today = selectedDate

        val cells = buildMonthCells(
            yearMonth = yearMonth,
            selectedDate = selectedDate,
            today = today,
            summariesByDate = emptyMap()
        )

        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val leadingBlanks = yearMonth.atDay(1).dayOfWeek.distanceFrom(firstDayOfWeek)
        val expectedFirstDate = yearMonth.atDay(1).minusDays(leadingBlanks.toLong())

        assertThat(cells).isNotEmpty()
        assertThat(cells.all { it.date != null }).isTrue()
        assertThat(cells.first().date).isEqualTo(expectedFirstDate)
        assertThat(cells.any { !it.isCurrentMonth }).isTrue()
    }

    @Test
    fun todayTaskCount_isExposedByUiState() {
        val today = LocalDate.of(2026, 4, 9)
        val uiState = CalendarUiState(
            currentMonth = YearMonth.of(2026, 4),
            selectedDate = today,
            days = emptyList(),
            summariesByDate = mapOf(
                today to DateTodoSummary(
                    date = today,
                    todos = emptyList(),
                    indicatorCount = 3,
                    overflowCount = 2
                )
            ),
            todayTaskCount = 5,
            selectedDateTodos = emptyList()
        )

        assertThat(uiState.todayTaskCount).isEqualTo(5)
    }

    private fun createViewModel(repository: FakeTodoRepository): CalendarViewModel {
        val viewModel = CalendarViewModel(
            savedStateHandle = SavedStateHandle(),
            observeMonthlyTodoSummariesUseCase = ObserveMonthlyTodoSummariesUseCase(
                observeMonthlyTodosUseCase = ObserveMonthlyTodosUseCase(repository)
            ),
            observeMonthlyTodosUseCase = ObserveMonthlyTodosUseCase(repository)
        )
        uiStateCollectionJobs += CoroutineScope(mainDispatcherRule.testDispatcher).launch {
            viewModel.uiState.collect()
        }
        return viewModel
    }
}
