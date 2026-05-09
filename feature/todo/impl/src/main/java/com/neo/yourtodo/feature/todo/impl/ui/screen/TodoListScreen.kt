package com.neo.yourtodo.feature.todo.impl.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.ui.TodoItemRow
import com.neo.yourtodo.core.ui.navigation.WorkspaceSyncUiState
import com.neo.yourtodo.core.ui.YourTodoScreenBackground
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        onSyncClick = onWorkspaceSyncClick
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
    onSyncClick: () -> Unit
) {
    val todayHeaderDateFormat = stringResource(R.string.todo_today_header_date_format)
    val (title, subtitle) = headerTextFor(
        filter = uiState.selectedFilter,
        allTitle = stringResource(R.string.todo_header_all_title),
        allSubtitle = stringResource(uiState.selectedSortOption.subtitleRes()),
        todayTitle = stringResource(R.string.todo_filter_today),
        todaySubtitle = formatDateLabel(LocalDate.now(), todayHeaderDateFormat),
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
                FloatingActionButton(
                    onClick = {
                        onAddRequested(
                            if (uiState.selectedFilter == TodoFilter.TODAY) LocalDate.now() else null
                        )
                    },
                    containerColor = Color(0xFF676CB4),
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(58.dp)
                        .testTag("add_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
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
                onSyncClick = onSyncClick
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("todo_list"),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.selectedFilter == TodoFilter.TODAY) {
                        todayPlannerSections(uiState.items).forEach { section ->
                            if (section.items.isNotEmpty()) {
                                item(key = "section_${section.titleRes}") {
                                    Text(
                                        text = stringResource(section.titleRes),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF323640),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                items(items = section.items, key = { item -> item.id }) { item ->
                                    TodoPlannerRow(
                                        item = item,
                                        rowCompletedText = rowCompletedText,
                                        rowTodayText = rowTodayText,
                                        dueDateFormat = dueDateFormat,
                                        onAction = onAction,
                                        onEditRequested = onEditRequested,
                                        onDeleteRequest = {
                                            item.assignedTodoId?.let {
                                                onAction(TodoListAction.OnAssignedDeleteRequest(it))
                                            } ?: onAction(TodoListAction.OnDeleteRequest(item.id))
                                        },
                                        showQuickActions = true
                                    )
                                }
                            }
                        }
                    } else {
                        items(items = uiState.items, key = { item -> item.id }) { item ->
                            TodoPlannerRow(
                                item = item,
                                rowCompletedText = rowCompletedText,
                                rowTodayText = rowTodayText,
                                dueDateFormat = dueDateFormat,
                                onAction = onAction,
                                onEditRequested = onEditRequested,
                                onDeleteRequest = {
                                    item.assignedTodoId?.let {
                                        onAction(TodoListAction.OnAssignedDeleteRequest(it))
                                    } ?: onAction(TodoListAction.OnDeleteRequest(item.id))
                                },
                                showQuickActions = false
                            )
                        }
                    }
                }
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
}

@Composable
private fun QuickAddLauncher(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .testTag("quick_add_open"),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color(0xFFD4DEEC))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE5EDF8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF4A6697),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = stringResource(R.string.todo_quick_add_open),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF51607A)
            )
            }
        }
}

@Composable
private fun QuickAddSlot(
    uiState: TodoListUiState,
    onAction: (TodoListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val placeholderRes = when (uiState.selectedFilter) {
        TodoFilter.TODAY -> R.string.todo_quick_add_placeholder_today
        else -> R.string.todo_quick_add_placeholder_all
    }
    val errorText = uiState.quickAddErrorMessageRes?.let { stringResource(it) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("quick_add_slot"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.quickAddTitle,
                onValueChange = { onAction(TodoListAction.OnQuickAddTitleChange(it)) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("quick_add_title_input"),
                placeholder = { Text(stringResource(placeholderRes)) },
                isError = uiState.quickAddErrorMessageRes != null,
                supportingText = errorText?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { onAction(TodoListAction.OnQuickAddSubmit) }
                )
            )
            IconButton(
                onClick = { onAction(TodoListAction.OnQuickAddSubmit) },
                modifier = Modifier.testTag("quick_add_submit")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.todo_quick_add_submit)
                )
            }
            IconButton(
                onClick = { onAction(TodoListAction.OnQuickAddDismiss) },
                modifier = Modifier.testTag("quick_add_close")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.todo_quick_add_close)
                )
            }
        }
    }
}

