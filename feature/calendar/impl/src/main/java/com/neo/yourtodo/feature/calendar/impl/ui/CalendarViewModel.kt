package com.neo.yourtodo.feature.calendar.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodoSummariesUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ToggleTodoDoneUseCase
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min

private const val STATE_MONTH_KEY = "calendar_month"
private const val STATE_SELECTED_DATE_KEY = "calendar_selected_date"

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    observeMonthlyTodoSummariesUseCase: ObserveMonthlyTodoSummariesUseCase,
    observeMonthlyTodosUseCase: ObserveMonthlyTodosUseCase,
    private val toggleTodoDoneUseCase: ToggleTodoDoneUseCase
) : ViewModel() {
    private val monthState = MutableStateFlow(savedStateHandle.initialMonth())
    private val selectedDateState = MutableStateFlow(savedStateHandle.initialSelectedDate())
    private val sideEffectMutable = MutableSharedFlow<CalendarSideEffect>()

    val sideEffect = sideEffectMutable.asSharedFlow()

    private val monthlyTodos = monthState
        .flatMapLatest { yearMonth -> observeMonthlyTodosUseCase(yearMonth) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val summariesByDate = monthState
        .flatMapLatest { yearMonth -> observeMonthlyTodoSummariesUseCase(yearMonth) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    private val selectedDateTodos = combine(
        selectedDateState,
        monthlyTodos
    ) { selectedDate, todos ->
        todos
            .asSequence()
            .filter { it.dueDate == selectedDate }
            .sortedWith(
                compareBy<TodoItem> { it.isDone }
                    .thenByDescending {
                        when (it.priority) {
                            TodoPriority.HIGH -> 3
                            TodoPriority.MEDIUM -> 2
                            TodoPriority.LOW -> 1
                        }
                    }
                    .thenBy { it.id }
            )
            .map { it.toSelectedTodoUiModel() }
            .toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<CalendarUiState> = combine(
        monthState,
        selectedDateState,
        summariesByDate,
        selectedDateTodos,
        observeAuthSessionUseCase()
    ) { currentMonth, selectedDate, summaries, dateTodos, authSession ->
        val adjustedSelectedDate = selectedDate.normalizeToMonth(currentMonth)
        CalendarUiState(
            profileInitial = authSession?.user?.nickname,
            currentMonth = currentMonth,
            selectedDate = adjustedSelectedDate,
            days = buildMonthCells(
                yearMonth = currentMonth,
                selectedDate = adjustedSelectedDate,
                today = LocalDate.now(),
                summariesByDate = summaries
            ),
            summariesByDate = summaries,
            todayTaskCount = summaries.todayTaskCount(today = LocalDate.now()),
            selectedDateTodos = dateTodos
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = initialCalendarUiState(
            currentMonth = monthState.value,
            selectedDate = selectedDateState.value
        )
    )

    fun onAction(action: CalendarAction) {
        when (action) {
            CalendarAction.OnNextMonthClick -> moveMonthBy(1)
            CalendarAction.OnPreviousMonthClick -> moveMonthBy(-1)
            is CalendarAction.OnDateClick -> {
                updateSelectedDate(action.date)
            }

            is CalendarAction.OnTodoClick -> {
                viewModelScope.launch {
                    sideEffectMutable.emit(CalendarSideEffect.NavigateToTodoEdit(action.todoId))
                }
            }

            is CalendarAction.OnToggleTodoDone -> toggleTodoDone(action.todoId)

            CalendarAction.OnAddTodoClick -> {
                viewModelScope.launch {
                    sideEffectMutable.emit(CalendarSideEffect.NavigateToTodoAdd(selectedDateState.value))
                }
            }
        }
    }

    fun selectRouteDate(rawDate: String) {
        val selectedDate = runCatching { LocalDate.parse(rawDate) }
            .getOrDefault(LocalDate.now())
        updateMonthAndSelectedDate(
            month = YearMonth.from(selectedDate),
            selectedDate = selectedDate
        )
    }

    private fun moveMonthBy(offsetMonths: Long) {
        val newMonth = monthState.value.plusMonths(offsetMonths)
        updateMonthAndSelectedDate(
            month = newMonth,
            selectedDate = selectedDateState.value.normalizeToMonth(newMonth)
        )
    }

    private fun updateSelectedDate(date: LocalDate) {
        savedStateHandle[STATE_SELECTED_DATE_KEY] = date.toString()
        selectedDateState.value = date
    }

    private fun updateMonthAndSelectedDate(
        month: YearMonth,
        selectedDate: LocalDate
    ) {
        savedStateHandle[STATE_MONTH_KEY] = month.toString()
        savedStateHandle[STATE_SELECTED_DATE_KEY] = selectedDate.toString()
        monthState.value = month
        selectedDateState.value = selectedDate
    }

    private fun toggleTodoDone(todoId: Long) {
        viewModelScope.launch {
            toggleTodoDoneUseCase(todoId)
        }
    }
}

internal fun SavedStateHandle.initialMonth(): YearMonth =
    get<String>(STATE_MONTH_KEY)
        ?.let { rawMonth -> runCatching { YearMonth.parse(rawMonth) }.getOrNull() }
        ?: YearMonth.now()

internal fun SavedStateHandle.initialSelectedDate(): LocalDate =
    get<String>(STATE_SELECTED_DATE_KEY)
        ?.let { rawDate -> runCatching { LocalDate.parse(rawDate) }.getOrNull() }
        ?: LocalDate.now()

internal fun initialCalendarUiState(
    currentMonth: YearMonth,
    selectedDate: LocalDate
): CalendarUiState {
    val today = LocalDate.now()
    val adjustedSelectedDate = selectedDate.normalizeToMonth(currentMonth)
    return CalendarUiState(
        currentMonth = currentMonth,
        selectedDate = adjustedSelectedDate,
        days = buildMonthCells(
            yearMonth = currentMonth,
            selectedDate = adjustedSelectedDate,
            today = today,
            summariesByDate = emptyMap()
        ),
        summariesByDate = emptyMap(),
        todayTaskCount = 0,
        selectedDateTodos = emptyList()
    )
}

internal fun buildMonthCells(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    summariesByDate: Map<LocalDate, DateTodoSummary>
): List<CalendarDayUiModel> {
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val firstDate = yearMonth.atDay(1)
    val leadingBlanks = firstDate.dayOfWeek.distanceFrom(firstDayOfWeek)
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7
    val previousMonth = yearMonth.minusMonths(1)
    val nextMonth = yearMonth.plusMonths(1)
    val previousMonthDays = previousMonth.lengthOfMonth()

    return List(totalCells) { index ->
        val dayOfMonth = index - leadingBlanks + 1
        val isCurrentMonth = dayOfMonth in 1..daysInMonth
        val date = when {
            dayOfMonth < 1 -> previousMonth.atDay(previousMonthDays + dayOfMonth)
            dayOfMonth > daysInMonth -> nextMonth.atDay(dayOfMonth - daysInMonth)
            else -> yearMonth.atDay(dayOfMonth)
        }
        val summary = summariesByDate[date]

        CalendarDayUiModel(
            date = date,
            isCurrentMonth = isCurrentMonth,
            isToday = date == today,
            isSelected = isCurrentMonth && date == selectedDate,
            indicatorCount = if (isCurrentMonth) summary?.indicatorCount ?: 0 else 0,
            overflowCount = if (isCurrentMonth) summary?.overflowCount ?: 0 else 0
        )
    }
}

internal fun LocalDate.normalizeToMonth(targetMonth: YearMonth): LocalDate {
    val normalizedDay = min(dayOfMonth, targetMonth.lengthOfMonth())
    return targetMonth.atDay(normalizedDay)
}

internal fun DayOfWeek.distanceFrom(other: DayOfWeek): Int =
    (value - other.value + 7) % 7

internal fun TodoItem.toSelectedTodoUiModel(): CalendarSelectedTodoUiModel =
    CalendarSelectedTodoUiModel(
        id = id,
        title = title,
        isDone = isDone,
        priority = priority,
        isReminderEnabled = isReminderEnabled,
        dueTimeLabel = dueTimeMinutes?.let(::formatLocalTimeFromMinutes)
            ?: reminderAtEpochMillis?.let(::formatLocalTimeFromEpochMillis),
        reminderLeadMinutes = reminderLeadMinutes
    )

private fun formatLocalTimeFromMinutes(minutes: Int): String {
    val normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    return java.time.LocalTime.of(normalized / 60, normalized % 60)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

private fun formatLocalTimeFromEpochMillis(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

internal fun Map<LocalDate, DateTodoSummary>.todayTaskCount(today: LocalDate): Int {
    val summary = this[today] ?: return 0
    return summary.indicatorCount + summary.overflowCount
}
