package com.neo.yourtodo.feature.todo.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.scheduler.TodoReminderScheduler
import com.neo.yourtodo.core.domain.usecase.AddTodoUseCase
import com.neo.yourtodo.core.domain.usecase.DeleteTodoUseCase
import com.neo.yourtodo.core.domain.usecase.GetTodoUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveSelectedTodoPriorityFilterUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ToggleTodoDoneUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateSelectedTodoPriorityFilterUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateTodoUseCase
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.feature.todo.impl.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TodoListViewModel @Inject constructor(
    observeTodosUseCase: ObserveTodosUseCase,
    observeSelectedTodoPriorityFilterUseCase: ObserveSelectedTodoPriorityFilterUseCase,
    private val addTodoUseCase: AddTodoUseCase,
    private val updateTodoUseCase: UpdateTodoUseCase,
    private val deleteTodoUseCase: DeleteTodoUseCase,
    private val toggleTodoDoneUseCase: ToggleTodoDoneUseCase,
    private val updateSelectedTodoPriorityFilterUseCase: UpdateSelectedTodoPriorityFilterUseCase,
    private val getTodoUseCase: GetTodoUseCase,
    private val todoReminderScheduler: TodoReminderScheduler,
    private val calendarWidgetUpdater: CalendarWidgetUpdater
) : ViewModel() {

    private val uiLocalState = MutableStateFlow(TodoListUiState(isLoading = true))
    private val sideEffectMutable = MutableSharedFlow<TodoListSideEffect>()
    private val todoItems = observeTodosUseCase()
    private val selectedPriorityFilter = observeSelectedTodoPriorityFilterUseCase()
    private val localPriorityFilter = MutableStateFlow(TodoPriorityFilter.ALL)

    val sideEffect = sideEffectMutable.asSharedFlow()

    val uiState: StateFlow<TodoListUiState> = combine(
        todoItems,
        selectedPriorityFilter,
        localPriorityFilter,
        uiLocalState
    ) { items, _, localPriorityFilter, localState ->
        buildTodoListUiState(
            localState = localState,
            items = items,
            selectedFilter = localState.selectedFilter,
            selectedPriorityFilter = localPriorityFilter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TodoListUiState(isLoading = true)
    )

    fun setRouteFilter(filter: TodoFilter) {
        if (uiLocalState.value.selectedFilter != filter) {
            updateLocalState { copy(selectedFilter = filter) }
        }
    }

    fun onAction(action: TodoListAction) {
        when (action) {
            TodoListAction.OnAddClick -> updateLocalState { openNewTodoEditor() }
            is TodoListAction.OnAddForDateClick -> {
                updateLocalState { openNewTodoEditorForDate(action.dueDate) }
            }

            TodoListAction.OnQuickAddClick -> openQuickAdd()
            is TodoListAction.OnQuickAddTitleChange -> {
                updateLocalState {
                    copy(
                        quickAddTitle = action.value,
                        quickAddErrorMessageRes = null
                    )
                }
            }

            TodoListAction.OnQuickAddSubmit -> saveQuickAdd()
            TodoListAction.OnQuickAddDismiss -> updateLocalState { dismissQuickAdd() }
            is TodoListAction.OnTitleChange -> {
                updateLocalState { copy(draftTitle = action.value) }
            }

            is TodoListAction.OnDueDateInputChange -> {
                updateLocalState { copy(draftDueDateInput = action.value) }
            }

            is TodoListAction.OnDueTimeInputChange -> {
                updateLocalState { copy(draftDueTimeInput = action.value) }
            }

            is TodoListAction.OnReminderEnabledChange -> {
                updateLocalState { copy(draftReminderEnabled = action.value) }
            }

            is TodoListAction.OnReminderLeadMinutesChange -> {
                updateLocalState { copy(draftReminderLeadMinutes = action.value) }
            }

            is TodoListAction.OnReminderRepeatTypeChange -> {
                updateLocalState { copy(draftReminderRepeatType = action.value.normalizeRepeatType()) }
            }

            is TodoListAction.OnPrioritySelectedInEditor -> {
                updateLocalState { copy(draftPriority = action.priority) }
            }

            TodoListAction.OnSaveClick -> saveTodo()
            is TodoListAction.OnToggleDone -> toggleDone(action.id)
            is TodoListAction.OnMoveToTomorrow -> moveToTomorrow(action.id)
            is TodoListAction.OnClearSchedule -> clearSchedule(action.id)
            TodoListAction.OnUndoLastQuickAction -> undoLastQuickAction()
            is TodoListAction.OnEditClick -> openEditDialog(action.id)
            is TodoListAction.OnDeleteRequest -> requestTodoDelete(action.id)
            TodoListAction.OnDeleteCancel -> updateLocalState { copy(deleteConfirmation = null) }
            TodoListAction.OnDeleteConfirm -> confirmDelete()
            TodoListAction.OnClearCompletedClick -> requestCompletedTodoDelete()
            is TodoListAction.OnFilterChange -> updateFilter(action.filter)
            is TodoListAction.OnPriorityFilterChange -> updatePriorityFilter(action.filter)
            is TodoListAction.OnSortOptionChange -> updateSortOption(action.option)

            TodoListAction.OnDismissDialog -> updateLocalState { dismissTodoEditor() }
        }
    }

    private fun saveTodo() {
        val current = uiLocalState.value
        val validation = validateTodoDraft(current)
        if (validation.errorMessageRes != null) {
            uiLocalState.value = current.copy(errorMessageRes = validation.errorMessageRes)
            return
        }

        viewModelScope.launch {
            val result: Result<Long> = if (current.editingItem?.id != null) {
                updateTodoUseCase(
                    id = current.editingItem.id,
                    title = checkNotNull(validation.normalizedTitle),
                    dueDate = validation.parsedDueDate,
                    categoryId = null,
                    dueTimeMinutes = validation.parsedDueTimeMinutes,
                    reminderAtEpochMillis = if (current.draftReminderEnabled) validation.reminderAtEpochMillis else null,
                    isReminderEnabled = current.draftReminderEnabled,
                    reminderRepeatType = ReminderRepeatType.NONE,
                    reminderRepeatDaysMask = 0,
                    reminderLeadMinutes = if (current.draftReminderEnabled) current.draftReminderLeadMinutes else null,
                    priority = current.draftPriority
                ).map { current.editingItem.id }
            } else {
                addTodoUseCase(
                    title = checkNotNull(validation.normalizedTitle),
                    dueDate = validation.parsedDueDate,
                    categoryId = null,
                    dueTimeMinutes = validation.parsedDueTimeMinutes,
                    reminderAtEpochMillis = if (current.draftReminderEnabled) validation.reminderAtEpochMillis else null,
                    isReminderEnabled = current.draftReminderEnabled,
                    reminderRepeatType = ReminderRepeatType.NONE,
                    reminderRepeatDaysMask = 0,
                    reminderLeadMinutes = if (current.draftReminderEnabled) current.draftReminderLeadMinutes else null,
                    priority = current.draftPriority
                )
            }

            if (result.isSuccess) {
                result.getOrNull()?.let { syncTodoReminder(it) }
                notifyCalendarWidgetChanged()
                uiLocalState.value = current.dismissTodoEditor()
            } else {
                sideEffectMutable.emit(TodoListSideEffect.ShowSnackbar(R.string.todo_error_save_failed))
            }
        }
    }

    private fun openQuickAdd() {
        val current = uiLocalState.value
        if (current.selectedFilter == TodoFilter.COMPLETED) {
            showCompletedQuickAddGuidance()
            return
        }
        updateLocalState {
            copy(
                isQuickAddVisible = true,
                quickAddErrorMessageRes = null,
                deleteConfirmation = null
            )
        }
    }

    private fun saveQuickAdd() {
        val current = uiLocalState.value
        val normalizedTitle = current.quickAddTitle.trim()
        if (normalizedTitle.isBlank()) {
            updateLocalState { copy(quickAddErrorMessageRes = R.string.todo_error_title_required) }
            return
        }

        val dueDate = if (current.selectedFilter == TodoFilter.TODAY) {
            LocalDate.now()
        } else {
            null
        }
        val priority = when (uiState.value.selectedPriorityFilter) {
            TodoPriorityFilter.LOW -> com.neo.yourtodo.core.model.TodoPriority.LOW
            TodoPriorityFilter.MEDIUM -> com.neo.yourtodo.core.model.TodoPriority.MEDIUM
            TodoPriorityFilter.HIGH -> com.neo.yourtodo.core.model.TodoPriority.HIGH
            TodoPriorityFilter.ALL -> com.neo.yourtodo.core.model.TodoPriority.MEDIUM
        }

        viewModelScope.launch {
            addTodoUseCase(
                title = normalizedTitle,
                dueDate = dueDate,
                categoryId = null,
                dueTimeMinutes = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                reminderLeadMinutes = null,
                priority = priority
            ).onSuccess {
                notifyCalendarWidgetChanged()
                updateLocalState {
                    copy(
                        isQuickAddVisible = true,
                        quickAddTitle = "",
                        quickAddErrorMessageRes = null
                    )
                }
            }.onFailure {
                sideEffectMutable.emit(TodoListSideEffect.ShowSnackbar(R.string.todo_error_save_failed))
            }
        }
    }

    private fun showCompletedQuickAddGuidance() {
        viewModelScope.launch {
            sideEffectMutable.emit(
                TodoListSideEffect.ShowSnackbar(
                    messageRes = R.string.todo_quick_add_completed_unavailable
                )
            )
        }
    }

    private fun toggleDone(id: Long) {
        viewModelScope.launch {
            toggleTodoDoneUseCase(id)
                .onSuccess {
                    notifyCalendarWidgetChanged()
                }
                .onFailure {
                    sideEffectMutable.emit(
                        TodoListSideEffect.ShowSnackbar(R.string.todo_error_toggle_done_failed)
                    )
                }
        }
    }

    private fun openEditDialog(id: Long) {
        val target = uiState.value.items.firstOrNull { it.id == id } ?: return
        uiLocalState.value = uiLocalState.value.copy(
            isEditDialogVisible = true,
            editingItem = target.toTodoEditModel(),
            draftTitle = target.title,
            draftDueDateInput = target.dueDateText.orEmpty(),
            draftDueTimeInput = target.dueTimeText.orEmpty(),
            draftReminderEnabled = target.isReminderEnabled,
            draftReminderLeadMinutes = target.reminderLeadMinutes ?: DEFAULT_REMINDER_LEAD_MINUTES,
            draftReminderRepeatType = ReminderRepeatType.NONE,
            draftPriority = target.priority,
            errorMessageRes = null
        )
    }

    private fun requestTodoDelete(id: Long) {
        updateLocalState { copy(deleteConfirmation = TodoDeleteConfirmation.Single(id)) }
    }

    private fun requestCompletedTodoDelete() {
        val completedIds = uiState.value.completedTodoIds
        if (completedIds.isEmpty()) return
        updateLocalState {
            copy(deleteConfirmation = TodoDeleteConfirmation.Completed(completedIds))
        }
    }

    private fun confirmDelete() {
        val confirmation = uiLocalState.value.deleteConfirmation ?: return
        viewModelScope.launch {
            val previousSingle = (confirmation as? TodoDeleteConfirmation.Single)
                ?.let { getTodoUseCase(it.id) }
            val deletedIds = mutableSetOf<Long>()
            var hasFailure = false
            confirmation.ids.forEach { id ->
                deleteTodoUseCase(id)
                    .onSuccess {
                        deletedIds += id
                        todoReminderScheduler.cancel(id)
                    }
                    .onFailure {
                        hasFailure = true
                    }
            }

            updateLocalState {
                val shouldDismissEditor = editingItem?.id?.let { it in deletedIds } == true
                val clearedState = if (shouldDismissEditor) dismissTodoEditor() else this
                clearedState.copy(deleteConfirmation = null)
            }

            if (deletedIds.isNotEmpty()) {
                notifyCalendarWidgetChanged()
            }

            if (hasFailure) {
                sideEffectMutable.emit(
                    TodoListSideEffect.ShowSnackbar(R.string.todo_error_delete_failed)
                )
            }

            if (!hasFailure && previousSingle != null) {
                uiLocalState.value = uiLocalState.value.copy(pendingUndoTodo = previousSingle)
                sideEffectMutable.emit(
                    TodoListSideEffect.ShowSnackbar(
                        messageRes = R.string.todo_action_deleted,
                        actionLabelRes = R.string.todo_action_undo,
                        action = TodoListSnackbarAction.UndoLastQuickAction
                    )
                )
            }
        }
    }

    private fun moveToTomorrow(id: Long) {
        viewModelScope.launch {
            val previous = getTodoUseCase(id) ?: return@launch
            val tomorrow = LocalDate.now().plusDays(1)
            val reminderAtEpochMillis = previous.reminderEpochMillisFor(tomorrow)
            updateTodoUseCase(
                id = previous.id,
                title = previous.title,
                dueDate = tomorrow,
                categoryId = previous.categoryId,
                dueTimeMinutes = previous.dueTimeMinutes,
                reminderAtEpochMillis = reminderAtEpochMillis,
                isReminderEnabled = previous.isReminderEnabled && reminderAtEpochMillis != null,
                reminderRepeatType = previous.reminderRepeatType.normalizeRepeatType(),
                reminderRepeatDaysMask = previous.reminderRepeatDaysMask,
                reminderLeadMinutes = if (reminderAtEpochMillis != null) previous.reminderLeadMinutes else null,
                priority = previous.priority
            ).onSuccess {
                uiLocalState.value = uiLocalState.value.copy(pendingUndoTodo = previous)
                syncTodoReminder(id)
                notifyCalendarWidgetChanged()
                sideEffectMutable.emit(
                    TodoListSideEffect.ShowSnackbar(
                        messageRes = R.string.todo_action_moved_to_tomorrow,
                        actionLabelRes = R.string.todo_action_undo,
                        action = TodoListSnackbarAction.UndoLastQuickAction
                    )
                )
            }.onFailure {
                sideEffectMutable.emit(
                    TodoListSideEffect.ShowSnackbar(R.string.todo_error_reschedule_failed)
                )
            }
        }
    }

    private fun clearSchedule(id: Long) {
        viewModelScope.launch {
            val previous = getTodoUseCase(id) ?: return@launch
            updateTodoUseCase(
                id = previous.id,
                title = previous.title,
                dueDate = null,
                categoryId = previous.categoryId,
                dueTimeMinutes = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                reminderLeadMinutes = null,
                priority = previous.priority
            ).onSuccess {
                uiLocalState.value = uiLocalState.value.copy(pendingUndoTodo = previous)
                todoReminderScheduler.cancel(id)
                notifyCalendarWidgetChanged()
                sideEffectMutable.emit(
                    TodoListSideEffect.ShowSnackbar(
                        messageRes = R.string.todo_action_schedule_cleared,
                        actionLabelRes = R.string.todo_action_undo,
                        action = TodoListSnackbarAction.UndoLastQuickAction
                    )
                )
            }.onFailure {
                sideEffectMutable.emit(
                    TodoListSideEffect.ShowSnackbar(R.string.todo_error_reschedule_failed)
                )
            }
        }
    }

    private fun undoLastQuickAction() {
        viewModelScope.launch {
            val previous = uiLocalState.value.pendingUndoTodo ?: return@launch
            val result = if (getTodoUseCase(previous.id) == null) {
                addTodoUseCase(
                    title = previous.title,
                    dueDate = previous.dueDate,
                    categoryId = previous.categoryId,
                    dueTimeMinutes = previous.dueTimeMinutes,
                    reminderAtEpochMillis = previous.reminderAtEpochMillis,
                    isReminderEnabled = previous.isReminderEnabled,
                    reminderRepeatType = previous.reminderRepeatType.normalizeRepeatType(),
                    reminderRepeatDaysMask = previous.reminderRepeatDaysMask,
                    reminderLeadMinutes = previous.reminderLeadMinutes,
                    priority = previous.priority
                ).map { newId ->
                    if (previous.isDone) {
                        toggleTodoDoneUseCase(newId)
                    }
                    getTodoUseCase(newId)?.let { restored ->
                        if (restored.isReminderEnabled && restored.reminderAtEpochMillis != null) {
                            todoReminderScheduler.schedule(restored)
                        }
                    }
                }
            } else {
                restoreTodo(previous)
            }

            result
                .onSuccess {
                    uiLocalState.value = uiLocalState.value.copy(pendingUndoTodo = null)
                    syncTodoReminder(previous.id)
                    notifyCalendarWidgetChanged()
                    sideEffectMutable.emit(TodoListSideEffect.ShowSnackbar(R.string.todo_action_restored))
                }
                .onFailure {
                    sideEffectMutable.emit(TodoListSideEffect.ShowSnackbar(R.string.todo_error_undo_failed))
                }
        }
    }

    private fun updateFilter(filter: TodoFilter) {
        updateLocalState { copy(selectedFilter = filter) }
    }

    private fun updatePriorityFilter(filter: TodoPriorityFilter) {
        val previousFilter = uiState.value.selectedPriorityFilter
        localPriorityFilter.value = filter
        viewModelScope.launch {
            updateSelectedTodoPriorityFilterUseCase(filter)
                .onFailure {
                    localPriorityFilter.value = previousFilter
                    sideEffectMutable.emit(
                        TodoListSideEffect.ShowSnackbar(
                            R.string.todo_error_priority_filter_change_failed
                        )
                    )
                }
        }
    }

    private fun updateSortOption(option: TodoSortOption) {
        if (uiState.value.selectedSortOption == option) return

        localPriorityFilter.value = TodoPriorityFilter.ALL
        updateLocalState { copy(selectedSortOption = option) }
        viewModelScope.launch {
            updateSelectedTodoPriorityFilterUseCase(TodoPriorityFilter.ALL)
                .onFailure {
                    sideEffectMutable.emit(
                        TodoListSideEffect.ShowSnackbar(
                            R.string.todo_error_priority_filter_change_failed
                        )
                    )
                }
        }
    }

    private suspend fun syncTodoReminder(todoId: Long) {
        val todo = getTodoUseCase(todoId)
        if (todo != null && todo.isReminderEnabled && todo.reminderAtEpochMillis != null) {
            todoReminderScheduler.schedule(todo)
        } else {
            todoReminderScheduler.cancel(todoId)
        }
    }

    private suspend fun notifyCalendarWidgetChanged() {
        calendarWidgetUpdater.updateCalendarWidgets()
    }

    private inline fun updateLocalState(block: TodoListUiState.() -> TodoListUiState) {
        uiLocalState.value = uiLocalState.value.block()
    }

    private suspend fun restoreTodo(todo: TodoItem): Result<Unit> =
        updateTodoUseCase(
            id = todo.id,
            title = todo.title,
            dueDate = todo.dueDate,
            categoryId = todo.categoryId,
            dueTimeMinutes = todo.dueTimeMinutes,
            reminderAtEpochMillis = todo.reminderAtEpochMillis,
            isReminderEnabled = todo.isReminderEnabled,
            reminderRepeatType = todo.reminderRepeatType.normalizeRepeatType(),
            reminderRepeatDaysMask = todo.reminderRepeatDaysMask,
            reminderLeadMinutes = todo.reminderLeadMinutes,
            priority = todo.priority
        )

    private fun TodoItem.reminderEpochMillisFor(dueDate: LocalDate): Long? {
        if (!isReminderEnabled) return null
        val dueTime = dueTimeMinutes ?: return null
        val leadMinutes = reminderLeadMinutes ?: DEFAULT_REMINDER_LEAD_MINUTES
        val reminderAt = dueDateTimeToEpochMillis(dueDate, dueTime) - leadMinutes * 60_000L
        return reminderAt.takeIf { it > System.currentTimeMillis() }
    }
}
