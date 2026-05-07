package com.neo.yourtodo.feature.calendar.widget

import java.time.LocalDate

internal data class CalendarMonthWidgetState(
    val monthLabel: String,
    val weekdayLabels: List<String>,
    val weeks: List<List<CalendarMonthWidgetDay>>,
    val isError: Boolean = false
)

internal data class CalendarMonthWidgetDay(
    val date: LocalDate,
    val dayLabel: String,
    val taskCountLabel: String?,
    val isCurrentMonth: Boolean,
    val isToday: Boolean
)
