package com.neo.yourtodo.core.data.mapper

import com.neo.yourtodo.core.database.entity.ReminderEntity
import com.neo.yourtodo.core.model.Reminder
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.ReminderStatus

fun ReminderEntity.toDomain(): Reminder =
    Reminder(
        id = id,
        title = title,
        note = note,
        triggerAtEpochMillis = triggerAtEpochMillis,
        repeatType = ReminderRepeatType.valueOf(repeatType),
        repeatDaysMask = repeatDaysMask,
        isEnabled = isEnabled,
        status = ReminderStatus.valueOf(status),
        lastTriggeredAtEpochMillis = lastTriggeredAtEpochMillis,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = updatedAt
    )

fun Reminder.toEntity(): ReminderEntity =
    ReminderEntity(
        id = id,
        title = title,
        note = note,
        triggerAtEpochMillis = triggerAtEpochMillis,
        repeatType = repeatType.name,
        repeatDaysMask = repeatDaysMask,
        isEnabled = isEnabled,
        status = status.name,
        lastTriggeredAtEpochMillis = lastTriggeredAtEpochMillis,
        createdAt = createdAtEpochMillis,
        updatedAt = updatedAtEpochMillis
    )
