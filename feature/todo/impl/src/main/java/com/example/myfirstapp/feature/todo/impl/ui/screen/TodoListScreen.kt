package com.example.myfirstapp.feature.todo.impl.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
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
    initialEditTodoId: Long? = null,
    initialAddDueDate: LocalDate? = null,
    isEditOnlyEntry: Boolean = false,
    onEditOnlyExit: (() -> Unit)? = null,
    onBackBlockedChange: (Boolean) -> Unit = {},
    viewModel: TodoListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isModalVisible = uiState.isEditDialogVisible
    var hasHandledInitialEdit by remember(initialEditTodoId) {
        mutableStateOf(initialEditTodoId == null)
    }
    var hasHandledInitialAdd by remember(initialAddDueDate) {
        mutableStateOf(initialAddDueDate == null)
    }
    var seenEditSheetInEditOnly by remember { mutableStateOf(false) }

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

    LaunchedEffect(initialEditTodoId, uiState.items, hasHandledInitialEdit) {
        if (
            !hasHandledInitialEdit &&
            initialEditTodoId != null &&
            uiState.items.any { item -> item.id == initialEditTodoId }
        ) {
            viewModel.onAction(TodoListAction.OnEditClick(initialEditTodoId))
            hasHandledInitialEdit = true
        }
    }

    LaunchedEffect(initialAddDueDate, hasHandledInitialAdd) {
        if (!hasHandledInitialAdd && initialAddDueDate != null) {
            viewModel.onAction(TodoListAction.OnAddForDateClick(initialAddDueDate))
            hasHandledInitialAdd = true
        }
    }

    LaunchedEffect(isModalVisible, onBackBlockedChange) {
        onBackBlockedChange(isModalVisible)
    }

    BackHandler(enabled = uiState.isEditDialogVisible) {
        viewModel.onAction(TodoListAction.OnDismissDialog)
    }

    LaunchedEffect(isEditOnlyEntry, isModalVisible, seenEditSheetInEditOnly) {
        if (!isEditOnlyEntry) return@LaunchedEffect
        if (isModalVisible) {
            seenEditSheetInEditOnly = true
            return@LaunchedEffect
        }
        if (seenEditSheetInEditOnly) {
            seenEditSheetInEditOnly = false
            onEditOnlyExit?.invoke()
        }
    }

    DisposableEffect(onBackBlockedChange) {
        onDispose {
            onBackBlockedChange(false)
        }
    }

    TodoListScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        showListContent = !isEditOnlyEntry
    )
}

@Composable
private fun TodoListScreen(
    uiState: TodoListUiState,
    onAction: (TodoListAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    showListContent: Boolean
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

    if (showListContent) {
        Scaffold(
            containerColor = Color(0xFFF5F6FB),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { onAction(TodoListAction.OnAddClick) },
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
    }

    if (uiState.isEditDialogVisible) {
        EditTodoBottomSheet(
            sheetTitle = stringResource(
                if (uiState.editingItem == null) {
                    R.string.todo_editor_title_new_task
                } else {
                    R.string.todo_editor_title_edit_task
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
            onDelete = {
                uiState.editingItem?.id?.let { onAction(TodoListAction.OnDeleteClick(it)) }
                onAction(TodoListAction.OnDismissDialog)
            },
            showDelete = uiState.editingItem != null
        )
    }
}

@Composable
private fun TodoPlannerRow(
    item: TodoItemUiModel,
    rowCompletedText: String,
    rowTodayText: String,
    onAction: (TodoListAction) -> Unit,
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
        TodoItemRow(
            title = item.title,
            dueDateText = rowDueLabel,
            reminderText = reminderLabel,
            isDone = item.isDone,
            isEmphasized = !item.isDone && dueDateLabel == rowTodayText,
            isReminderEnabled = item.isReminderEnabled,
            onToggleDone = { onAction(TodoListAction.OnToggleDone(item.id)) },
            onClick = { onAction(TodoListAction.OnEditClick(item.id)) },
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

private data class TodayPlannerSection(
    val titleRes: Int,
    val items: List<TodoItemUiModel>
)

private fun todayPlannerSections(items: List<TodoItemUiModel>): List<TodayPlannerSection> {
    val today = LocalDate.now()
    val overdue = items.filter { !it.isDone && it.dueDate?.isBefore(today) == true }
    val timedToday = items.filter {
        !it.isDone && it.dueDate == today && !it.dueTimeText.isNullOrBlank()
    }
    val todayUntimed = items.filter {
        !it.isDone && it.dueDate == today && it.dueTimeText.isNullOrBlank()
    }
    val highPriority = items.filter {
        !it.isDone &&
            it.priority == TodoPriority.HIGH &&
            it.dueDate != today &&
            it.dueDate?.isBefore(today) != true
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

private fun isToday(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    return runCatching {
        java.time.LocalDate.parse(raw, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) == java.time.LocalDate.now()
    }.getOrDefault(false)
}
