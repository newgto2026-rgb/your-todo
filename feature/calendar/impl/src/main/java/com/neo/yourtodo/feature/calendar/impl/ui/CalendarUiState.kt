package com.neo.yourtodo.feature.calendar.impl.ui

import androidx.compose.runtime.Immutable
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import java.time.LocalDate
import java.time.YearMonth

@Immutable
data class CalendarUiState(
    val profileInitial: String? = null,
    val currentMonth: YearMonth,
    val selectedDate: LocalDate,
    val isMonthExpanded: Boolean = true,
    val days: List<CalendarDayUiModel>,
    val selectedWeekDays: List<CalendarDayUiModel> = emptyList(),
    val summariesByDate: Map<LocalDate, DateTodoSummary>,
    val todayTaskCount: Int,
    val selectedDateTodos: List<CalendarSelectedTodoUiModel>,
    val selectedDateTodoSections: List<CalendarAgendaSectionUiModel> = emptyList(),
    val isFriendTodosExpanded: Boolean = false,
    val isSyncing: Boolean = false
)

@Immutable
data class CalendarDayUiModel(
    val date: LocalDate?,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val indicatorCount: Int,
    val overflowCount: Int
)

@Immutable
data class CalendarSelectedTodoUiModel(
    val id: Long,
    val itemKey: String = id.toString(),
    val title: String,
    val isDone: Boolean,
    val priority: TodoPriority,
    val isReminderEnabled: Boolean,
    val dueTimeLabel: String?,
    val reminderLeadMinutes: Int?,
    val sourceLabel: String? = null,
    val source: CalendarTodoSource = CalendarTodoSource.MINE,
    val assignmentMode: AssignmentMode = AssignmentMode.REQUEST,
    val assignedTodoId: String? = null
)

@Immutable
data class CalendarAgendaSectionUiModel(
    val source: CalendarTodoSource,
    val totalCount: Int,
    val visibleTodos: List<CalendarSelectedTodoUiModel>,
    val isCollapsible: Boolean,
    val isExpanded: Boolean
)

enum class CalendarTodoSource {
    MINE,
    FRIEND
}
