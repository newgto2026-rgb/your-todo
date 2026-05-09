package com.neo.yourtodo.feature.todo.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.scheduler.CalendarWidgetUpdater
import com.neo.yourtodo.core.domain.scheduler.TodoReminderScheduler
import com.neo.yourtodo.core.domain.usecase.AddTodoUseCase
import com.neo.yourtodo.core.domain.usecase.DeleteTodoUseCase
import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.domain.usecase.GetTodoUseCase
import com.neo.yourtodo.core.domain.usecase.ManageAssignedTodoUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveTodosUseCase
import com.neo.yourtodo.core.domain.usecase.SyncTodosUseCase
import com.neo.yourtodo.core.domain.usecase.ToggleTodoDoneUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateSelectedTodoPriorityFilterUseCase
import com.neo.yourtodo.core.domain.usecase.UpdateTodoUseCase
import com.neo.yourtodo.core.domain.usecase.WorkspaceSyncNotifier
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.feature.todo.impl.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    private val addTodoUseCase: AddTodoUseCase,
    private val updateTodoUseCase: UpdateTodoUseCase,
    private val deleteTodoUseCase: DeleteTodoUseCase,
    private val toggleTodoDoneUseCase: ToggleTodoDoneUseCase,
    private val syncTodosUseCase: SyncTodosUseCase,
    private val getAssignedTodosUseCase: GetAssignedTodosUseCase,
    private val manageAssignedTodoUseCase: ManageAssignedTodoUseCase,
    private val updateSelectedTodoPriorityFilterUseCase: UpdateSelectedTodoPriorityFilterUseCase,
    private val getTodoUseCase: GetTodoUseCase,
    private val todoReminderScheduler: TodoReminderScheduler,
    private val calendarWidgetUpdater: CalendarWidgetUpdater,
    private val workspaceSyncNotifier: WorkspaceSyncNotifier = WorkspaceSyncNotifier()
) : ViewModel() {

    private val uiLocalState = MutableStateFlow(TodoListUiState(isLoading = true))
    private val sideEffectMutable = MutableSharedFlow<TodoListSideEffect>()
    private val todoItems = observeTodosUseCase()
    private val authSession = observeAuthSessionUseCase()
    private val localPriorityFilter = MutableStateFlow(TodoPriorityFilter.ALL)
    private val receivedAssignedTodos = MutableStateFlow<List<AssignedTodo>>(emptyList())
    private var syncJob: Job? = null
    private var foregroundSyncJob: Job? = null

    val sideEffect = sideEffectMutable.asSharedFlow()

    val uiState: StateFlow<TodoListUiState> = combine(
        todoItems,
        receivedAssignedTodos,
        authSession,
        localPriorityFilter,
        uiLocalState
    ) { items, assignedItems, session, localPriorityFilter, localState ->
        buildTodoListUiState(
            localState = localState,
            items = items,
            assignedItems = assignedItems,
            selectedFilter = localState.selectedFilter,
            selectedPriorityFilter = localPriorityFilter,
            profileInitial = session?.user?.nickname
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TodoListUiState(isLoading = true)
    )

    init {
        syncTodosQuietly()
        refreshAssignedTodosQuietly()
        observeWorkspaceSync()
    }

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
            is TodoListAction.OnToggleAssignedDone -> completeAssignedTodo(action.assignedTodoId)
            is TodoListAction.OnMoveToTomorrow -> moveToTomorrow(action.id)
            is TodoListAction.OnClearSchedule -> clearSchedule(action.id)
            TodoListAction.OnUndoLastQuickAction -> undoLastQuickAction()
            TodoListAction.OnUndoSnackbarDismissed -> clearPendingUndo()
            is TodoListAction.OnEditClick -> openEditDialog(action.id)
            is TodoListAction.OnDeleteRequest -> requestTodoDelete(action.id)
            is TodoListAction.OnAssignedDeleteRequest -> updateLocalState {
                copy(pendingAssignedDeleteId = action.assignedTodoId)
            }
            TodoListAction.OnDeleteCancel -> updateLocalState {
                copy(deleteConfirmation = null, pendingAssignedDeleteId = null)
            }
            TodoListAction.OnDeleteConfirm -> confirmDelete()
            TodoListAction.OnClearCompletedClick -> requestCompletedTodoDelete()
            is TodoListAction.OnFilterChange -> updateFilter(action.filter)
            is TodoListAction.OnPriorityFilterChange -> updatePriorityFilter(action.filter)
            is TodoListAction.OnSortOptionChange -> updateSortOption(action.option)
            TodoListAction.OnSyncClick -> Unit
            TodoListAction.OnScreenStarted -> startForegroundSync()
            TodoListAction.OnScreenStopped -> stopForegroundSync()

            TodoListAction.OnDismissDialog -> updateLocalState { dismissTodoEditor() }
        }
    }

    private fun saveTodo() {
        val current = uiLocalState.value
        current.editingAssignedTodoId?.let { assignedTodoId ->
            saveAssignedReminder(current, assignedTodoId)
            return
        }
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
                syncTodosQuietly()
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
                syncTodosQuietly()
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
                    syncTodosQuietly()
                }
                .onFailure {
                    sideEffectMutable.emit(
                        TodoListSideEffect.ShowSnackbar(R.string.todo_error_toggle_done_failed)
                    )
                }
        }
    }

    private fun completeAssignedTodo(assignedTodoId: String) {
        updateLocalState {
            copy(optimisticCompletedAssignedTodoIds = optimisticCompletedAssignedTodoIds + assignedTodoId)
        }
        viewModelScope.launch {
            manageAssignedTodoUseCase.complete(assignedTodoId)
                .onSuccess { refreshAssignedTodosQuietly() }
                .onFailure {
                    updateLocalState {
                        copy(optimisticCompletedAssignedTodoIds = optimisticCompletedAssignedTodoIds - assignedTodoId)
                    }
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
            editingAssignedTodoId = target.assignedTodoId,
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

    private fun saveAssignedReminder(
        current: TodoListUiState,
        assignedTodoId: String
    ) {
        val validation = validateTodoDraft(current)
        if (current.draftReminderEnabled && validation.errorMessageRes != null) {
            uiLocalState.value = current.copy(errorMessageRes = validation.errorMessageRes)
            return
        }

        viewModelScope.launch {
            val result = if (current.draftReminderEnabled) {
                manageAssignedTodoUseCase.upsertReminder(
                    assignedTodoId = assignedTodoId,
                    reminderAt = Instant.ofEpochMilli(checkNotNull(validation.reminderAtEpochMillis)).toString(),
                    enabled = true
                )
            } else {
                manageAssignedTodoUseCase.deleteReminder(assignedTodoId)
            }

            if (result.isSuccess) {
                refreshAssignedTodosQuietly()
                uiLocalState.value = current.dismissTodoEditor()
            } else {
                sideEffectMutable.emit(TodoListSideEffect.ShowSnackbar(R.string.todo_error_save_failed))
            }
        }
    }

    private fun requestTodoDelete(id: Long) {
        updateLocalState { copy(deleteConfirmation = TodoDeleteConfirmation.Single(id)) }
    }

    private fun requestCompletedTodoDelete() {
        val current = uiState.value
        if (!current.hasClearableCompletedItems) return
        updateLocalState {
            copy(
                deleteConfirmation = TodoDeleteConfirmation.Completed(
                    todoIds = current.completedTodoIds,
                    assignedTodoIds = current.completedAssignedTodoIds
                )
            )
        }
    }

    private fun confirmDelete() {
        uiLocalState.value.pendingAssignedDeleteId?.let { assignedTodoId ->
            confirmAssignedDelete(assignedTodoId)
            return
        }
        val confirmation = uiLocalState.value.deleteConfirmation ?: return
        viewModelScope.launch {
            val previousSingle = (confirmation as? TodoDeleteConfirmation.Single)
                ?.let { getTodoUseCase(it.id) }
            val todoIds = when (confirmation) {
                is TodoDeleteConfirmation.Single -> listOf(confirmation.id)
                is TodoDeleteConfirmation.Completed -> confirmation.todoIds
            }
            val assignedTodoIds = when (confirmation) {
                is TodoDeleteConfirmation.Single -> emptyList()
                is TodoDeleteConfirmation.Completed -> confirmation.assignedTodoIds
            }
            val deletedIds = mutableSetOf<Long>()
            val deletedAssignedIds = mutableSetOf<String>()
            var hasFailure = false
            todoIds.forEach { id ->
                deleteTodoUseCase(id)
                    .onSuccess {
                        deletedIds += id
                        todoReminderScheduler.cancel(id)
                    }
                    .onFailure {
                        hasFailure = true
                    }
            }
            assignedTodoIds.forEach { assignedTodoId ->
                manageAssignedTodoUseCase.deleteReceived(assignedTodoId)
                    .onSuccess {
                        deletedAssignedIds += assignedTodoId
                    }
                    .onFailure {
                        hasFailure = true
                    }
            }
            updateLocalState {
                val shouldDismissEditor = editingItem?.id?.let { it in deletedIds } == true
                val clearedState = if (shouldDismissEditor) dismissTodoEditor() else this
                clearedState.copy(
                    deleteConfirmation = null,
                    optimisticCompletedAssignedTodoIds = optimisticCompletedAssignedTodoIds - deletedAssignedIds
                )
            }

            if (deletedIds.isNotEmpty()) {
                notifyCalendarWidgetChanged()
                syncTodosQuietly()
            }
            if (deletedAssignedIds.isNotEmpty()) {
                refreshAssignedTodosQuietly()
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

    private fun confirmAssignedDelete(assignedTodoId: String) {
        viewModelScope.launch {
            manageAssignedTodoUseCase.deleteReceived(assignedTodoId)
                .onSuccess {
                    updateLocalState {
                        copy(
                            pendingAssignedDeleteId = null,
                            optimisticCompletedAssignedTodoIds = optimisticCompletedAssignedTodoIds - assignedTodoId
                        )
                    }
                    refreshAssignedTodosQuietly()
                }
                .onFailure {
                    updateLocalState { copy(pendingAssignedDeleteId = null) }
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
                notifyCalendarWidgetChanged()
                syncTodosQuietly()
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
                syncTodosQuietly()
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
                    syncTodosQuietly()
                    sideEffectMutable.emit(TodoListSideEffect.ShowSnackbar(R.string.todo_action_restored))
                }
                .onFailure {
                    sideEffectMutable.emit(TodoListSideEffect.ShowSnackbar(R.string.todo_error_undo_failed))
                }
        }
    }

    private fun clearPendingUndo() {
        updateLocalState { copy(pendingUndoTodo = null) }
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

    private fun syncTodosQuietly() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            syncTodosUseCase()
        }
    }

    private fun observeWorkspaceSync() {
        viewModelScope.launch {
            workspaceSyncNotifier.snapshots.collect { snapshot ->
                if (snapshot != null) {
                    receivedAssignedTodos.value = snapshot.visibleReceivedAssignedTodos
                }
            }
        }
    }

    private fun refreshAssignedTodosQuietly() {
        viewModelScope.launch {
            getAssignedTodosUseCase.visibleReceived()
                .onSuccess { receivedAssignedTodos.value = it }
        }
    }

    private fun startForegroundSync() {
        if (foregroundSyncJob?.isActive == true) return
        foregroundSyncJob = viewModelScope.launch {
            while (true) {
                syncTodosQuietly()
                refreshAssignedTodosQuietly()
                delay(ForegroundSyncIntervalMillis)
            }
        }
    }

    private fun stopForegroundSync() {
        foregroundSyncJob?.cancel()
        foregroundSyncJob = null
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

private const val ForegroundSyncIntervalMillis = 15_000L
