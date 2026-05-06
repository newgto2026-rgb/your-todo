package com.example.myfirstapp.feature.todo.impl.ui.editor

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.core.domain.scheduler.TodoReminderScheduler
import com.example.myfirstapp.core.domain.usecase.AddTodoUseCase
import com.example.myfirstapp.core.domain.usecase.DeleteTodoUseCase
import com.example.myfirstapp.core.domain.usecase.GetTodoUseCase
import com.example.myfirstapp.core.domain.usecase.UpdateTodoUseCase
import com.example.myfirstapp.core.model.ReminderRepeatType
import com.example.myfirstapp.core.model.TodoItem
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.feature.todo.impl.R
import com.example.myfirstapp.feature.todo.impl.ui.DEFAULT_REMINDER_LEAD_MINUTES
import com.example.myfirstapp.feature.todo.impl.ui.dueDateTimeToEpochMillis
import com.example.myfirstapp.feature.todo.impl.ui.dueTimeTextToMinutes
import com.example.myfirstapp.feature.todo.impl.ui.minutesToDueTimeText
import com.example.myfirstapp.feature.todo.impl.ui.normalizeRepeatType
import com.example.myfirstapp.feature.todo.impl.ui.parseIsoDateInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@HiltViewModel
class TodoEditorViewModel @Inject constructor(
    private val addTodoUseCase: AddTodoUseCase,
    private val updateTodoUseCase: UpdateTodoUseCase,
    private val deleteTodoUseCase: DeleteTodoUseCase,
    private val getTodoUseCase: GetTodoUseCase,
    private val todoReminderScheduler: TodoReminderScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(TodoEditorUiState())
    val uiState = _uiState

    private val sideEffects = MutableSharedFlow<TodoEditorSideEffect>()
    val sideEffect = sideEffects.asSharedFlow()

    fun initialize(todoId: Long?, dueDate: String?) {
        if (_uiState.value.isInitialized) return
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

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, errorMessageRes = null) }
    fun onDateInputChange(value: String) = _uiState.update { it.copy(dueDateInput = value, errorMessageRes = null) }
    fun onDueTimeInputChange(value: String) = _uiState.update { it.copy(dueTimeInput = value, errorMessageRes = null) }
    fun onReminderEnabledChange(value: Boolean) = _uiState.update { it.copy(reminderEnabled = value, errorMessageRes = null) }
    fun onReminderLeadMinutesChange(value: Int) = _uiState.update { it.copy(reminderLeadMinutes = value, errorMessageRes = null) }
    fun onPrioritySelected(priority: TodoPriority) = _uiState.update { it.copy(priority = priority, errorMessageRes = null) }

    fun onDelete() {
        val id = _uiState.value.editingTodoId ?: return
        viewModelScope.launch {
            deleteTodoUseCase(id).onSuccess {
                todoReminderScheduler.cancel(id)
                sideEffects.emit(TodoEditorSideEffect.Exit)
            }
        }
    }

    fun onSave() {
        val state = _uiState.value
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
    val title: String = "",
    val dueDateInput: String = "",
    val dueTimeInput: String = "",
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Int = DEFAULT_REMINDER_LEAD_MINUTES,
    val priority: TodoPriority = TodoPriority.MEDIUM,
    @StringRes val errorMessageRes: Int? = null
) {
    val showDelete: Boolean
        get() = editingTodoId != null
    val sheetTitleRes: Int
        get() = if (editingTodoId == null) R.string.todo_editor_title_new_task else R.string.todo_editor_title_edit_task

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
