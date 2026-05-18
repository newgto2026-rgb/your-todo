package com.neo.yourtodo.feature.todo.impl.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.ui.TodoItemRow
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel
import com.neo.yourtodo.feature.todo.impl.ui.TodoListAction
import java.time.LocalDate

@Composable
internal fun TodoPlannerRow(
    item: TodoItemUiModel,
    rowCompletedText: String,
    rowTodayText: String,
    dueDateFormat: String,
    today: LocalDate,
    onAction: (TodoListAction) -> Unit,
    onEditRequested: (Long) -> Unit,
    onDeleteRequest: () -> Unit,
    showQuickActions: Boolean
) {
    val dueDateLabel = when {
        item.dueDate == today -> rowTodayText
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
        stringResource(
            if (item.assignmentMode == AssignmentMode.DIRECT) {
                R.string.todo_row_direct_assigned_from
            } else {
                R.string.todo_row_assigned_from
            },
            it
        )
    }
    val rowDueLabel = when {
        item.isDone -> rowCompletedText
        !dueLabel.isNullOrBlank() -> dueLabel
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LongPressDeleteTodoItemRow(
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
            priorityLabel = stringResource(item.priority.labelRes()),
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

@Composable
private fun LongPressDeleteTodoItemRow(
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
    var isDeleteVisible by rememberSaveable(itemId) { mutableStateOf(false) }

    TodoItemRow(
        title = title,
        dueDateText = dueDateText,
        reminderText = reminderText,
        isDone = isDone,
        isEmphasized = isEmphasized,
        isReminderEnabled = isReminderEnabled,
        onToggleDone = onToggleDone,
        onClick = {
            if (isDeleteVisible) {
                isDeleteVisible = false
            } else {
                onClick()
            }
        },
        onLongClick = { isDeleteVisible = true },
        modifier = Modifier.testTag("todo_row_$itemId"),
        priorityLabel = priorityLabel,
        priorityColor = priorityColor,
        toggleTestTag = "todo_row_toggle_$itemId",
        sourceText = assignedFromText,
        content = if (isDeleteVisible) {
            {
                IconButton(
                    onClick = {
                        isDeleteVisible = false
                        onDeleteRequest()
                    },
                    modifier = Modifier.testTag("todo_row_delete_$itemId")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.todo_editor_delete),
                        tint = Color(0xFFD85C5C)
                    )
                }
            }
        } else {
            null
        }
    )
}

private fun priorityColor(priority: TodoPriority): Color = when (priority) {
    TodoPriority.LOW -> Color(0xFF6FA58C)
    TodoPriority.MEDIUM -> Color(0xFF6F86C9)
    TodoPriority.HIGH -> Color(0xFFC76B7D)
}

private fun buildDueLabel(dueDate: String?, dueTime: String?): String? {
    if (dueDate.isNullOrBlank()) return null
    return if (dueTime.isNullOrBlank()) dueDate else "$dueDate $dueTime"
}
