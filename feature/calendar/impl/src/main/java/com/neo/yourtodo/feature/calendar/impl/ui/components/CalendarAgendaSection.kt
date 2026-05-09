package com.neo.yourtodo.feature.calendar.impl.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import com.neo.yourtodo.feature.calendar.impl.ui.CalendarSelectedTodoUiModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CalendarAgendaSection(
    selectedDate: LocalDate,
    selectedDateTodos: List<CalendarSelectedTodoUiModel>,
    onTodoClick: (CalendarSelectedTodoUiModel) -> Unit,
    onToggleTodoDone: (CalendarSelectedTodoUiModel) -> Unit,
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
                items(items = selectedDateTodos, key = { it.id }) { todo ->
                    CalendarAgendaItem(
                        todo = todo,
                        onClick = { onTodoClick(todo) },
                        onToggleDone = { onToggleTodoDone(todo) }
                    )
                }
                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

private fun LocalDate.formatSelectedDateLabel(locale: Locale): String {
    val pattern = if (locale.language == Locale.KOREAN.language) {
        "yyyy년 M월 d일 (E)"
    } else {
        "yyyy MMM d (E)"
    }
    return format(DateTimeFormatter.ofPattern(pattern, locale))
}
