package com.neo.yourtodo.core.model

data class Reminder(
    val id: Long,
    val title: String,
    val note: String?,
    val triggerAtEpochMillis: Long,
    val repeatType: ReminderRepeatType,
    val repeatDaysMask: Int,
    val isEnabled: Boolean,
    val status: ReminderStatus,
    val lastTriggeredAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
