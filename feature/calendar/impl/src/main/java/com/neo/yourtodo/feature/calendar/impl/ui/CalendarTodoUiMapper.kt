package com.neo.yourtodo.feature.calendar.impl.ui

import com.neo.yourtodo.core.domain.usecase.TaskSurfaceItem
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
