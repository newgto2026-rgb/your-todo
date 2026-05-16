package com.neo.yourtodo.feature.calendar.impl.ui

import com.neo.yourtodo.core.model.DateTodoSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min

internal fun buildCalendarUiState(
    profileInitial: String?,
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    summariesByDate: Map<LocalDate, DateTodoSummary>,
    selectedDateTodos: List<CalendarSelectedTodoUiModel>,
    today: LocalDate = LocalDate.now()
): CalendarUiState {
    val adjustedSelectedDate = selectedDate.normalizeToMonth(currentMonth)
    return CalendarUiState(
        profileInitial = profileInitial,
        currentMonth = currentMonth,
        selectedDate = adjustedSelectedDate,
        days = buildMonthCells(
            yearMonth = currentMonth,
            selectedDate = adjustedSelectedDate,
            today = today,
            summariesByDate = summariesByDate
        ),
        summariesByDate = summariesByDate,
        todayTaskCount = summariesByDate.todayTaskCount(today),
        selectedDateTodos = selectedDateTodos
    )
}

internal fun initialCalendarUiState(
    currentMonth: YearMonth,
    selectedDate: LocalDate
): CalendarUiState =
    buildCalendarUiState(
        profileInitial = null,
        currentMonth = currentMonth,
        selectedDate = selectedDate,
        summariesByDate = emptyMap(),
        selectedDateTodos = emptyList()
    )

internal fun buildMonthCells(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    summariesByDate: Map<LocalDate, DateTodoSummary>
): List<CalendarDayUiModel> {
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val firstDate = yearMonth.atDay(1)
    val leadingBlanks = firstDate.dayOfWeek.distanceFrom(firstDayOfWeek)
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = ((leadingBlanks + daysInMonth + 6) / WEEK_DAY_COUNT) * WEEK_DAY_COUNT
    val previousMonth = yearMonth.minusMonths(1)
    val nextMonth = yearMonth.plusMonths(1)
    val previousMonthDays = previousMonth.lengthOfMonth()

    return List(totalCells) { index ->
        val dayOfMonth = index - leadingBlanks + 1
        val isCurrentMonth = dayOfMonth in 1..daysInMonth
        val date = when {
            dayOfMonth < 1 -> previousMonth.atDay(previousMonthDays + dayOfMonth)
            dayOfMonth > daysInMonth -> nextMonth.atDay(dayOfMonth - daysInMonth)
            else -> yearMonth.atDay(dayOfMonth)
        }
        val summary = summariesByDate[date]

        CalendarDayUiModel(
            date = date,
            isCurrentMonth = isCurrentMonth,
            isToday = date == today,
            isSelected = isCurrentMonth && date == selectedDate,
            indicatorCount = if (isCurrentMonth) summary?.indicatorCount ?: 0 else 0,
            overflowCount = if (isCurrentMonth) summary?.overflowCount ?: 0 else 0
        )
    }
}

internal fun LocalDate.normalizeToMonth(targetMonth: YearMonth): LocalDate {
    val normalizedDay = min(dayOfMonth, targetMonth.lengthOfMonth())
    return targetMonth.atDay(normalizedDay)
}

internal fun DayOfWeek.distanceFrom(other: DayOfWeek): Int =
    (value - other.value + WEEK_DAY_COUNT) % WEEK_DAY_COUNT

internal fun Map<LocalDate, DateTodoSummary>.todayTaskCount(today: LocalDate): Int {
    val summary = this[today] ?: return 0
    return summary.indicatorCount + summary.overflowCount
}

private const val WEEK_DAY_COUNT = 7
