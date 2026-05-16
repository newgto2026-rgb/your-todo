package com.neo.yourtodo.feature.todo.impl.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoSortOption
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.ui.navigation.WorkspaceSyncUiState
import com.neo.yourtodo.core.ui.YourTodoScreenBackground
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.ui.ai.AiTodoDraftRoute
import com.neo.yourtodo.feature.todo.impl.ui.DEFAULT_REMINDER_LEAD_MINUTES
import com.neo.yourtodo.feature.todo.impl.ui.TodoDeleteConfirmation
import com.neo.yourtodo.feature.todo.impl.ui.TodoListAction
import com.neo.yourtodo.feature.todo.impl.ui.TodoListSideEffect
import com.neo.yourtodo.feature.todo.impl.ui.TodoListSnackbarAction
import com.neo.yourtodo.feature.todo.impl.ui.TodoListUiState
import com.neo.yourtodo.feature.todo.impl.ui.TodoListViewModel
import com.neo.yourtodo.feature.todo.impl.ui.AppHeader
import com.neo.yourtodo.feature.todo.impl.ui.EditTodoBottomSheet
import com.neo.yourtodo.feature.todo.impl.ui.EmptyState
import com.neo.yourtodo.feature.todo.impl.ui.HeaderSummary
import com.neo.yourtodo.feature.todo.impl.ui.PriorityFilterBar
import com.neo.yourtodo.feature.todo.impl.ui.emptyMessage
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun TodoListRoute(
    presetFilter: TodoFilter,
    viewModelKey: String = "todo:${presetFilter.name}",
    onBackBlockedChange: (Boolean) -> Unit = {},
    onAddRequested: (LocalDate?) -> Unit = {},
    onEditRequested: (Long) -> Unit = {},
    workspaceSyncState: StateFlow<WorkspaceSyncUiState> = MutableStateFlow(WorkspaceSyncUiState()),
    onWorkspaceSyncClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: TodoListViewModel = hiltViewModel(key = viewModelKey)
) {
    val routeUiState = remember(viewModel, presetFilter) {
        viewModel.setRouteFilter(presetFilter)
        viewModel.uiState
    }
    val collectedUiState by routeUiState.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.CREATED
    )
    val uiState = if (collectedUiState.selectedFilter == presetFilter) {
        collectedUiState
    } else {
        TodoListUiState(isLoading = true, selectedFilter = presetFilter)
    }
    val syncUiState by workspaceSyncState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    if (uiState.isLoading) {
        TodoListLoadingScreen()
        return
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                is TodoListSideEffect.ShowSnackbar -> {
                    val dismissJob = if (sideEffect.action == TodoListSnackbarAction.UndoLastQuickAction) {
                        launch {
                            delay(UndoSnackbarDurationMillis)
                            snackbarHostState.currentSnackbarData?.dismiss()
                        }
                    } else {
                        null
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(sideEffect.messageRes),
                        actionLabel = sideEffect.actionLabelRes?.let(context::getString),
                        duration = if (sideEffect.actionLabelRes == null) {
                            SnackbarDuration.Short
                        } else {
                            SnackbarDuration.Long
                        }
                    )
                    dismissJob?.cancel()
                    if (result == SnackbarResult.ActionPerformed) {
                        when (sideEffect.action) {
                            TodoListSnackbarAction.UndoLastQuickAction -> {
                                viewModel.onAction(TodoListAction.OnUndoLastQuickAction)
                            }

                            null -> Unit
                        }
                    } else if (sideEffect.action == TodoListSnackbarAction.UndoLastQuickAction) {
                        viewModel.onAction(TodoListAction.OnUndoSnackbarDismissed)
                    }
                }
            }
        }
    }

    LaunchedEffect(onBackBlockedChange) {
        onBackBlockedChange(false)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onAction(TodoListAction.OnScreenStarted)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onAction(TodoListAction.OnScreenStopped)
    }

    TodoListScreen(
        uiState = uiState,
        isSyncing = syncUiState.isSyncing,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        onAddRequested = onAddRequested,
        onEditRequested = onEditRequested,
        onSyncClick = onWorkspaceSyncClick,
        onProfileClick = onProfileClick
    )
}

