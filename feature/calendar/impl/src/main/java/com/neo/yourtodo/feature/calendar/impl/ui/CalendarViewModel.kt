package com.neo.yourtodo.feature.calendar.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.usecase.BuildTaskSurfaceDateTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ManageAssignedTodoUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveCalendarMonthExpandedUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveMonthlyTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveObservedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveTaskSurfaceSummariesUseCase
import com.neo.yourtodo.core.domain.usecase.SyncTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ToggleTodoDoneUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateCalendarMonthExpandedUseCase
import com.neo.yourtodo.core.domain.usecase.WorkspaceSyncNotifier
import com.neo.yourtodo.feature.calendar.impl.di.CalendarZoneId
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
import java.time.ZoneId

private const val STATE_MONTH_KEY = "calendar_month"
private const val STATE_SELECTED_DATE_KEY = "calendar_selected_date"
private const val STATE_IS_MONTH_EXPANDED_KEY = "calendar_is_month_expanded"
private const val STATE_IS_FRIEND_TODOS_EXPANDED_KEY = "calendar_is_friend_todos_expanded"

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    observeCalendarMonthExpandedUseCase: ObserveCalendarMonthExpandedUseCase,
    observeTaskSurfaceSummariesUseCase: ObserveTaskSurfaceSummariesUseCase,
    observeMonthlyTodosUseCase: ObserveMonthlyTodosUseCase,
    observeObservedTodosUseCase: ObserveObservedTodosUseCase,
    @CalendarZoneId private val zoneId: ZoneId,
    private val buildTaskSurfaceDateTodosUseCase: BuildTaskSurfaceDateTodosUseCase,
    private val toggleTodoDoneUseCase: ToggleTodoDoneUseCase,
    private val syncTodosUseCase: SyncTodosUseCase,
    private val updateCalendarMonthExpandedUseCase: UpdateCalendarMonthExpandedUseCase,
    private val getAssignedTodosUseCase: GetAssignedTodosUseCase,
    private val manageAssignedTodoUseCase: ManageAssignedTodoUseCase,
    private val calendarWidgetUpdater: CalendarWidgetUpdater,
    private val workspaceSyncNotifier: WorkspaceSyncNotifier = WorkspaceSyncNotifier()
) : ViewModel() {
    private val monthState = MutableStateFlow(savedStateHandle.initialMonth())
    private val selectedDateState = MutableStateFlow(savedStateHandle.initialSelectedDate())
    private val isMonthExpandedState = MutableStateFlow(savedStateHandle.initialIsMonthExpanded())
    private val isFriendTodosExpandedState =
        MutableStateFlow(savedStateHandle.initialIsFriendTodosExpanded())
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

    private val observedPeople = observeObservedTodosUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val taskSurfaceSummariesByDate = monthState
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

    private val summariesByDate = combine(
        monthState,
        taskSurfaceSummariesByDate,
        observedPeople
    ) { yearMonth, summaries, observed ->
        mergeObservedTodoSummaries(
            yearMonth = yearMonth,
            localSummaries = summaries,
            observedPeople = observed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyMap()
    )

    private val selectedDateTodos = combine(
        selectedDateState,
        monthlyTodos,
        receivedAssignedTodos,
        observedPeople
    ) { selectedDate, todos, assignedTodos, observed ->
        val myTodos = buildSelectedDateTodos(
            taskSurfaceItems = buildTaskSurfaceDateTodosUseCase(
                selectedDate = selectedDate,
                localTodos = todos,
                assignedTodos = assignedTodos,
                zoneId = zoneId
            ),
            zoneId = zoneId
        )
        myTodos + buildObservedSelectedDateTodos(
            observedPeople = observed,
            selectedDate = selectedDate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val calendarPresentationState = combine(
        selectedDateTodos,
        isMonthExpandedState,
        isFriendTodosExpandedState
    ) { dateTodos, isMonthExpanded, isFriendTodosExpanded ->
        CalendarPresentationState(
            selectedDateTodos = dateTodos,
            isMonthExpanded = isMonthExpanded,
            isFriendTodosExpanded = isFriendTodosExpanded
        )
    }

    private val calendarContentState = combine(
        monthState,
        selectedDateState,
        summariesByDate,
        calendarPresentationState,
        observeAuthSessionUseCase()
    ) { currentMonth, selectedDate, summaries, presentation, authSession ->
        buildCalendarUiState(
            profileInitial = authSession?.user?.nickname,
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            summariesByDate = summaries,
            selectedDateTodos = presentation.selectedDateTodos,
            isMonthExpanded = presentation.isMonthExpanded,
            isFriendTodosExpanded = presentation.isFriendTodosExpanded
        )
    }

    val uiState: StateFlow<CalendarUiState> = calendarContentState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = initialCalendarUiState(
            currentMonth = monthState.value,
            selectedDate = selectedDateState.value,
            isMonthExpanded = isMonthExpandedState.value
        )
    )

    fun onAction(action: CalendarAction) {
        when (action) {
            CalendarAction.OnNextMonthClick -> moveMonthBy(1)
            CalendarAction.OnPreviousMonthClick -> moveMonthBy(-1)
            CalendarAction.OnToggleMonthExpansion -> toggleMonthExpansion()
            CalendarAction.OnToggleFriendTodosExpanded -> toggleFriendTodosExpanded()
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
        viewModelScope.launch {
            observeCalendarMonthExpandedUseCase().collect { isExpanded ->
                savedStateHandle[STATE_IS_MONTH_EXPANDED_KEY] = isExpanded
                isMonthExpandedState.value = isExpanded
            }
        }
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

    private fun toggleMonthExpansion() {
        val nextValue = !isMonthExpandedState.value
        savedStateHandle[STATE_IS_MONTH_EXPANDED_KEY] = nextValue
        isMonthExpandedState.value = nextValue
        viewModelScope.launch {
            updateCalendarMonthExpandedUseCase(nextValue)
        }
    }

    private fun toggleFriendTodosExpanded() {
        val nextValue = !isFriendTodosExpandedState.value
        savedStateHandle[STATE_IS_FRIEND_TODOS_EXPANDED_KEY] = nextValue
        isFriendTodosExpandedState.value = nextValue
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
                    .onSuccess {
                        calendarWidgetUpdater.updateCalendarWidgets()
                        syncTodosUseCase()
                    }
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

internal fun SavedStateHandle.initialIsMonthExpanded(): Boolean =
    get<Boolean>(STATE_IS_MONTH_EXPANDED_KEY) ?: true

internal fun SavedStateHandle.initialIsFriendTodosExpanded(): Boolean =
    get<Boolean>(STATE_IS_FRIEND_TODOS_EXPANDED_KEY) ?: false

private data class CalendarPresentationState(
    val selectedDateTodos: List<CalendarSelectedTodoUiModel>,
    val isMonthExpanded: Boolean,
    val isFriendTodosExpanded: Boolean
)
