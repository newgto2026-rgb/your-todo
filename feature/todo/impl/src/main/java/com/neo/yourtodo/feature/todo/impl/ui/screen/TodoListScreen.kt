package com.neo.yourtodo.feature.todo.impl.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.ui.TodoItemRow
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodoListRoute(
    presetFilter: TodoFilter,
    viewModelKey: String = "todo:${presetFilter.name}",
    onBackBlockedChange: (Boolean) -> Unit = {},
    onAddRequested: (LocalDate?) -> Unit = {},
    onEditRequested: (Long) -> Unit = {},
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
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                is TodoListSideEffect.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(sideEffect.messageRes),
                        actionLabel = sideEffect.actionLabelRes?.let(context::getString)
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        when (sideEffect.action) {
                            TodoListSnackbarAction.UndoLastQuickAction -> {
                                viewModel.onAction(TodoListAction.OnUndoLastQuickAction)
                            }

                            null -> Unit
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(onBackBlockedChange) {
        onBackBlockedChange(false)
    }

    TodoListScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        onAddRequested = onAddRequested,
        onEditRequested = onEditRequested
    )
}

@Composable
private fun TodoListScreen(
    uiState: TodoListUiState,
    onAction: (TodoListAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    onAddRequested: (LocalDate?) -> Unit,
    onEditRequested: (Long) -> Unit
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

    LaunchedEffect(
        uiState.selectedFilter,
        uiState.selectedPriorityFilter,
        uiState.selectedSortOption
    ) {
        listState.scrollToItem(0)
    }

    Scaffold(
        containerColor = Color(0xFFF5F6FB),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    onAddRequested(
                        if (uiState.selectedFilter == TodoFilter.TODAY) LocalDate.now() else null
                    )
                },
                containerColor = Color(0xFF4A6697),
                contentColor = Color.White,
                modifier = Modifier
                    .size(58.dp)
                    .testTag("add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("todo_screen_${uiState.selectedFilter.name.lowercase()}")
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            AppHeader()
            Spacer(Modifier.height(22.dp))

            HeaderSummary(
                title = title,
                subtitle = subtitle,
                selectedFilter = uiState.selectedFilter,
                completionProgress = completionProgress
            )
            if (
                uiState.selectedFilter == TodoFilter.COMPLETED &&
                uiState.completedTodoIds.isNotEmpty()
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
            PriorityFilterBar(
                selectedPriorityFilter = uiState.selectedPriorityFilter,
                onPrioritySelected = { onAction(TodoListAction.OnPriorityFilterChange(it)) }
            )
            if (uiState.selectedFilter == TodoFilter.ALL) {
                Spacer(Modifier.height(10.dp))
                TodoSortMenu(
                    selectedSortOption = uiState.selectedSortOption,
                    onSortOptionSelected = { onAction(TodoListAction.OnSortOptionChange(it)) },
                    modifier = Modifier.align(Alignment.End)
                )
            }
            Spacer(Modifier.height(12.dp))

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
                Spacer(Modifier.height(12.dp))
            }

            if (uiState.items.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("todo_list"),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                            onAction(TodoListAction.OnDeleteRequest(item.id))
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
                                    onAction(TodoListAction.OnDeleteRequest(item.id))
                                },
                                showQuickActions = false
                            )
                        }
                    }
                }
            } else if (!uiState.isQuickAddVisible) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    message = stringResource(emptyMessage(uiState.selectedFilter))
                )
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
}

@Composable
private fun QuickAddLauncher(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag("quick_add_open")
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.todo_quick_add_open))
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
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("quick_add_slot"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(
            onClick = { isExpanded = true },
            modifier = Modifier.testTag("todo_sort_menu_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(stringResource(selectedSortOption.labelRes()))
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            TodoSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    leadingIcon = {
                        if (option == selectedSortOption) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null
                            )
                        }
                    },
                    onClick = {
                        isExpanded = false
                        onSortOptionSelected(option)
                    },
                    modifier = Modifier.testTag(option.testTag())
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
            onToggleDone = { onAction(TodoListAction.OnToggleDone(item.id)) },
            onClick = { onEditRequested(item.id) },
            onDeleteRequest = onDeleteRequest,
            priorityLabel = priorityLabel(item.priority),
            priorityColor = priorityColor(item.priority)
        )
        if (showQuickActions) {
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
    priorityColor: Color
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
            content = {
                TodoRowOverflowMenu(
                    itemId = itemId,
                    onDeleteRequest = onDeleteRequest
                )
            }
        )
    }
}

@Composable
private fun TodoRowOverflowMenu(
    itemId: Long,
    onDeleteRequest: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { isExpanded = true },
            modifier = Modifier.testTag("todo_row_more_$itemId")
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.todo_row_more_actions)
            )
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.todo_editor_delete)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null
                    )
                },
                onClick = {
                    isExpanded = false
                    onDeleteRequest()
                },
                modifier = Modifier.testTag("todo_row_delete_$itemId")
            )
        }
    }
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
            confirmation.ids.size,
            confirmation.ids.size
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
        TodayPlannerSection(R.string.todo_today_section_overdue, overdue),
        TodayPlannerSection(R.string.todo_today_section_timed, timedToday),
        TodayPlannerSection(R.string.todo_today_section_today, todayUntimed)
    )
}

@Composable
private fun priorityLabel(priority: TodoPriority): String = when (priority) {
    TodoPriority.LOW -> stringResource(R.string.todo_priority_low)
    TodoPriority.MEDIUM -> stringResource(R.string.todo_priority_medium)
    TodoPriority.HIGH -> stringResource(R.string.todo_priority_high)
}

private fun priorityColor(priority: TodoPriority): Color = when (priority) {
    TodoPriority.LOW -> Color(0xFF6E8E72)
    TodoPriority.MEDIUM -> Color(0xFF8B7A4E)
    TodoPriority.HIGH -> Color(0xFF9B4B4B)
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
