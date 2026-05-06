package com.example.myfirstapp.feature.todo.impl.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.core.ui.TodoItemRow
import com.example.myfirstapp.feature.todo.impl.R
import com.example.myfirstapp.feature.todo.impl.model.TodoItemUiModel
import java.time.LocalDate

@Composable
fun TodoListRoute(
    presetFilter: TodoFilter,
    onBackBlockedChange: (Boolean) -> Unit = {},
    onAddRequested: (LocalDate?) -> Unit = {},
    onEditRequested: (Long) -> Unit = {},
    viewModel: TodoListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                        viewModel.onAction(TodoListAction.OnUndoLastQuickAction)
                    }
                }
            }
        }
    }

    LaunchedEffect(presetFilter, uiState.selectedFilter) {
        if (uiState.selectedFilter != presetFilter) {
            viewModel.onAction(TodoListAction.OnFilterChange(presetFilter))
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
    val (title, subtitle) = headerTextFor(
        filter = uiState.selectedFilter,
        allTitle = stringResource(R.string.todo_header_all_title),
        allSubtitle = stringResource(R.string.todo_header_all_subtitle),
        todayTitle = stringResource(R.string.todo_filter_today),
        completedTitle = stringResource(R.string.todo_header_completed_title),
        completedSubtitle = stringResource(R.string.todo_header_completed_subtitle)
    )
    val completionProgress = completionProgress(uiState)
    val rowCompletedText = stringResource(R.string.todo_row_subtitle_completed)
    val rowTodayText = stringResource(R.string.todo_row_subtitle_today)

    Scaffold(
        containerColor = Color(0xFFF5F6FB),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddRequested(null) },
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
            Spacer(Modifier.height(12.dp))

            if (uiState.items.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
            } else {
                EmptyState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    message = stringResource(emptyMessage(uiState.selectedFilter))
                )
            }
        }
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
private fun TodoPlannerRow(
    item: TodoItemUiModel,
    rowCompletedText: String,
    rowTodayText: String,
    onAction: (TodoListAction) -> Unit,
    onEditRequested: (Long) -> Unit,
    onDeleteRequest: () -> Unit,
    showQuickActions: Boolean
) {
    val dueDateLabel = when {
        item.dueDate == LocalDate.now() -> rowTodayText
        !item.dueDateText.isNullOrBlank() -> formatDueDateLabel(item.dueDateText)
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
    val sectionedIds = (overdue + timedToday + todayUntimed).mapTo(mutableSetOf()) { it.id }
    val highPriority = items.filter {
        !it.isDone &&
            it.priority == TodoPriority.HIGH &&
            it.id !in sectionedIds
    }
    return listOf(
        TodayPlannerSection(R.string.todo_today_section_overdue, overdue),
        TodayPlannerSection(R.string.todo_today_section_timed, timedToday),
        TodayPlannerSection(R.string.todo_today_section_today, todayUntimed),
        TodayPlannerSection(R.string.todo_today_section_high_priority, highPriority)
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
    completedTitle: String,
    completedSubtitle: String
): Pair<String, String> = when (filter) {
    TodoFilter.ALL -> allTitle to allSubtitle
    TodoFilter.TODAY -> {
        val subtitle = java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern("MMMM d", java.util.Locale.getDefault())
        )
        todayTitle to subtitle
    }
    TodoFilter.COMPLETED -> completedTitle to completedSubtitle
}

private fun completionProgress(uiState: TodoListUiState): Float {
    if (uiState.items.isEmpty()) return 0f
    return uiState.items.count { it.isDone }.toFloat() / uiState.items.size.toFloat()
}

private fun formatDueDateLabel(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val parsed = java.time.LocalDate.parse(raw, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        parsed.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()))
    }.getOrDefault(raw)
}

private fun buildDueLabel(dueDate: String?, dueTime: String?): String? {
    if (dueDate.isNullOrBlank()) return null
    return if (dueTime.isNullOrBlank()) dueDate else "$dueDate $dueTime"
}