@Composable
private fun TodoSortMenu(
    selectedSortOption: TodoSortOption,
    onSortOptionSelected: (TodoSortOption) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedLabel = stringResource(selectedSortOption.labelRes())

    Box(modifier = modifier) {
        Surface(
            onClick = { isExpanded = true },
            modifier = Modifier
                .height(34.dp)
                .testTag("todo_sort_menu_button"),
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, Color(0xFFE0E6F1))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (showLabel) 10.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = selectedLabel,
                    tint = Color(0xFF5F5391),
                    modifier = Modifier.size(18.dp)
                )
                if (showLabel) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF5F5391)
                    )
                }
            }
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color(0xFFFAFBFE),
            shadowElevation = 8.dp
        ) {
            TodoSortOption.entries.forEach { option ->
                val isSelected = option == selectedSortOption
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = stringResource(option.labelRes()),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF303440)
                            )
                            Text(
                                text = stringResource(option.descriptionRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7A8595)
                            )
                        }
                    },
                    leadingIcon = {
                        SortOptionDot(color = option.accentColor())
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF5F5391)
                            )
                        }
                    },
                    onClick = {
                        isExpanded = false
                        onSortOptionSelected(option)
                    },
                    modifier = Modifier
                        .background(if (isSelected) Color(0xFFF0F3FF) else Color.Transparent)
                        .testTag(option.testTag())
                )
            }
        }
    }
}

@Composable
private fun TodoPlannerRow(
    item: TodoItemUiModel,
    rowCompletedText: String,
    rowTodayText: String,
    dueDateFormat: String,
    onAction: (TodoListAction) -> Unit,
    onEditRequested: (Long) -> Unit,
    onDeleteRequest: () -> Unit,
    showQuickActions: Boolean
) {
    val dueDateLabel = when {
        item.dueDate == LocalDate.now() -> rowTodayText
        !item.dueDateText.isNullOrBlank() -> formatDueDateLabel(item.dueDateText, dueDateFormat)
        else -> null
    }
    val dueLabel = buildDueLabel(dueDateLabel, item.dueTimeText)
    val leadLabel = when (item.reminderLeadMinutes) {
        0 -> stringResource(R.string.todo_reminder_lead_at_time)
        5 -> stringResource(R.string.todo_reminder_lead_5m)
        10 -> stringResource(R.string.todo_reminder_lead_10m)
        30 -> stringResource(R.string.todo_reminder_lead_30m)
        60 -> stringResource(R.string.todo_reminder_lead_60m)
        else -> null
    }
    val reminderLabel = if (item.isReminderEnabled && !leadLabel.isNullOrBlank() && !item.isDone) {
        leadLabel
    } else {
        null
    }
    val assignedFromLabel = item.senderNickname?.let {
        stringResource(R.string.todo_row_assigned_from, it)
    }
    val rowDueLabel = when {
        item.isDone -> rowCompletedText
        !dueLabel.isNullOrBlank() -> dueLabel
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DeletableTodoItemRow(
            itemId = item.id,
            title = item.title,
            dueDateText = rowDueLabel,
            reminderText = reminderLabel,
            isDone = item.isDone,
            isEmphasized = !item.isDone && dueDateLabel == rowTodayText,
            isReminderEnabled = item.isReminderEnabled,
            onToggleDone = {
                item.assignedTodoId?.let {
                    onAction(TodoListAction.OnToggleAssignedDone(it))
                } ?: onAction(TodoListAction.OnToggleDone(item.id))
            },
            onClick = {
                if (item.isAssigned) {
                    onAction(TodoListAction.OnEditClick(item.id))
                } else {
                    onEditRequested(item.id)
                }
            },
            onDeleteRequest = onDeleteRequest,
            priorityLabel = priorityLabel(item.priority),
            priorityColor = priorityColor(item.priority),
            assignedFromText = assignedFromLabel
        )
        if (showQuickActions && !item.isAssigned) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onAction(TodoListAction.OnMoveToTomorrow(item.id)) },
                    modifier = Modifier.testTag("todo_quick_tomorrow_${item.id}")
                ) {
                    Text(stringResource(R.string.todo_quick_action_tomorrow))
                }
                if (item.dueDate != null) {
                    TextButton(
                        onClick = { onAction(TodoListAction.OnClearSchedule(item.id)) },
                        modifier = Modifier.testTag("todo_quick_clear_date_${item.id}")
                    ) {
                        Text(stringResource(R.string.todo_quick_action_clear_date))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeletableTodoItemRow(
    itemId: Long,
    title: String,
    dueDateText: String?,
    reminderText: String?,
    isDone: Boolean,
    isEmphasized: Boolean,
    isReminderEnabled: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
    priorityLabel: String,
    priorityColor: Color,
    assignedFromText: String?
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDeleteRequest()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFD85C5C))
                    .padding(end = 22.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    ) {
        TodoItemRow(
            title = title,
            dueDateText = dueDateText,
            reminderText = reminderText,
            isDone = isDone,
            isEmphasized = isEmphasized,
            isReminderEnabled = isReminderEnabled,
            onToggleDone = onToggleDone,
            onClick = onClick,
            modifier = Modifier.testTag("todo_row_$itemId"),
            priorityLabel = priorityLabel,
            priorityColor = priorityColor,
            toggleTestTag = "todo_row_toggle_$itemId",
            sourceText = assignedFromText
        )
    }
}

