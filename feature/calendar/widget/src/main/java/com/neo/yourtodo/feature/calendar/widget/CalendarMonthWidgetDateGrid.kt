package com.neo.yourtodo.feature.calendar.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

internal data class CalendarMonthWidgetDateCell(
    val date: LocalDate,
    val isCurrentMonth: Boolean
)

internal fun buildCalendarMonthWidgetDateGrid(
    yearMonth: YearMonth,
    locale: Locale
): List<List<CalendarMonthWidgetDateCell>> {
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val firstDate = yearMonth.atDay(1)
    val leadingCells = firstDate.dayOfWeek.distanceFrom(firstDayOfWeek)
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = ((leadingCells + daysInMonth + 6) / 7) * 7
    val previousMonth = yearMonth.minusMonths(1)
    val previousMonthDays = previousMonth.lengthOfMonth()
    val nextMonth = yearMonth.plusMonths(1)

    return List(totalCells) { index ->
        val dayOfMonth = index - leadingCells + 1
        val isCurrentMonth = dayOfMonth in 1..daysInMonth
        val date = when {
            dayOfMonth < 1 -> previousMonth.atDay(previousMonthDays + dayOfMonth)
            dayOfMonth > daysInMonth -> nextMonth.atDay(dayOfMonth - daysInMonth)
            else -> yearMonth.atDay(dayOfMonth)
        }
        CalendarMonthWidgetDateCell(
            date = date,
            isCurrentMonth = isCurrentMonth
        )
    }.chunked(WEEK_DAY_COUNT)
}

internal fun DayOfWeek.distanceFrom(other: DayOfWeek): Int =
    (value - other.value + WEEK_DAY_COUNT) % WEEK_DAY_COUNT

private const val WEEK_DAY_COUNT = 7
