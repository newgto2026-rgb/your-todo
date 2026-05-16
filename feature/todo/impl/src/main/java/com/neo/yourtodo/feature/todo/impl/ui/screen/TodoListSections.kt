package com.neo.yourtodo.feature.todo.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.model.TodoItemUiModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
internal fun TodoListSectionHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF4A5161)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFDCE2EC)
        )
    }
}

@Composable
internal fun TodoListSectionKey.label(dueDateFormat: String): String = when (this) {
    TodoListSectionKey.Open -> stringResource(R.string.todo_section_open)
    TodoListSectionKey.Completed -> stringResource(R.string.todo_filter_completed)
    is TodoListSectionKey.Priority -> when (priority) {
        TodoPriority.HIGH -> stringResource(R.string.todo_section_priority_high)
        TodoPriority.MEDIUM -> stringResource(R.string.todo_section_priority_medium)
        TodoPriority.LOW -> stringResource(R.string.todo_section_priority_low)
    }
    is TodoListSectionKey.DueDate -> date?.let {
        formatDateLabel(it, dueDateFormat)
    } ?: stringResource(R.string.todo_section_no_due_date)
    is TodoListSectionKey.Friend -> nickname ?: stringResource(R.string.todo_section_unknown_friend)
    TodoListSectionKey.Self -> stringResource(R.string.todo_section_self)
}

internal fun headerTextFor(
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

internal fun completionProgress(uiState: TodoListUiState): Float {
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
