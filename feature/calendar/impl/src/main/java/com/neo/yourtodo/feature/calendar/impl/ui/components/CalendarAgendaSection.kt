package com.neo.yourtodo.feature.calendar.impl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.feature.calendar.impl.R
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarAgendaSectionUiModel
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarSelectedTodoUiModel
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarTodoSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CalendarAgendaSection(
    selectedDate: LocalDate,
    selectedDateTodos: List<CalendarSelectedTodoUiModel>,
    selectedDateTodoSections: List<CalendarAgendaSectionUiModel>,
    onTodoClick: (CalendarSelectedTodoUiModel) -> Unit,
    onToggleTodoDone: (CalendarSelectedTodoUiModel) -> Unit,
    onToggleFriendTodosExpanded: () -> Unit,
    onAddTodoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val selectedDateLabel = selectedDate.formatSelectedDateLabel(locale)
    val selectedDateCount = selectedDateTodos.size

    Column(modifier = modifier.testTag("calendar_day_todo_sheet")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.calendar_agenda_date_label, selectedDateLabel),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.testTag("calendar_day_todo_list_title")
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFD8E2FF)
                    ) {
                        Text(
                            text = pluralStringResource(
                                id = R.plurals.calendar_agenda_task_count_badge,
                                count = selectedDateCount,
                                selectedDateCount
                            ),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF45526D)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.calendar_agenda_title),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF5A6065)
                )
            }
            IconButton(
                onClick = onAddTodoClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("calendar_add_todo_for_date")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.calendar_add_task_for_date),
                    tint = Color(0xFF4A6697)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (selectedDateTodos.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_bottom_sheet_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5A6065),
                modifier = Modifier
                    .testTag("calendar_day_todo_list_empty")
                    .padding(bottom = 20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                selectedDateTodoSections.forEach { section ->
                    item(key = "section_${section.source}") {
                        CalendarAgendaSectionHeader(
                            section = section,
                            onToggleFriendTodosExpanded = onToggleFriendTodosExpanded
                        )
                    }
                    items(
                        items = section.visibleTodos,
                        key = { todo -> "${section.source}_${todo.itemKey}" }
                    ) { todo ->
                        val isEditable = section.source == CalendarTodoSource.MINE
                        CalendarAgendaItem(
                            todo = todo,
                            isEditable = isEditable,
                            onClick = { onTodoClick(todo) },
                            onToggleDone = { onToggleTodoDone(todo) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun CalendarAgendaSectionHeader(
    section: CalendarAgendaSectionUiModel,
    onToggleFriendTodosExpanded: () -> Unit
) {
    val isFriendSection = section.source == CalendarTodoSource.FRIEND
    val accentColor = if (isFriendSection) Color(0xFF3C7766) else Color(0xFF526585)
    val containerColor = if (isFriendSection) Color(0xFFF8FBFA) else Color(0xFFF7F9FD)
    val borderColor = if (isFriendSection) Color(0xFFE1EDE7) else Color(0xFFE2E8F2)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 22.dp)
                        .background(accentColor, RoundedCornerShape(999.dp))
                )
                Text(
                    text = section.source.title(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF26313D)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.74f)
                ) {
                    Text(
                        text = section.totalCount.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accentColor
                    )
                }
            }
            if (section.isCollapsible) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.clickable(onClick = onToggleFriendTodosExpanded)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (section.isExpanded) {
                                stringResource(R.string.calendar_friend_tasks_collapse)
                            } else {
                                stringResource(R.string.calendar_friend_tasks_expand, section.totalCount)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                        Icon(
                            imageVector = if (section.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarTodoSource.title(): String = when (this) {
    CalendarTodoSource.MINE -> stringResource(R.string.calendar_agenda_my_tasks)
    CalendarTodoSource.FRIEND -> stringResource(R.string.calendar_agenda_friend_tasks)
}

private fun LocalDate.formatSelectedDateLabel(locale: Locale): String {
    val pattern = if (locale.language == Locale.KOREAN.language) {
        "yyyy년 M월 d일 (E)"
    } else {
        "yyyy MMM d (E)"
    }
    return format(DateTimeFormatter.ofPattern(pattern, locale))
}
