package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

fun TodoItem.toTaskSurfaceItem(): TaskSurfaceItem =
    TaskSurfaceItem(
        id = id,
        title = title,
        isDone = isDone,
        dueDate = dueDate,
        dueTimeMinutes = dueTimeMinutes,
        reminderAtEpochMillis = reminderAtEpochMillis,
        isReminderEnabled = isReminderEnabled,
        reminderLeadMinutes = reminderLeadMinutes,
        reminderRepeatType = reminderRepeatType,
        priority = priority,
        source = TaskSurfaceSource.Local(todoId = id)
    )

fun AssignedTodo.toTaskSurfaceItem(
    zoneId: ZoneId,
    isDoneOverride: Boolean? = null
): TaskSurfaceItem {
    val reminderEpochMillis = reminderAtEpochMillis()
    return TaskSurfaceItem(
        id = assignedTaskSurfaceRowId(id),
        title = title,
        isDone = isDoneOverride ?: isDone,
        dueDate = dueDate,
        dueTimeMinutes = dueTimeMinutes,
        reminderAtEpochMillis = reminderEpochMillis,
        isReminderEnabled = reminder?.enabled == true,
        reminderLeadMinutes = reminderLeadMinutes(
            reminderEpochMillis = reminderEpochMillis,
            zoneId = zoneId
        ),
        reminderRepeatType = ReminderRepeatType.NONE,
        priority = priority,
        source = TaskSurfaceSource.Assigned(assignedTodoId = id),
        senderNickname = sender?.nickname,
        assignmentMode = assignmentMode
    )
}

fun AssignedTodo.toTaskSurfaceSummary(): TodoSummary =
    TodoSummary(
        id = assignedTaskSurfaceRowId(id),
        title = title,
        isDone = isDone,
        dueTimeMinutes = dueTimeMinutes,
        priority = priority,
        createdAt = createdAt?.toEpochMilli() ?: 0L
    )

fun assignedTaskSurfaceRowId(id: String): Long {
    val positiveHash = abs(id.hashCode().toLong())
    return -positiveHash - 1
}

internal fun TodoPriority.taskSurfaceSortRank(): Int = when (this) {
    TodoPriority.HIGH -> 3
    TodoPriority.MEDIUM -> 2
    TodoPriority.LOW -> 1
}

private fun AssignedTodo.reminderAtEpochMillis(): Long? =
    reminder
        ?.takeIf { it.enabled }
        ?.reminderAt
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

private fun AssignedTodo.reminderLeadMinutes(
    reminderEpochMillis: Long?,
    zoneId: ZoneId
): Int? {
    val dueDate = dueDate ?: return null
    val dueTimeMinutes = dueTimeMinutes ?: return null
    val reminderMillis = reminderEpochMillis ?: return null
    val dueMillis = dueDate
        .atTime(LocalTime.of(dueTimeMinutes / MINUTES_PER_HOUR, dueTimeMinutes % MINUTES_PER_HOUR))
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
    val leadMinutes = ((dueMillis - reminderMillis) / MILLIS_PER_MINUTE).toInt()
    return leadMinutes.takeIf { it in SupportedReminderLeadMinutes }
}

private val SupportedReminderLeadMinutes = setOf(0, 5, 10, 30, 60)
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_MINUTE = 60_000L
