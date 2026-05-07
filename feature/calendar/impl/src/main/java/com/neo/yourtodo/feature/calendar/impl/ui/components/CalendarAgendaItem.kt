package com.neo.yourtodo.feature.calendar.impl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.feature.calendar.impl.R
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarSelectedTodoUiModel

@Composable
internal fun CalendarAgendaItem(
    todo: CalendarSelectedTodoUiModel,
    onClick: () -> Unit,
    onToggleDone: () -> Unit
) {
    val dueText = todo.dueTimeLabel ?: stringResource(R.string.calendar_bottom_sheet_all_day)
    val toggleLabel = stringResource(
        if (todo.isDone) {
            R.string.calendar_mark_task_incomplete
        } else {
            R.string.calendar_mark_task_complete
        }
    )
    val reminderText = when (todo.reminderLeadMinutes) {
        0 -> stringResource(R.string.calendar_reminder_lead_at_time)
        5 -> stringResource(R.string.calendar_reminder_lead_5m)
        10 -> stringResource(R.string.calendar_reminder_lead_10m)
        30 -> stringResource(R.string.calendar_reminder_lead_30m)
        60 -> stringResource(R.string.calendar_reminder_lead_60m)
        else -> null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_day_todo_item_${todo.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (todo.isDone) Color(0xFFF1F4F8) else Color.White
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (todo.isDone) {
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(width = 2.dp, height = 64.dp)
                        .background(Color(0xFF6C63FF).copy(alpha = 0.85f), RoundedCornerShape(999.dp))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("calendar_day_todo_toggle_${todo.id}")
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (todo.isDone) Color(0xFF6C63FF) else Color.Transparent)
                        .border(
                            width = if (todo.isDone) 0.dp else 1.8.dp,
                            color = Color(0xFF6C63FF),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .clickable(
                            onClickLabel = toggleLabel,
                            role = Role.Checkbox,
                            onClick = onToggleDone
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (todo.isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = todo.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (todo.isDone) {
                                Color(0xFF2D3338).copy(alpha = 0.42f)
                            } else {
                                Color(0xFF2D3338)
                            },
                            textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = priorityColor(todo.priority).copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = priorityLabel(todo.priority),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = priorityColor(todo.priority)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF5A6065),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = dueText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5A6065).copy(alpha = if (todo.isDone) 0.6f else 1f)
                        )
                        if (todo.isReminderEnabled && !reminderText.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color(0xFF5A6065).copy(alpha = if (todo.isDone) 0.6f else 1f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = reminderText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5A6065).copy(alpha = if (todo.isDone) 0.6f else 1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun priorityLabel(priority: TodoPriority): String = when (priority) {
    TodoPriority.LOW -> stringResource(R.string.calendar_priority_low)
    TodoPriority.MEDIUM -> stringResource(R.string.calendar_priority_medium)
    TodoPriority.HIGH -> stringResource(R.string.calendar_priority_high)
}

private fun priorityColor(priority: TodoPriority): Color = when (priority) {
    TodoPriority.LOW -> Color(0xFF6E8E72)
    TodoPriority.MEDIUM -> Color(0xFF8B7A4E)
    TodoPriority.HIGH -> Color(0xFF9B4B4B)
}