private const val UndoSnackbarDurationMillis = 5_000L

@Composable
private fun TodoListLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F6FB))
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TodoListScreen(
    uiState: TodoListUiState,
    isSyncing: Boolean,
    onAction: (TodoListAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    onAddRequested: (LocalDate?) -> Unit,
    onEditRequested: (Long) -> Unit,
    onSyncClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val today = LocalDate.now()
    val todayHeaderDateFormat = stringResource(R.string.todo_today_header_date_format)
    val (title, subtitle) = headerTextFor(
        filter = uiState.selectedFilter,
        allTitle = stringResource(R.string.todo_header_all_title),
        allSubtitle = stringResource(uiState.selectedSortOption.subtitleRes()),
        todayTitle = stringResource(R.string.todo_filter_today),
        todaySubtitle = formatDateLabel(today, todayHeaderDateFormat),
        completedTitle = stringResource(R.string.todo_header_completed_title),
        completedSubtitle = stringResource(R.string.todo_header_completed_subtitle)
    )
    val completionProgress = completionProgress(uiState)
    val rowCompletedText = stringResource(R.string.todo_row_subtitle_completed)
    val rowTodayText = stringResource(R.string.todo_row_subtitle_today)
    val shouldShowQuickAdd = uiState.selectedFilter != TodoFilter.COMPLETED
    val dueDateFormat = stringResource(R.string.todo_due_date_format)
    val listState = rememberLazyListState()
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isAddMenuExpanded by remember { mutableStateOf(false) }
    var isAiSheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(
        uiState.selectedFilter,
        uiState.selectedPriorityFilter,
        uiState.selectedSortOption
    ) {
        listState.scrollToItem(0)
    }

    YourTodoScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                AddTodoFloatingMenu(
                    expanded = isAddMenuExpanded,
                    onExpandedChange = { isAddMenuExpanded = it },
                    onManualAdd = {
                        isAddMenuExpanded = false
                        onAddRequested(
                            if (uiState.selectedFilter == TodoFilter.TODAY) today else null
                        )
                    },
                    onAiAdd = {
                        isAddMenuExpanded = false
                        isAiSheetVisible = true
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.End,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0.dp)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("todo_screen_${uiState.selectedFilter.name.lowercase()}")
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                AppHeader(
                    profileInitial = uiState.profileInitial,
                    isSyncing = isSyncing,
                    onSyncClick = onSyncClick,
                    onProfileClick = onProfileClick
                )
                Spacer(Modifier.height(12.dp))

                HeaderSummary(
                    title = title,
                    subtitle = subtitle,
                    selectedFilter = uiState.selectedFilter,
                    completionProgress = completionProgress
                )
                if (
                    uiState.selectedFilter == TodoFilter.COMPLETED &&
                    uiState.hasClearableCompletedItems
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onAction(TodoListAction.OnClearCompletedClick) },
                            modifier = Modifier.testTag("clear_completed_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.todo_clear_completed_action))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriorityFilterBar(
                        selectedPriorityFilter = uiState.selectedPriorityFilter,
                        onPrioritySelected = { onAction(TodoListAction.OnPriorityFilterChange(it)) },
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.selectedFilter == TodoFilter.ALL) {
                        TodoSortMenu(
                            selectedSortOption = uiState.selectedSortOption,
                            onSortOptionSelected = { onAction(TodoListAction.OnSortOptionChange(it)) },
                            showLabel = uiState.selectedSortOption != TodoSortOption.DEFAULT
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (shouldShowQuickAdd) {
                    if (uiState.isQuickAddVisible) {
                        QuickAddSlot(
                            uiState = uiState,
                            onAction = onAction,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        QuickAddLauncher(
                            onClick = { onAction(TodoListAction.OnQuickAddClick) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (uiState.items.isNotEmpty()) {
                    TodoListItems(
                        uiState = uiState,
                        rowCompletedText = rowCompletedText,
                        rowTodayText = rowTodayText,
                        dueDateFormat = dueDateFormat,
                        today = today,
                        listState = listState,
                        onAction = onAction,
                        onEditRequested = onEditRequested
                    )
                } else if (!uiState.isLoading && !uiState.isQuickAddVisible) {
                    EmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        message = stringResource(emptyMessage(uiState.selectedFilter))
                    )
                }
            }
        }
    }

    BackHandler(enabled = uiState.isQuickAddVisible && shouldShowQuickAdd) {
        onAction(TodoListAction.OnQuickAddDismiss)
    }

    uiState.deleteConfirmation?.let { confirmation ->
        DeleteConfirmationDialog(
            confirmation = confirmation,
            onConfirm = { onAction(TodoListAction.OnDeleteConfirm) },
            onDismiss = { onAction(TodoListAction.OnDeleteCancel) }
        )
    }
    uiState.pendingAssignedDeleteId?.let {
        DeleteConfirmationDialog(
            confirmation = TodoDeleteConfirmation.Single(0),
            onConfirm = { onAction(TodoListAction.OnDeleteConfirm) },
            onDismiss = { onAction(TodoListAction.OnDeleteCancel) }
        )
    }

    if (uiState.isEditDialogVisible) {
        val isAssignedEdit = uiState.editingAssignedTodoId != null
        ModalBottomSheet(
            onDismissRequest = { onAction(TodoListAction.OnDismissDialog) },
            sheetState = editSheetState,
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            EditTodoBottomSheet(
                sheetTitle = stringResource(
                    when {
                        isAssignedEdit && uiState.editingAssignedTodoMode == AssignmentMode.DIRECT ->
                            R.string.todo_editor_title_direct_assigned_task
                        isAssignedEdit -> R.string.todo_editor_title_received_task
                        uiState.editingItem == null -> R.string.todo_editor_title_new_task
                        else -> R.string.todo_editor_title_edit_task
                    }
                ),
                title = uiState.draftTitle,
                dueDateInput = uiState.draftDueDateInput,
                dueTimeInput = uiState.draftDueTimeInput,
                reminderEnabled = uiState.draftReminderEnabled,
                reminderLeadMinutes = uiState.draftReminderLeadMinutes ?: DEFAULT_REMINDER_LEAD_MINUTES,
                selectedPriority = uiState.draftPriority,
                errorMessageRes = uiState.errorMessageRes,
                onTitleChange = { onAction(TodoListAction.OnTitleChange(it)) },
                onDateInputChange = { onAction(TodoListAction.OnDueDateInputChange(it)) },
                onDueTimeInputChange = { onAction(TodoListAction.OnDueTimeInputChange(it)) },
                onReminderEnabledChange = { onAction(TodoListAction.OnReminderEnabledChange(it)) },
                onReminderLeadMinutesChange = { onAction(TodoListAction.OnReminderLeadMinutesChange(it)) },
                onPrioritySelected = { onAction(TodoListAction.OnPrioritySelectedInEditor(it)) },
                onDismiss = { onAction(TodoListAction.OnDismissDialog) },
                onSave = { onAction(TodoListAction.OnSaveClick) },
                onDelete = { uiState.editingItem?.id?.let { onAction(TodoListAction.OnDeleteRequest(it)) } },
                showDelete = !isAssignedEdit && uiState.editingItem?.id != null,
                contentEditable = !isAssignedEdit
            )
        }
    }

    if (isAiSheetVisible) {
        LockedAiBottomSheetDialog(
            onDismissRequest = { isAiSheetVisible = false }
        ) {
            AiTodoDraftRoute(onDismiss = { isAiSheetVisible = false })
        }
    }
}
