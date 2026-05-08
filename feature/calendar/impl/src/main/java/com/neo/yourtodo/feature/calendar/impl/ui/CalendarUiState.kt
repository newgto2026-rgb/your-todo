package com.neo.yourtodo.feature.calendar.impl.ui

import androidx.compose.runtime.Immutable
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoPriority
import java.time.LocalDate
import java.time.YearMonth

@Immutable
data class CalendarUiState(
    val profileInitial: String? = null,
    val currentMonth: YearMonth,
    val selectedDate: LocalDate,
    val days: List<CalendarDayUiModel>,
    val summariesByDate: Map<LocalDate, DateTodoSummary>,
    val todayTaskCount: Int,
    val selectedDateTodos: List<CalendarSelectedTodoUiModel>
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
    val title: String,
    val isDone: Boolean,
    val priority: TodoPriority,
    val isReminderEnabled: Boolean,
    val dueTimeLabel: String?,
    val reminderLeadMinutes: Int?
)
