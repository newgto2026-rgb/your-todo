package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.Reminder
import com.neo.yourtodo.core.model.ReminderRepeatType
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observeReminders(): Flow<List<Reminder>>
    suspend fun getReminder(id: Long): Reminder?
    suspend fun getActiveReminders(): List<Reminder>

    suspend fun addReminder(
        title: String,
        note: String?,
        triggerAtEpochMillis: Long,
        repeatType: ReminderRepeatType,
        repeatDaysMask: Int,
        isEnabled: Boolean
    ): Result<Long>

    suspend fun updateReminder(
        id: Long,
        title: String,
        note: String?,
        triggerAtEpochMillis: Long,
        repeatType: ReminderRepeatType,
        repeatDaysMask: Int,
        isEnabled: Boolean
    ): Result<Unit>

    suspend fun deleteReminder(id: Long): Result<Unit>
    suspend fun setReminderEnabled(id: Long, enabled: Boolean): Result<Unit>
    suspend fun completeReminder(id: Long): Result<Unit>
    suspend fun snoozeReminder(id: Long, minutes: Int): Result<Unit>
}
