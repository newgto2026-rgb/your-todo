package com.neo.yourtodo.feature.calendar.impl.ui

import com.neo.yourtodo.core.domain.usecase.TaskSurfaceItem
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoSummary
import com.neo.yourtodo.core.model.personvisibility.ObservedPersonTodos
import com.neo.yourtodo.core.model.personvisibility.ObservedTodo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

internal fun buildSelectedDateTodos(
    taskSurfaceItems: List<TaskSurfaceItem>
): List<CalendarSelectedTodoUiModel> =
    taskSurfaceItems.map { it.toSelectedTodoUiModel() }

private fun TaskSurfaceItem.toSelectedTodoUiModel(): CalendarSelectedTodoUiModel =
    CalendarSelectedTodoUiModel(
        id = id,
        title = title,
        isDone = isDone,
        priority = priority,
        isReminderEnabled = isReminderEnabled,
        dueTimeLabel = dueTimeMinutes?.let(::formatLocalTimeFromMinutes)
            ?: reminderFallbackTimeLabel(),
        reminderLeadMinutes = reminderLeadMinutes,
        sourceLabel = senderNickname?.let { "@$it" },
        assignmentMode = assignmentMode,
        assignedTodoId = assignedTodoId
    )

internal fun buildObservedSelectedDateTodos(
    observedPeople: List<ObservedPersonTodos>,
    selectedDate: LocalDate
): List<CalendarSelectedTodoUiModel> =
    observedPeople
                .flatMap { person ->
            person.todos
                .filter { it.dueDate == selectedDate }
                .map { it.toSelectedTodoUiModel() }
        }
        .sortedWith(observedSelectedDateComparator)

internal fun mergeObservedTodoSummaries(
    yearMonth: YearMonth,
    localSummaries: Map<LocalDate, DateTodoSummary>,
    observedPeople: List<ObservedPersonTodos>,
    maxIndicatorsPerDate: Int = 3
): Map<LocalDate, DateTodoSummary> {
    val safeMaxIndicators = maxIndicatorsPerDate.coerceAtLeast(0)
    val mutable = localSummaries.toMutableMap()
    observedPeople
        .flatMap { it.todos }
        .filter { it.dueDate != null && YearMonth.from(it.dueDate) == yearMonth }
        .groupBy { checkNotNull(it.dueDate) }
        .forEach { (date, observedTodos) ->
            val existing = mutable[date]
            val todos = existing?.todos.orEmpty() + observedTodos.map { it.toSummary() }
            val indicatorCount = min(todos.size, safeMaxIndicators)
            mutable[date] = DateTodoSummary(
                date = date,
                todos = todos,
                indicatorCount = indicatorCount,
                overflowCount = max(todos.size - indicatorCount, 0)
            )
        }
    return mutable
}

private fun ObservedTodo.toSelectedTodoUiModel(): CalendarSelectedTodoUiModel =
    CalendarSelectedTodoUiModel(
        id = stableNegativeId(id),
        itemKey = "observed_$id",
        title = title,
        isDone = isDone,
        priority = priority,
        isReminderEnabled = false,
        dueTimeLabel = dueTimeMinutes?.let(::formatLocalTimeFromMinutes),
        reminderLeadMinutes = null,
        sourceLabel = ownerNickname?.takeIf { it.isNotBlank() }?.let { "@$it" },
        source = CalendarTodoSource.FRIEND,
        assignedTodoId = null
    )

private fun ObservedTodo.toSummary(): TodoSummary =
    TodoSummary(
        id = stableNegativeId(id),
        title = title,
        isDone = isDone,
        dueTimeMinutes = dueTimeMinutes,
        priority = priority
    )

private fun TaskSurfaceItem.reminderFallbackTimeLabel(): String? =
    if (assignedTodoId == null) {
        reminderAtEpochMillis?.let(::formatLocalTimeFromEpochMillis)
    } else {
        null
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

private val observedSelectedDateComparator: Comparator<CalendarSelectedTodoUiModel> =
    compareBy<CalendarSelectedTodoUiModel> { it.isDone }
        .thenByDescending { it.priority.sortRank() }
        .thenBy { it.dueTimeLabel ?: "" }
        .thenBy { it.title }

private fun stableNegativeId(rawId: String): Long {
    val hash = rawId.hashCode().absoluteValue.toLong().coerceAtLeast(1L)
    return -hash
}

private fun com.neo.yourtodo.core.model.TodoPriority.sortRank(): Int = when (this) {
    com.neo.yourtodo.core.model.TodoPriority.HIGH -> 3
    com.neo.yourtodo.core.model.TodoPriority.MEDIUM -> 2
    com.neo.yourtodo.core.model.TodoPriority.LOW -> 1
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
