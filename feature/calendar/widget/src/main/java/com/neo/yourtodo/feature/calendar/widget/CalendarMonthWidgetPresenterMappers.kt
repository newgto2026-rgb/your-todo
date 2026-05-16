package com.neo.yourtodo.feature.calendar.widget

import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.max

internal fun formatWidgetMonthLabel(
    yearMonth: YearMonth,
    locale: Locale
): String =
    yearMonth.atDay(1)
        .format(DateTimeFormatter.ofPattern("yyyy MMMM", locale))

internal fun buildCalendarMonthWidgetState(
    monthLabel: String,
    currentMonth: YearMonth,
    today: LocalDate,
    locale: Locale,
    summaries: Map<LocalDate, DateTodoSummary>
): CalendarMonthWidgetState =
    CalendarMonthWidgetState(
        monthLabel = monthLabel,
        weekdayLabels = buildWeekdayLabels(locale),
        weeks = buildCalendarMonthWidgetDateGrid(
            yearMonth = currentMonth,
            locale = locale
        ).map { week ->
            week.map { cell ->
                cell.toWidgetDay(
                    summary = summaries[cell.date],
                    today = today
                )
            }
        }
    )

internal fun calendarMonthWidgetPresentationErrorState(
    monthLabel: String,
    locale: Locale
): CalendarMonthWidgetState =
    CalendarMonthWidgetState(
        monthLabel = monthLabel,
        weekdayLabels = buildWeekdayLabels(locale),
        weeks = emptyList(),
        isError = true
    )

internal fun Map<LocalDate, DateTodoSummary>.withWidgetAssignedTodos(
    yearMonth: YearMonth,
    assignedTodos: List<AssignedTodo>
): Map<LocalDate, DateTodoSummary> {
    val mutable = toMutableMap()
    assignedTodos
        .filter { it.dueDate != null && YearMonth.from(it.dueDate) == yearMonth }
        .groupBy { checkNotNull(it.dueDate) }
        .forEach { (date, dateAssignedTodos) ->
            val existing = mutable[date]
            val assignedSummaries = dateAssignedTodos.map { it.toWidgetTodoSummary() }
            val todos = existing?.todos.orEmpty() + assignedSummaries
            val indicatorCount = minOf(todos.size, MAX_INLINE_INDICATORS)
            mutable[date] = DateTodoSummary(
                date = date,
                todos = todos,
                indicatorCount = indicatorCount,
                overflowCount = max(todos.size - indicatorCount, 0)
            )
        }
    return mutable
}

private fun buildWeekdayLabels(locale: Locale): List<String> {
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    return List(WEEK_DAY_COUNT) { offset ->
        firstDayOfWeek
            .plus(offset.toLong())
            .getDisplayName(TextStyle.NARROW_STANDALONE, locale)
    }
}

private fun CalendarMonthWidgetDateCell.toWidgetDay(
    summary: DateTodoSummary?,
    today: LocalDate
): CalendarMonthWidgetDay =
    CalendarMonthWidgetDay(
        date = date,
        dayLabel = date.dayOfMonth.toString(),
        taskCountLabel = summary?.totalCount?.toTaskCountLabel(),
        todoChips = summary?.toTodoChips().orEmpty(),
        isCurrentMonth = isCurrentMonth,
        isToday = date == today
    )

private fun Int.toTaskCountLabel(): String? = when {
    this <= 0 -> null
    this > MAX_VISIBLE_TASK_COUNT -> "$MAX_VISIBLE_TASK_COUNT+"
    else -> toString()
}

private fun DateTodoSummary.toTodoChips(): List<CalendarMonthWidgetTodoChip> {
    val total = totalCount
    if (total <= 0) return emptyList()

    val visibleTodoCount = if (total > MAX_EXPANDED_TODO_LINES) {
        MAX_EXPANDED_TODO_LINES - 1
    } else {
        MAX_EXPANDED_TODO_LINES
    }
    val todoChips = todos
        .sortedWith(todoPreviewComparator)
        .take(visibleTodoCount)
        .map { todo ->
            CalendarMonthWidgetTodoChip(
                label = todo.title,
                isDone = todo.isDone
            )
        }

    return if (total > MAX_EXPANDED_TODO_LINES) {
        todoChips + CalendarMonthWidgetTodoChip(
            label = "+${total - todoChips.size}",
            isOverflow = true
        )
    } else {
        todoChips
    }
}

private val DateTodoSummary.totalCount: Int
    get() = indicatorCount + overflowCount

private fun AssignedTodo.toWidgetTodoSummary(): TodoSummary =
    TodoSummary(
        id = stableAssignedRowId(id),
        title = title,
        isDone = isDone,
        dueTimeMinutes = dueTimeMinutes,
        priority = priority,
        createdAt = createdAt?.toEpochMilli() ?: 0L
    )

private val todoPreviewComparator: Comparator<TodoSummary> =
    compareBy<TodoSummary> { it.isDone }
        .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
        .thenByDescending { it.priority.sortRank() }
        .thenByDescending { it.createdAt }

private fun TodoPriority.sortRank(): Int = when (this) {
    TodoPriority.HIGH -> 3
    TodoPriority.MEDIUM -> 2
    TodoPriority.LOW -> 1
}

private fun stableAssignedRowId(id: String): Long {
    val positiveHash = id.hashCode().toLong().let { if (it == Long.MIN_VALUE) 0 else kotlin.math.abs(it) }
    return -positiveHash - 1
}

private const val WEEK_DAY_COUNT = 7
private const val MAX_VISIBLE_TASK_COUNT = 9
private const val MAX_EXPANDED_TODO_LINES = 4
private const val MAX_INLINE_INDICATORS = 3
