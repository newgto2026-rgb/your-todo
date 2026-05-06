package com.example.myfirstapp.feature.todo.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.myfirstapp.core.model.TodoPriorityFilter
import com.example.myfirstapp.feature.todo.impl.R
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
    observeSelectedTodoFilterUseCase: ObserveSelectedTodoFilterUseCase,
    observeSelectedTodoPriorityFilterUseCase: ObserveSelectedTodoPriorityFilterUseCase,
    private val addTodoUseCase: AddTodoUseCase,
    private val updateTodoUseCase: UpdateTodoUseCase,
    private val deleteTodoUseCase: DeleteTodoUseCase,
    private val toggleTodoDoneUseCase: ToggleTodoDoneUseCase,
    private val updateSelectedTodoFilterUseCase: UpdateSelectedTodoFilterUseCase,
    private val updateSelectedTodoPriorityFilterUseCase: UpdateSelectedTodoPriorityFilterUseCase,
    private val getTodoUseCase: GetTodoUseCase,
    private val todoReminderScheduler: TodoReminderScheduler
) : ViewModel() {

    private val uiLocalState = MutableStateFlow(TodoListUiState(isLoading = true))
    private val sideEffectMutable = MutableSharedFlow<TodoListSideEffect>()

    val sideEffect = sideEffectMutable.asSharedFlow()

    val uiState: StateFlow<TodoListUiState> = combine(
        observeTodosUseCase(),
        observeSelectedTodoFilterUseCase(),
        observeSelectedTodoPriorityFilterUseCase(),
        uiLocalState
    ) { items, selectedFilter, selectedPriorityFilter, localState ->
        buildTodoListUiState(
            localState = localState,
            items = items,
            selectedFilter = selectedFilter,
            selectedPriorityFilter = selectedPriorityFilter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TodoListUiState(isLoading = true)
    )

    fun onAction(action: TodoListAction) {
        when (action) {
            TodoListAction.OnAddClick -> updateLocalState { openNewTodoEditor() }
            is TodoListAction.OnAddForDateClick -> {
                updateLocalState { openNewTodoEditorForDate(action.dueDate) }
            }

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
            is TodoListAction.OnDeleteClick -> deleteTodo(action.id)
            is TodoListAction.OnFilterChange -> updateFilter(action.filter)
            is TodoListAction.OnPriorityFilterChange -> updatePriorityFilter(action.filter)
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
                uiLocalState.value = current.dismissTodoEditor()
            } else {
                sideEffectMutable.emit(TodoListSideEffect.ShowSnackbar(R.string.todo_error_save_failed))
            }
        }
    }

    private fun toggleDone(id: Long) {
        viewModelScope.launch {
            toggleTodoDoneUseCase(id)
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

    private fun deleteTodo(id: Long) {
        viewModelScope.launch {
            val previous = getTodoUseCase(id)
            deleteTodoUseCase(id)
                .onSuccess {
                    todoReminderScheduler.cancel(id)
                    previous?.let {
                        uiLocalState.value = uiLocalState.value.copy(pendingUndoTodo = it)
                        sideEffectMutable.emit(
                            TodoListSideEffect.ShowSnackbar(
                                messageRes = R.string.todo_action_deleted,
                                actionLabelRes = R.string.todo_action_undo
                            )
                        )
                    }
                }
                .onFailure {
                    sideEffectMutable.emit(
                        TodoListSideEffect.ShowSnackbar(R.string.todo_error_delete_failed)
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
                sideEffectMutable.emit(
                    TodoListSideEffect.ShowSnackbar(
                        messageRes = R.string.todo_action_moved_to_tomorrow,
                        actionLabelRes = R.string.todo_action_undo
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
                sideEffectMutable.emit(
                    TodoListSideEffect.ShowSnackbar(
                        messageRes = R.string.todo_action_schedule_cleared,
                        actionLabelRes = R.string.todo_action_undo
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
                    sideEffectMutable.emit(TodoListSideEffect.ShowSnackbar(R.string.todo_action_restored))
                }
                .onFailure {
                    sideEffectMutable.emit(TodoListSideEffect.ShowSnackbar(R.string.todo_error_undo_failed))
                }
        }
    }

    private fun updateFilter(filter: TodoFilter) {
        viewModelScope.launch {
            updateSelectedTodoFilterUseCase(filter)
                .onFailure {
                    sideEffectMutable.emit(
                        TodoListSideEffect.ShowSnackbar(R.string.todo_error_filter_change_failed)
                    )
                }
        }
    }

    private fun updatePriorityFilter(filter: TodoPriorityFilter) {
        viewModelScope.launch {
            updateSelectedTodoPriorityFilterUseCase(filter)
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
