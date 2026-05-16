package com.neo.yourtodo.feature.calendar.impl.ui

import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max
import kotlin.math.min

internal fun buildSelectedDateTodos(
    selectedDate: LocalDate,
    localTodos: List<TodoItem>,
    assignedTodos: List<AssignedTodo>
): List<CalendarSelectedTodoUiModel> {
    val selectedLocalTodos = localTodos
        .asSequence()
        .filter { it.dueDate == selectedDate }
        .sortedWith(localTodoComparator)
        .map { it.toSelectedTodoUiModel() }
        .toList()

    val selectedAssignedTodos = assignedTodos
        .asSequence()
        .filter { it.dueDate == selectedDate }
        .sortedWith(assignedTodoComparator)
        .map { it.toSelectedTodoUiModel() }
        .toList()

    return selectedLocalTodos + selectedAssignedTodos
}

internal fun TodoItem.toSelectedTodoUiModel(): CalendarSelectedTodoUiModel =
    CalendarSelectedTodoUiModel(
        id = id,
        title = title,
        isDone = isDone,
        priority = priority,
        isReminderEnabled = isReminderEnabled,
        dueTimeLabel = dueTimeMinutes?.let(::formatLocalTimeFromMinutes)
            ?: reminderAtEpochMillis?.let(::formatLocalTimeFromEpochMillis),
        reminderLeadMinutes = reminderLeadMinutes
    )

internal fun AssignedTodo.toSelectedTodoUiModel(): CalendarSelectedTodoUiModel {
    val reminderEpochMillis = reminder
        ?.takeIf { it.enabled }
        ?.reminderAt
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    return CalendarSelectedTodoUiModel(
        id = stableAssignedRowId(id),
        title = title,
        isDone = isDone,
        priority = priority,
        isReminderEnabled = reminder?.enabled == true,
        dueTimeLabel = dueTimeMinutes?.let(::formatLocalTimeFromMinutes),
        reminderLeadMinutes = reminderLeadMinutes(reminderEpochMillis),
        sourceLabel = sender?.nickname?.let { "@$it" },
        assignmentMode = assignmentMode,
        assignedTodoId = id
    )
}

internal fun Map<LocalDate, DateTodoSummary>.withAssignedTodos(
    yearMonth: YearMonth,
    assignedTodos: List<AssignedTodo>
): Map<LocalDate, DateTodoSummary> {
    val mutable = toMutableMap()
    assignedTodos
        .filter { it.dueDate != null && YearMonth.from(it.dueDate) == yearMonth }
        .groupBy { checkNotNull(it.dueDate) }
        .forEach { (date, dateAssignedTodos) ->
            val existing = mutable[date]
            val assignedSummaries = dateAssignedTodos.map { it.toTodoSummary() }
            val todos = existing?.todos.orEmpty() + assignedSummaries
            val indicatorCount = min(todos.size, MAX_INLINE_INDICATORS)
            mutable[date] = DateTodoSummary(
                date = date,
                todos = todos,
                indicatorCount = indicatorCount,
                overflowCount = max(todos.size - indicatorCount, 0)
            )
        }
    return mutable
}

internal fun TodoPriority.sortRank(): Int = when (this) {
    TodoPriority.HIGH -> 3
    TodoPriority.MEDIUM -> 2
    TodoPriority.LOW -> 1
}

private val localTodoComparator: Comparator<TodoItem> =
    compareBy<TodoItem> { it.isDone }
        .thenByDescending { it.priority.sortRank() }
        .thenBy { it.id }

private val assignedTodoComparator: Comparator<AssignedTodo> =
    compareBy<AssignedTodo> { it.isDone }
        .thenByDescending { it.priority.sortRank() }
        .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
        .thenBy { it.title }

private fun AssignedTodo.toTodoSummary(): TodoSummary =
    TodoSummary(
        id = stableAssignedRowId(id),
        title = title,
        isDone = isDone,
        dueTimeMinutes = dueTimeMinutes,
        priority = priority
    )

private fun AssignedTodo.reminderLeadMinutes(reminderEpochMillis: Long?): Int? {
    val dueDate = dueDate ?: return null
    val dueTimeMinutes = dueTimeMinutes ?: return null
    val reminderMillis = reminderEpochMillis ?: return null
    val dueMillis = dueDate
        .atTime(LocalTime.of(dueTimeMinutes / MINUTES_PER_HOUR, dueTimeMinutes % MINUTES_PER_HOUR))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val leadMinutes = ((dueMillis - reminderMillis) / MILLIS_PER_MINUTE).toInt()
    return leadMinutes.takeIf { it in SupportedReminderLeadMinutes }
}

private fun stableAssignedRowId(id: String): Long {
    val positiveHash = id.hashCode().toLong().let { if (it == Long.MIN_VALUE) 0 else kotlin.math.abs(it) }
    return -positiveHash - 1
}

private fun formatLocalTimeFromMinutes(minutes: Int): String {
    val normalized = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    return LocalTime.of(normalized / MINUTES_PER_HOUR, normalized % MINUTES_PER_HOUR)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

private fun formatLocalTimeFromEpochMillis(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private val SupportedReminderLeadMinutes = setOf(0, 5, 10, 30, 60)
private const val MAX_INLINE_INDICATORS = 3
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val MILLIS_PER_MINUTE = 60_000L
