package com.neo.yourtodo.feature.todo.impl.ui.editor

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.scheduler.TodoReminderScheduler
import com.neo.yourtodo.core.domain.usecase.AddTodoUseCase
import com.neo.yourtodo.core.domain.usecase.DeleteTodoUseCase
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.GetTodoUseCase
import com.neo.yourtodo.core.domain.usecase.ManageAssignedTodoUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateTodoUseCase
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.ui.DEFAULT_REMINDER_LEAD_MINUTES
import com.neo.yourtodo.feature.todo.impl.ui.dueDateTimeToEpochMillis
import com.neo.yourtodo.feature.todo.impl.ui.dueTimeTextToMinutes
import com.neo.yourtodo.feature.todo.impl.ui.minutesToDueTimeText
import com.neo.yourtodo.feature.todo.impl.ui.normalizeRepeatType
import com.neo.yourtodo.feature.todo.impl.ui.parseIsoDateInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@HiltViewModel
class TodoEditorViewModel @Inject constructor(
    private val addTodoUseCase: AddTodoUseCase,
    private val updateTodoUseCase: UpdateTodoUseCase,
    private val deleteTodoUseCase: DeleteTodoUseCase,
    private val getTodoUseCase: GetTodoUseCase,
    private val getAssignedTodosUseCase: GetAssignedTodosUseCase,
    private val manageAssignedTodoUseCase: ManageAssignedTodoUseCase,
    private val todoReminderScheduler: TodoReminderScheduler,
    private val calendarWidgetUpdater: CalendarWidgetUpdater
) : ViewModel() {
    private val _uiState = MutableStateFlow(TodoEditorUiState())
    val uiState = _uiState

    private val sideEffects = MutableSharedFlow<TodoEditorSideEffect>()
    val sideEffect = sideEffects.asSharedFlow()

    fun initialize(todoId: Long?, assignedTodoId: String?, dueDate: String?) {
        if (_uiState.value.isInitialized) return
        if (assignedTodoId != null) {
            viewModelScope.launch {
                val assignedTodo = getAssignedTodosUseCase.visibleReceived()
                    .getOrNull()
                    ?.firstOrNull { it.id == assignedTodoId }
                if (assignedTodo == null) {
                    sideEffects.emit(TodoEditorSideEffect.Exit)
                    return@launch
                }
                _uiState.value = TodoEditorUiState.fromAssignedTodo(assignedTodo)
            }
            return
        }
        if (todoId != null) {
            viewModelScope.launch {
                val todo = getTodoUseCase(todoId)
                if (todo == null) {
                    sideEffects.emit(TodoEditorSideEffect.Exit)
                    return@launch
                }
                _uiState.value = TodoEditorUiState.fromTodo(todo)
            }
            return
        }
        _uiState.value = TodoEditorUiState(
            isInitialized = true,
            dueDateInput = dueDate.orEmpty()
        )
    }

    fun onTitleChange(value: String) =
        _uiState.update { if (it.isAssignedEdit) it else it.copy(title = value, errorMessageRes = null) }

    fun onDateInputChange(value: String) =
        _uiState.update { if (it.isAssignedEdit) it else it.copy(dueDateInput = value, errorMessageRes = null) }

    fun onDueTimeInputChange(value: String) =
        _uiState.update { if (it.isAssignedEdit) it else it.copy(dueTimeInput = value, errorMessageRes = null) }

    fun onReminderEnabledChange(value: Boolean) = _uiState.update { it.copy(reminderEnabled = value, errorMessageRes = null) }
    fun onReminderLeadMinutesChange(value: Int) = _uiState.update { it.copy(reminderLeadMinutes = value, errorMessageRes = null) }
    fun onPrioritySelected(priority: TodoPriority) =
        _uiState.update { if (it.isAssignedEdit) it else it.copy(priority = priority, errorMessageRes = null) }

    fun onDelete() {
        val id = _uiState.value.editingTodoId ?: return
        viewModelScope.launch {
            deleteTodoUseCase(id).onSuccess {
                todoReminderScheduler.cancel(id)
                notifyCalendarWidgetChanged()
                sideEffects.emit(TodoEditorSideEffect.Exit)
            }
        }
    }

    fun onSave() {
        val state = _uiState.value
        state.editingAssignedTodoId?.let { assignedTodoId ->
            saveAssignedReminder(state, assignedTodoId)
            return
        }
        val validation = validate(state)
        if (validation.errorMessageRes != null) {
            _uiState.update { it.copy(errorMessageRes = validation.errorMessageRes) }
            return
        }
        viewModelScope.launch {
            val result: Result<Long> = if (state.editingTodoId != null) {
                updateTodoUseCase(
                    id = state.editingTodoId,
                    title = checkNotNull(validation.normalizedTitle),
                    dueDate = validation.parsedDueDate,
                    categoryId = null,
                    dueTimeMinutes = validation.parsedDueTimeMinutes,
                    reminderAtEpochMillis = if (state.reminderEnabled) validation.reminderAtEpochMillis else null,
                    isReminderEnabled = state.reminderEnabled,
                    reminderRepeatType = ReminderRepeatType.NONE,
                    reminderRepeatDaysMask = 0,
                    reminderLeadMinutes = if (state.reminderEnabled) state.reminderLeadMinutes else null,
                    priority = state.priority
                ).map { state.editingTodoId }
            } else {
                addTodoUseCase(
                    title = checkNotNull(validation.normalizedTitle),
                    dueDate = validation.parsedDueDate,
                    categoryId = null,
                    dueTimeMinutes = validation.parsedDueTimeMinutes,
                    reminderAtEpochMillis = if (state.reminderEnabled) validation.reminderAtEpochMillis else null,
                    isReminderEnabled = state.reminderEnabled,
                    reminderRepeatType = ReminderRepeatType.NONE,
                    reminderRepeatDaysMask = 0,
                    reminderLeadMinutes = if (state.reminderEnabled) state.reminderLeadMinutes else null,
                    priority = state.priority
                )
            }
            result.onSuccess { id ->
                syncTodoReminder(id)
                notifyCalendarWidgetChanged()
                sideEffects.emit(TodoEditorSideEffect.Exit)
            }.onFailure {
                _uiState.update { current -> current.copy(errorMessageRes = R.string.todo_error_save_failed) }
            }
        }
    }

    private fun saveAssignedReminder(
        state: TodoEditorUiState,
        assignedTodoId: String
    ) {
        val validation = validate(state)
        if (state.reminderEnabled && validation.errorMessageRes != null) {
            _uiState.update { it.copy(errorMessageRes = validation.errorMessageRes) }
            return
        }
        viewModelScope.launch {
            val result = if (state.reminderEnabled) {
                manageAssignedTodoUseCase.upsertReminder(
                    assignedTodoId = assignedTodoId,
                    reminderAt = Instant.ofEpochMilli(checkNotNull(validation.reminderAtEpochMillis)).toString(),
                    enabled = true
                )
            } else {
                manageAssignedTodoUseCase.deleteReminder(assignedTodoId)
            }
            result.onSuccess {
                notifyCalendarWidgetChanged()
                sideEffects.emit(TodoEditorSideEffect.Exit)
            }.onFailure {
                _uiState.update { current -> current.copy(errorMessageRes = R.string.todo_error_save_failed) }
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

    private fun validate(state: TodoEditorUiState): TodoEditorValidationResult {
        val normalizedTitle = state.title.trim()
        if (normalizedTitle.isBlank()) {
            return TodoEditorValidationResult(errorMessageRes = R.string.todo_error_title_required)
        }
        val parsedDueDate = parseIsoDateInput(state.dueDateInput)
        if (state.dueDateInput.isNotBlank() && parsedDueDate == null) {
            return TodoEditorValidationResult(errorMessageRes = R.string.todo_error_due_date_format)
        }

        val parsedDueTimeMinutes = dueTimeTextToMinutes(state.dueTimeInput)
        if (state.dueTimeInput.isNotBlank() && parsedDueTimeMinutes == null) {
            return TodoEditorValidationResult(errorMessageRes = R.string.todo_error_due_time_format)
        }
        if (state.dueTimeInput.isNotBlank() && parsedDueDate == null) {
            return TodoEditorValidationResult(errorMessageRes = R.string.todo_error_due_time_requires_due_date)
        }
        if (state.reminderEnabled) {
            if (parsedDueDate == null) {
                return TodoEditorValidationResult(errorMessageRes = R.string.todo_error_reminder_due_date_required)
            }
            if (parsedDueTimeMinutes == null) {
                return TodoEditorValidationResult(errorMessageRes = R.string.todo_error_reminder_due_time_required)
            }
        }
        val reminderAtEpochMillis = if (state.reminderEnabled) {
            dueDateTimeToEpochMillis(
                dueDate = checkNotNull(parsedDueDate),
                dueTimeMinutes = checkNotNull(parsedDueTimeMinutes)
            ) - state.reminderLeadMinutes * 60_000L
        } else {
            null
        }
        if (state.reminderEnabled && reminderAtEpochMillis != null && reminderAtEpochMillis <= System.currentTimeMillis()) {
            return TodoEditorValidationResult(errorMessageRes = R.string.todo_error_reminder_time_in_past)
        }
        return TodoEditorValidationResult(
            normalizedTitle = normalizedTitle,
            parsedDueDate = parsedDueDate,
            parsedDueTimeMinutes = parsedDueTimeMinutes,
            reminderAtEpochMillis = reminderAtEpochMillis
        )
    }
}

data class TodoEditorUiState(
    val isInitialized: Boolean = false,
    val editingTodoId: Long? = null,
    val editingAssignedTodoId: String? = null,
    val title: String = "",
    val dueDateInput: String = "",
    val dueTimeInput: String = "",
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Int = DEFAULT_REMINDER_LEAD_MINUTES,
    val priority: TodoPriority = TodoPriority.MEDIUM,
    @StringRes val errorMessageRes: Int? = null
) {
    val isAssignedEdit: Boolean
        get() = editingAssignedTodoId != null
    val showDelete: Boolean
        get() = editingTodoId != null && !isAssignedEdit
    val sheetTitleRes: Int
        get() = when {
            isAssignedEdit -> R.string.todo_editor_title_received_task
            editingTodoId == null -> R.string.todo_editor_title_new_task
            else -> R.string.todo_editor_title_edit_task
        }

    companion object {
        fun fromTodo(todo: TodoItem): TodoEditorUiState = TodoEditorUiState(
            isInitialized = true,
            editingTodoId = todo.id,
            title = todo.title,
            dueDateInput = todo.dueDate?.toString().orEmpty(),
            dueTimeInput = todo.dueTimeMinutes?.let(::minutesToDueTimeText).orEmpty(),
            reminderEnabled = todo.isReminderEnabled,
            reminderLeadMinutes = todo.reminderLeadMinutes ?: DEFAULT_REMINDER_LEAD_MINUTES,
            priority = todo.priority
        )

        fun fromAssignedTodo(todo: AssignedTodo): TodoEditorUiState = TodoEditorUiState(
            isInitialized = true,
            editingAssignedTodoId = todo.id,
            title = todo.title,
            dueDateInput = todo.dueDate?.toString().orEmpty(),
            dueTimeInput = todo.dueTimeMinutes?.let(::minutesToDueTimeText).orEmpty(),
            reminderEnabled = todo.reminder?.enabled == true,
            reminderLeadMinutes = todo.assignedReminderLeadMinutes(),
            priority = todo.priority
        )
    }
}

data class TodoEditorValidationResult(
    val normalizedTitle: String? = null,
    val parsedDueDate: LocalDate? = null,
    val parsedDueTimeMinutes: Int? = null,
    val reminderAtEpochMillis: Long? = null,
    @StringRes val errorMessageRes: Int? = null
)

sealed interface TodoEditorSideEffect {
    data object Exit : TodoEditorSideEffect
}

private fun AssignedTodo.assignedReminderLeadMinutes(): Int {
    val dueDate = dueDate ?: return DEFAULT_REMINDER_LEAD_MINUTES
    val dueTimeMinutes = dueTimeMinutes ?: return DEFAULT_REMINDER_LEAD_MINUTES
    val reminderInstant = reminder
        ?.takeIf { it.enabled }
        ?.reminderAt
        ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: return DEFAULT_REMINDER_LEAD_MINUTES
    val dueInstant = dueDate
        .atTime(LocalTime.of(dueTimeMinutes / 60, dueTimeMinutes % 60))
        .atZone(ZoneId.systemDefault())
        .toInstant()
    val leadMinutes = java.time.Duration.between(reminderInstant, dueInstant).toMinutes().toInt()
    return leadMinutes.takeIf { it in setOf(0, 5, 10, 30, 60) } ?: DEFAULT_REMINDER_LEAD_MINUTES
}
