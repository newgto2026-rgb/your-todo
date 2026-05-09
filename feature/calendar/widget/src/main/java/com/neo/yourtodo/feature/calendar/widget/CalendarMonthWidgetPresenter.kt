package com.neo.yourtodo.feature.calendar.widget

import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first

internal class CalendarMonthWidgetPresenter @Inject constructor(
    private val summarySource: CalendarMonthSummarySource,
    private val getAssignedTodosUseCase: GetAssignedTodosUseCase,
    private val clock: Clock
) {
    suspend fun present(
        locale: Locale = Locale.getDefault(),
        displayedMonth: YearMonth? = null
    ): CalendarMonthWidgetState {
        val today = LocalDate.now(clock)
        val currentMonth = displayedMonth ?: YearMonth.from(today)
        val monthLabel = currentMonth.atDay(1)
            .format(DateTimeFormatter.ofPattern("yyyy MMMM", locale))

        return runCatching {
            val summaries = summarySource.summariesFor(currentMonth)
                .withAssignedTodos(
                    yearMonth = currentMonth,
                    assignedTodos = getAssignedTodosUseCase.observeVisibleReceived().first()
                )
            CalendarMonthWidgetState(
                monthLabel = monthLabel,
                weekdayLabels = buildWeekdayLabels(locale),
                weeks = buildCalendarMonthWidgetDateGrid(
                    yearMonth = currentMonth,
                    locale = locale
                ).map { week ->
                    week.map { cell ->
                        val summary = summaries[cell.date]
                        CalendarMonthWidgetDay(
                            date = cell.date,
                            dayLabel = cell.date.dayOfMonth.toString(),
                            taskCountLabel = summary?.totalCount?.toTaskCountLabel(),
                            todoChips = summary?.toTodoChips().orEmpty(),
                            isCurrentMonth = cell.isCurrentMonth,
                            isToday = cell.date == today
                        )
                    }
                }
            )
        }.getOrElse {
            CalendarMonthWidgetState(
                monthLabel = monthLabel,
                weekdayLabels = buildWeekdayLabels(locale),
                weeks = emptyList(),
                isError = true
            )
        }
    }

    private fun buildWeekdayLabels(locale: Locale): List<String> {
        val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
        return List(WEEK_DAY_COUNT) { offset ->
            firstDayOfWeek
                .plus(offset.toLong())
                .getDisplayName(TextStyle.NARROW_STANDALONE, locale)
        }
    }

    private fun Int.toTaskCountLabel(): String? = when {
        this <= 0 -> null
        this > MAX_VISIBLE_TASK_COUNT -> "$MAX_VISIBLE_TASK_COUNT+"
        else -> toString()
    }

    private fun DateTodoSummary.toTodoChips(): List<CalendarMonthWidgetTodoChip> {
        val total = totalCount
        if (total <= 0) return emptyList()

        val sortedTodos = todos.sortedWith(todoPreviewComparator)
        val visibleTodoCount = if (total > MAX_EXPANDED_TODO_LINES) {
            MAX_EXPANDED_TODO_LINES - 1
        } else {
            MAX_EXPANDED_TODO_LINES
        }
        val todoChips = sortedTodos
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

    private fun Map<LocalDate, DateTodoSummary>.withAssignedTodos(
        yearMonth: YearMonth,
        assignedTodos: List<AssignedTodo>
    ): Map<LocalDate, DateTodoSummary> {
        val mutable = toMutableMap()
        assignedTodos
            .filter { it.dueDate != null && YearMonth.from(it.dueDate) == yearMonth }
            .groupBy { checkNotNull(it.dueDate) }
            .forEach { (date, dateAssignedTodos) ->
                val existing = mutable[date]
                val assignedSummaries = dateAssignedTodos.map {
                    TodoSummary(
                        id = stableAssignedRowId(it.id),
                        title = it.title,
                        isDone = it.isDone,
                        dueTimeMinutes = it.dueTimeMinutes,
                        priority = it.priority,
                        createdAt = it.createdAt?.toEpochMilli() ?: 0L
                    )
                }
                val todos = existing?.todos.orEmpty() + assignedSummaries
                val indicatorCount = minOf(todos.size, 3)
                mutable[date] = DateTodoSummary(
                    date = date,
                    todos = todos,
                    indicatorCount = indicatorCount,
                    overflowCount = kotlin.math.max(todos.size - indicatorCount, 0)
                )
            }
        return mutable
    }

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

    private companion object {
        private const val WEEK_DAY_COUNT = 7
        private const val MAX_VISIBLE_TASK_COUNT = 9
        private const val MAX_EXPANDED_TODO_LINES = 4
    }
}
