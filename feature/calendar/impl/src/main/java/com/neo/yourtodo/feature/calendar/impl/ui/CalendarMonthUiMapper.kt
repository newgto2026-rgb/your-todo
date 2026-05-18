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
    isMonthExpanded: Boolean = true,
    isFriendTodosExpanded: Boolean = false,
    today: LocalDate = LocalDate.now()
): CalendarUiState {
    val adjustedSelectedDate = selectedDate.normalizeToMonth(currentMonth)
    val days = buildMonthCells(
        yearMonth = currentMonth,
        selectedDate = adjustedSelectedDate,
        today = today,
        summariesByDate = summariesByDate
    )
    return CalendarUiState(
        profileInitial = profileInitial,
        currentMonth = currentMonth,
        selectedDate = adjustedSelectedDate,
        isMonthExpanded = isMonthExpanded,
        days = days,
        selectedWeekDays = days.selectedWeekDays(adjustedSelectedDate),
        summariesByDate = summariesByDate,
        todayTaskCount = summariesByDate.todayTaskCount(today),
        selectedDateTodos = selectedDateTodos,
        selectedDateTodoSections = selectedDateTodos.toAgendaSections(
            isFriendTodosExpanded = isFriendTodosExpanded
        ),
        isFriendTodosExpanded = isFriendTodosExpanded
    )
}

internal fun initialCalendarUiState(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    isMonthExpanded: Boolean = true
): CalendarUiState =
    buildCalendarUiState(
        profileInitial = null,
        currentMonth = currentMonth,
        selectedDate = selectedDate,
        summariesByDate = emptyMap(),
        selectedDateTodos = emptyList(),
        isMonthExpanded = isMonthExpanded
    )

internal fun List<CalendarDayUiModel>.selectedWeekDays(selectedDate: LocalDate): List<CalendarDayUiModel> =
    chunked(WEEK_DAY_COUNT)
        .firstOrNull { week -> week.any { it.date == selectedDate } }
        ?: take(WEEK_DAY_COUNT)

internal fun List<CalendarSelectedTodoUiModel>.toAgendaSections(
    isFriendTodosExpanded: Boolean
): List<CalendarAgendaSectionUiModel> {
    val myTodos = filter { it.source == CalendarTodoSource.MINE }
        .withCompletedLast()
    val friendTodos = filter { it.source == CalendarTodoSource.FRIEND }
        .withCompletedLast()
    return buildList {
        if (myTodos.isNotEmpty()) {
            add(
                CalendarAgendaSectionUiModel(
                    source = CalendarTodoSource.MINE,
                    totalCount = myTodos.size,
                    visibleTodos = myTodos,
                    isCollapsible = false,
                    isExpanded = true
                )
            )
        }
        if (friendTodos.isNotEmpty()) {
            val isCollapsible = friendTodos.size > FRIEND_TODOS_COLLAPSED_COUNT
            add(
                CalendarAgendaSectionUiModel(
                    source = CalendarTodoSource.FRIEND,
                    totalCount = friendTodos.size,
                    visibleTodos = if (isCollapsible && !isFriendTodosExpanded) {
                        friendTodos.take(FRIEND_TODOS_COLLAPSED_COUNT)
                    } else {
                        friendTodos
                    },
                    isCollapsible = isCollapsible,
                    isExpanded = !isCollapsible || isFriendTodosExpanded
                )
            )
        }
    }
}

private fun List<CalendarSelectedTodoUiModel>.withCompletedLast(): List<CalendarSelectedTodoUiModel> =
    sortedBy { it.isDone }

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
private const val FRIEND_TODOS_COLLAPSED_COUNT = 3