@Composable
private fun SortOptionDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun DeleteConfirmationDialog(
    confirmation: TodoDeleteConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val titleRes = when (confirmation) {
        is TodoDeleteConfirmation.Single -> R.string.todo_delete_confirm_title
        is TodoDeleteConfirmation.Completed -> R.string.todo_clear_completed_confirm_title
    }
    val message = when (confirmation) {
        is TodoDeleteConfirmation.Single -> stringResource(R.string.todo_delete_confirm_message)
        is TodoDeleteConfirmation.Completed -> pluralStringResource(
            R.plurals.todo_clear_completed_confirm_message,
            confirmation.itemCount,
            confirmation.itemCount
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete_confirmation_dialog"),
        title = { Text(stringResource(titleRes)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(stringResource(R.string.todo_editor_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.todo_editor_cancel))
            }
        }
    )
}

internal data class TodayPlannerSection(
    val titleRes: Int,
    val items: List<TodoItemUiModel>
)

internal fun todayPlannerSections(items: List<TodoItemUiModel>): List<TodayPlannerSection> {
    val today = LocalDate.now()
    val overdue = items.filter { !it.isDone && it.dueDate?.isBefore(today) == true }
    val timedToday = items.filter {
        !it.isDone && it.dueDate == today && !it.dueTimeText.isNullOrBlank()
    }
    val todayUntimed = items.filter {
        !it.isDone && it.dueDate == today && it.dueTimeText.isNullOrBlank()
    }
    return listOf(
        TodayPlannerSection(R.string.todo_today_section_timed, timedToday),
        TodayPlannerSection(R.string.todo_today_section_today, todayUntimed),
        TodayPlannerSection(R.string.todo_today_section_overdue, overdue)
    )
}

@Composable
private fun priorityLabel(priority: TodoPriority): String = when (priority) {
    TodoPriority.LOW -> stringResource(R.string.todo_priority_low)
    TodoPriority.MEDIUM -> stringResource(R.string.todo_priority_medium)
    TodoPriority.HIGH -> stringResource(R.string.todo_priority_high)
}

private fun priorityColor(priority: TodoPriority): Color = when (priority) {
    TodoPriority.LOW -> Color(0xFF6FA58C)
    TodoPriority.MEDIUM -> Color(0xFF6F86C9)
    TodoPriority.HIGH -> Color(0xFFC76B7D)
}

private fun headerTextFor(
    filter: TodoFilter,
    allTitle: String,
    allSubtitle: String,
    todayTitle: String,
    todaySubtitle: String,
    completedTitle: String,
    completedSubtitle: String
): Pair<String, String> = when (filter) {
    TodoFilter.ALL -> allTitle to allSubtitle
    TodoFilter.TODAY -> todayTitle to todaySubtitle
    TodoFilter.COMPLETED -> completedTitle to completedSubtitle
}

@StringRes
private fun TodoSortOption.labelRes(): Int = when (this) {
    TodoSortOption.DEFAULT -> R.string.todo_sort_default
    TodoSortOption.DUE_DATE -> R.string.todo_sort_due_date
    TodoSortOption.PRIORITY -> R.string.todo_sort_priority
}

private fun TodoSortOption.descriptionRes(): Int = when (this) {
    TodoSortOption.DEFAULT -> R.string.todo_sort_default_description
    TodoSortOption.DUE_DATE -> R.string.todo_sort_due_date_description
    TodoSortOption.PRIORITY -> R.string.todo_sort_priority_description
}

private fun TodoSortOption.accentColor(): Color = when (this) {
    TodoSortOption.DEFAULT -> Color(0xFF8A93A5)
    TodoSortOption.DUE_DATE -> Color(0xFF4B83C5)
    TodoSortOption.PRIORITY -> Color(0xFFC76B7D)
}

@StringRes
private fun TodoSortOption.subtitleRes(): Int = when (this) {
    TodoSortOption.DEFAULT -> R.string.todo_header_all_subtitle
    TodoSortOption.DUE_DATE -> R.string.todo_header_all_subtitle_due_date
    TodoSortOption.PRIORITY -> R.string.todo_header_all_subtitle_priority
}

private fun TodoSortOption.testTag(): String = when (this) {
    TodoSortOption.DEFAULT -> "todo_sort_option_default"
    TodoSortOption.DUE_DATE -> "todo_sort_option_due_date"
    TodoSortOption.PRIORITY -> "todo_sort_option_priority"
}

private fun completionProgress(uiState: TodoListUiState): Float {
    if (uiState.items.isEmpty()) return 0f
    return uiState.items.count { it.isDone }.toFloat() / uiState.items.size.toFloat()
}

internal fun formatDateLabel(date: LocalDate, pattern: String): String =
    runCatching {
        date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }.getOrDefault(date.toString())

internal fun formatDueDateLabel(raw: String?, pattern: String): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val parsed = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
        formatDateLabel(parsed, pattern)
    }.getOrDefault(raw)
}

private fun buildDueLabel(dueDate: String?, dueTime: String?): String? {
    if (dueDate.isNullOrBlank()) return null
    return if (dueTime.isNullOrBlank()) dueDate else "$dueDate $dueTime"
}
