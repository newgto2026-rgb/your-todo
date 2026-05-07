package com.neo.yourtodo.core.data.repository

import android.util.Log
import com.neo.yourtodo.core.data.mapper.toDomain
import com.neo.yourtodo.core.database.dao.ReminderDao
import com.neo.yourtodo.core.database.entity.ReminderEntity
import com.neo.yourtodo.core.domain.repository.ReminderRepository
import com.neo.yourtodo.core.model.Reminder
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.ReminderStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao
) : ReminderRepository {

    override fun observeReminders(): Flow<List<Reminder>> =
        reminderDao.observeReminders().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getReminder(id: Long): Reminder? = reminderDao.getReminderById(id)?.toDomain()

    override suspend fun getActiveReminders(): List<Reminder> =
        reminderDao.getActiveReminders().map { it.toDomain() }

    override suspend fun addReminder(
        title: String,
        note: String?,
        triggerAtEpochMillis: Long,
        repeatType: ReminderRepeatType,
        repeatDaysMask: Int,
        isEnabled: Boolean
    ): Result<Long> = runCatching {
        val now = System.currentTimeMillis()
        reminderDao.insert(
            ReminderEntity(
                title = title,
                note = note,
                triggerAtEpochMillis = triggerAtEpochMillis,
                repeatType = repeatType.name,
                repeatDaysMask = repeatDaysMask,
                isEnabled = isEnabled,
                status = ReminderStatus.SCHEDULED.name,
                lastTriggeredAtEpochMillis = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }.onFailure { throwable ->
        logError("addReminder", throwable)
    }

    override suspend fun updateReminder(
        id: Long,
        title: String,
        note: String?,
        triggerAtEpochMillis: Long,
        repeatType: ReminderRepeatType,
        repeatDaysMask: Int,
        isEnabled: Boolean
    ): Result<Unit> = runCatching {
        val existing = reminderDao.getReminderById(id) ?: throw IllegalStateException("Reminder not found")
        reminderDao.update(
            existing.copy(
                title = title,
                note = note,
                triggerAtEpochMillis = triggerAtEpochMillis,
                repeatType = repeatType.name,
                repeatDaysMask = repeatDaysMask,
                isEnabled = isEnabled,
                status = ReminderStatus.SCHEDULED.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }.onFailure { throwable ->
        logError("updateReminder", throwable)
    }

    override suspend fun deleteReminder(id: Long): Result<Unit> = runCatching {
        val existing = reminderDao.getReminderById(id) ?: throw IllegalStateException("Reminder not found")
        reminderDao.delete(existing)
    }.onFailure { throwable ->
        logError("deleteReminder", throwable)
    }

    override suspend fun setReminderEnabled(id: Long, enabled: Boolean): Result<Unit> = runCatching {
        val existing = reminderDao.getReminderById(id) ?: throw IllegalStateException("Reminder not found")
        reminderDao.update(
            existing.copy(
                isEnabled = enabled,
                updatedAt = System.currentTimeMillis()
            )
        )
    }.onFailure { throwable ->
        logError("setReminderEnabled", throwable)
    }

    override suspend fun completeReminder(id: Long): Result<Unit> = runCatching {
        val existing = reminderDao.getReminderById(id) ?: throw IllegalStateException("Reminder not found")
        val now = System.currentTimeMillis()
        val repeatType = ReminderRepeatType.valueOf(existing.repeatType)
        val nextTrigger = computeNextTriggerAt(
            currentTriggerAt = existing.triggerAtEpochMillis,
            repeatType = repeatType,
            repeatDaysMask = existing.repeatDaysMask,
            nowEpochMillis = now
        )
        val shouldContinue = nextTrigger != null

        reminderDao.update(
            existing.copy(
                triggerAtEpochMillis = nextTrigger ?: existing.triggerAtEpochMillis,
                isEnabled = shouldContinue,
                status = if (shouldContinue) ReminderStatus.SCHEDULED.name else ReminderStatus.COMPLETED.name,
                lastTriggeredAtEpochMillis = now,
                updatedAt = now
            )
        )
    }.onFailure { throwable ->
        logError("completeReminder", throwable)
    }

    override suspend fun snoozeReminder(id: Long, minutes: Int): Result<Unit> = runCatching {
        val existing = reminderDao.getReminderById(id) ?: throw IllegalStateException("Reminder not found")
        val now = System.currentTimeMillis()
        val snoozedAt = now + minutes * 60_000L
        reminderDao.update(
            existing.copy(
                triggerAtEpochMillis = snoozedAt,
                isEnabled = true,
                status = ReminderStatus.SCHEDULED.name,
                updatedAt = now
            )
        )
    }.onFailure { throwable ->
        logError("snoozeReminder", throwable)
    }

    private fun computeNextTriggerAt(
        currentTriggerAt: Long,
        repeatType: ReminderRepeatType,
        repeatDaysMask: Int,
        nowEpochMillis: Long
    ): Long? {
        val zoneId = ZoneId.systemDefault()
        val anchor = maxOf(currentTriggerAt, nowEpochMillis)
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(anchor), zoneId)

        return when (repeatType) {
            ReminderRepeatType.NONE -> null
            ReminderRepeatType.DAILY -> base.plusDays(1).atZone(zoneId).toInstant().toEpochMilli()
            ReminderRepeatType.WEEKLY -> base.plusWeeks(1).atZone(zoneId).toInstant().toEpochMilli()
            ReminderRepeatType.CUSTOM_DAYS -> {
                if (repeatDaysMask == 0) return null
                val start = base.plusDays(1)
                val candidate = (0..13)
                    .asSequence()
                    .map { offset -> start.plusDays(offset.toLong()) }
                    .firstOrNull { dateTime ->
                        val bit = 1 shl (dateTime.dayOfWeek.value - 1)
                        (repeatDaysMask and bit) != 0
                    } ?: return null
                candidate.atZone(zoneId).toInstant().toEpochMilli()
            }
        }
    }

    private fun logError(action: String, throwable: Throwable) {
        Log.e(TAG, "action=$action failure=${throwable.message}", throwable)
    }

    private companion object {
        private const val TAG = "ReminderRepository"
    }
}
