package com.neo.yourtodo.feature.calendar.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ManageAssignedTodoUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodoSummariesUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ToggleTodoDoneUseCase
import com.neo.yourtodo.core.domain.usecase.WorkspaceSyncNotifier
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.feature.calendar.impl.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    private val toggleTodoDoneUseCase: ToggleTodoDoneUseCase,
    private val getAssignedTodosUseCase: GetAssignedTodosUseCase,
    private val manageAssignedTodoUseCase: ManageAssignedTodoUseCase,
    private val calendarWidgetUpdater: CalendarWidgetUpdater,
    private val workspaceSyncNotifier: WorkspaceSyncNotifier = WorkspaceSyncNotifier()
) : ViewModel() {
    private val monthState = MutableStateFlow(savedStateHandle.initialMonth())
    private val selectedDateState = MutableStateFlow(savedStateHandle.initialSelectedDate())
    private val receivedAssignedTodos = merge(
        getAssignedTodosUseCase.observeVisibleReceived(),
        workspaceSyncNotifier.snapshots
            .filterNotNull()
            .map { snapshot -> snapshot.visibleReceivedAssignedTodos }
    )
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
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

    private val mergedSummariesByDate = combine(
        monthState,
        summariesByDate,
        receivedAssignedTodos
    ) { currentMonth, summaries, assignedTodos ->
        summaries.withAssignedTodos(
            yearMonth = currentMonth,
            assignedTodos = assignedTodos
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )

    private val selectedDateTodos = combine(
        selectedDateState,
        monthlyTodos,
        receivedAssignedTodos
    ) { selectedDate, todos, assignedTodos ->
        val localTodos = todos
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

        val assignedDateTodos = assignedTodos
            .asSequence()
            .filter { it.dueDate == selectedDate }
            .sortedWith(
                compareBy<AssignedTodo> { it.isDone }
                    .thenByDescending { it.priority.sortRank() }
                    .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
                    .thenBy { it.title }
            )
            .map { it.toSelectedTodoUiModel() }
            .toList()

        localTodos + assignedDateTodos
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val calendarContentState = combine(
        monthState,
        selectedDateState,
        mergedSummariesByDate,
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
    }

    val uiState: StateFlow<CalendarUiState> = calendarContentState.stateIn(
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
                    if (action.assignedTodoId == null) {
                        sideEffectMutable.emit(CalendarSideEffect.NavigateToTodoEdit(action.todoId))
                    } else {
                        sideEffectMutable.emit(CalendarSideEffect.NavigateToAssignedTodoEdit(action.assignedTodoId))
                    }
                }
            }

            is CalendarAction.OnToggleTodoDone -> toggleTodoDone(action.todoId, action.assignedTodoId)

            CalendarAction.OnAddTodoClick -> {
                viewModelScope.launch {
                    sideEffectMutable.emit(CalendarSideEffect.NavigateToTodoAdd(selectedDateState.value))
                }
            }

            CalendarAction.OnSyncClick -> Unit
            CalendarAction.OnScreenStarted -> Unit
        }
    }

    init {
        refreshAssignedTodosQuietly()
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

    private fun toggleTodoDone(todoId: Long, assignedTodoId: String?) {
        viewModelScope.launch {
            if (assignedTodoId != null) {
                val target = receivedAssignedTodos.value.firstOrNull { it.id == assignedTodoId }
                if (target?.isDone == true) {
                    manageAssignedTodoUseCase.reopen(assignedTodoId)
                        .onFailure { refreshAssignedTodosQuietly() }
                } else {
                    manageAssignedTodoUseCase.complete(assignedTodoId)
                        .onFailure { refreshAssignedTodosQuietly() }
                }
            } else {
                toggleTodoDoneUseCase(todoId)
                    .onSuccess { calendarWidgetUpdater.updateCalendarWidgets() }
            }
        }
    }

    private fun refreshAssignedTodosQuietly() {
        viewModelScope.launch {
            refreshAssignedTodos()
        }
    }

    private suspend fun refreshAssignedTodos(): Result<Unit> =
        getAssignedTodosUseCase.visibleReceived()
            .map { Unit }
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

internal fun AssignedTodo.toSelectedTodoUiModel(): CalendarSelectedTodoUiModel {
    val reminderEpochMillis = reminder
        ?.takeIf { it.enabled }
        ?.reminderAt
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    return CalendarSelectedTodoUiModel(
        id = stableAssignedRowId(id),
        title = title,
        isDone = isDone,
        priority = priority,
        isReminderEnabled = reminder?.enabled == true,
        dueTimeLabel = dueTimeMinutes?.let(::formatLocalTimeFromMinutes),
        reminderLeadMinutes = reminderLeadMinutes(reminderEpochMillis),
        sourceLabel = sender?.nickname?.let { "@$it" },
        assignedTodoId = id
    )
}

internal fun Map<LocalDate, DateTodoSummary>.withAssignedTodos(
    yearMonth: YearMonth,
    assignedTodos: List<AssignedTodo>
): Map<LocalDate, DateTodoSummary> {
    val mutable = toMutableMap()
    assignedTodos
        .filter { it.dueDate != null && YearMonth.from(it.dueDate) == yearMonth }
        .groupBy { checkNotNull(it.dueDate) }
        .forEach { (date, dateAssignedTodos) ->
            val existing = mutable[date]
            val assignedSummaries = dateAssignedTodos.map {
                TodoSummary(
                    id = stableAssignedRowId(it.id),
                    title = it.title,
                    isDone = it.isDone,
                    dueTimeMinutes = it.dueTimeMinutes,
                    priority = it.priority
                )
            }
            val todos = (existing?.todos.orEmpty() + assignedSummaries)
            val indicatorCount = min(todos.size, 3)
            mutable[date] = DateTodoSummary(
                date = date,
                todos = todos,
                indicatorCount = indicatorCount,
                overflowCount = kotlin.math.max(todos.size - indicatorCount, 0)
            )
        }
    return mutable
}

private fun AssignedTodo.reminderLeadMinutes(reminderEpochMillis: Long?): Int? {
    val dueDate = dueDate ?: return null
    val dueTimeMinutes = dueTimeMinutes ?: return null
    val reminderMillis = reminderEpochMillis ?: return null
    val dueMillis = dueDate
        .atTime(LocalTime.of(dueTimeMinutes / 60, dueTimeMinutes % 60))
        .atZone(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val leadMinutes = ((dueMillis - reminderMillis) / 60_000L).toInt()
    return leadMinutes.takeIf { it in setOf(0, 5, 10, 30, 60) }
}

internal fun TodoPriority.sortRank(): Int = when (this) {
    TodoPriority.HIGH -> 3
    TodoPriority.MEDIUM -> 2
    TodoPriority.LOW -> 1
}

private fun stableAssignedRowId(id: String): Long {
    val positiveHash = id.hashCode().toLong().let { if (it == Long.MIN_VALUE) 0 else kotlin.math.abs(it) }
    return -positiveHash - 1
}

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
