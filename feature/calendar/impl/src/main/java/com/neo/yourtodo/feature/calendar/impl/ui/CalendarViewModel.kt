package com.neo.yourtodo.feature.calendar.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.usecase.BuildTaskSurfaceDateTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ManageAssignedTodoUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveTaskSurfaceSummariesUseCase
import com.neo.yourtodo.core.domain.usecase.ToggleTodoDoneUseCase
import com.neo.yourtodo.core.domain.usecase.WorkspaceSyncNotifier
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
import java.time.LocalDate
import java.time.YearMonth

private const val STATE_MONTH_KEY = "calendar_month"
private const val STATE_SELECTED_DATE_KEY = "calendar_selected_date"

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    observeTaskSurfaceSummariesUseCase: ObserveTaskSurfaceSummariesUseCase,
    observeMonthlyTodosUseCase: ObserveMonthlyTodosUseCase,
    private val buildTaskSurfaceDateTodosUseCase: BuildTaskSurfaceDateTodosUseCase,
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
        .flatMapLatest { yearMonth ->
            observeTaskSurfaceSummariesUseCase(
                yearMonth = yearMonth,
                assignedTodos = receivedAssignedTodos
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    private val selectedDateTodos = combine(
        selectedDateState,
        monthlyTodos,
        receivedAssignedTodos
    ) { selectedDate, todos, assignedTodos ->
        buildSelectedDateTodos(
            taskSurfaceItems = buildTaskSurfaceDateTodosUseCase(
                selectedDate = selectedDate,
                localTodos = todos,
                assignedTodos = assignedTodos
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val calendarContentState = combine(
        monthState,
        selectedDateState,
        summariesByDate,
        selectedDateTodos,
        observeAuthSessionUseCase()
    ) { currentMonth, selectedDate, summaries, dateTodos, authSession ->
        buildCalendarUiState(
            profileInitial = authSession?.user?.nickname,
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            summariesByDate = summaries,
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
