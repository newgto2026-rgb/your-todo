package com.neo.yourtodo.core.domain.reminder

import com.neo.yourtodo.core.model.ReminderRepeatType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderRecurrenceCalculator {

    fun nextTriggerAt(
        currentTriggerAt: Long,
        repeatType: ReminderRepeatType,
        repeatDaysMask: Int,
        nowEpochMillis: Long,
        zoneId: ZoneId
    ): Long? {
        val anchor = maxOf(currentTriggerAt, nowEpochMillis)
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(anchor), zoneId)

        return when (repeatType) {
            ReminderRepeatType.NONE -> null
            ReminderRepeatType.DAILY -> base.plusDays(1).toEpochMillis(zoneId)
            ReminderRepeatType.WEEKLY -> base.plusWeeks(1).toEpochMillis(zoneId)
            ReminderRepeatType.CUSTOM_DAYS -> nextCustomDaysTriggerAt(base, repeatDaysMask, zoneId)
        }
    }

    private fun nextCustomDaysTriggerAt(
        base: LocalDateTime,
        repeatDaysMask: Int,
        zoneId: ZoneId
    ): Long? {
        if (repeatDaysMask == 0) return null

        val start = base.plusDays(1)
        val candidate = (0..13)
            .asSequence()
            .map { offset -> start.plusDays(offset.toLong()) }
            .firstOrNull { dateTime ->
                val bit = 1 shl (dateTime.dayOfWeek.value - 1)
                (repeatDaysMask and bit) != 0
            } ?: return null

        return candidate.toEpochMillis(zoneId)
    }

    private fun LocalDateTime.toEpochMillis(zoneId: ZoneId): Long =
        atZone(zoneId).toInstant().toEpochMilli()
}
