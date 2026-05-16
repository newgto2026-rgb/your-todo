package com.neo.yourtodo.core.domain.reminder

import com.neo.yourtodo.core.model.ReminderRepeatType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object ReminderRecurrenceCalculator {

    fun nextTriggerAt(
        currentTriggerAt: Long,
        repeatType: ReminderRepeatType,
        repeatDaysMask: Int,
        nowEpochMillis: Long,
        zoneId: ZoneId
    ): Long? {
        val current = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTriggerAt), zoneId)
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMillis), zoneId)

        return when (repeatType) {
            ReminderRepeatType.NONE -> null
            ReminderRepeatType.DAILY -> nextDailyTriggerAt(current, nowEpochMillis, zoneId)
            ReminderRepeatType.WEEKLY -> nextWeeklyTriggerAt(current, nowEpochMillis, zoneId)
            ReminderRepeatType.CUSTOM_DAYS -> nextCustomDaysTriggerAt(
                current = current,
                now = now,
                repeatDaysMask = repeatDaysMask,
                nowEpochMillis = nowEpochMillis,
                zoneId = zoneId
            )
        }
    }

    private fun nextDailyTriggerAt(
        current: LocalDateTime,
        nowEpochMillis: Long,
        zoneId: ZoneId
    ): Long {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMillis), zoneId)
        val daysToNow = ChronoUnit.DAYS.between(current.toLocalDate(), now.toLocalDate())
        val firstStep = maxOf(daysToNow, 1L)
        return nextIntervalTriggerAt(
            firstCandidate = current.plusDays(firstStep),
            nowEpochMillis = nowEpochMillis,
            zoneId = zoneId
        ) { candidate -> candidate.plusDays(1) }
    }

    private fun nextWeeklyTriggerAt(
        current: LocalDateTime,
        nowEpochMillis: Long,
        zoneId: ZoneId
    ): Long {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMillis), zoneId)
        val daysToNow = ChronoUnit.DAYS.between(current.toLocalDate(), now.toLocalDate())
        val firstStep = maxOf(daysToNow / 7, 1L)
        return nextIntervalTriggerAt(
            firstCandidate = current.plusWeeks(firstStep),
            nowEpochMillis = nowEpochMillis,
            zoneId = zoneId
        ) { candidate -> candidate.plusWeeks(1) }
    }

    private fun nextIntervalTriggerAt(
        firstCandidate: LocalDateTime,
        nowEpochMillis: Long,
        zoneId: ZoneId,
        advance: (LocalDateTime) -> LocalDateTime
    ): Long {
        var candidate = firstCandidate
        var candidateEpochMillis = candidate.toEpochMillis(zoneId)
        while (candidateEpochMillis <= nowEpochMillis) {
            candidate = advance(candidate)
            candidateEpochMillis = candidate.toEpochMillis(zoneId)
        }
        return candidateEpochMillis
    }

    private fun nextCustomDaysTriggerAt(
        current: LocalDateTime,
        now: LocalDateTime,
        repeatDaysMask: Int,
        nowEpochMillis: Long,
        zoneId: ZoneId
    ): Long? {
        if (repeatDaysMask == 0) return null

        val start = LocalDateTime.of(
            maxOf(current.toLocalDate().plusDays(1), now.toLocalDate()),
            current.toLocalTime()
        ).let { candidate ->
            if (candidate.toEpochMillis(zoneId) <= nowEpochMillis) {
                candidate.plusDays(1)
            } else {
                candidate
            }
        }

        val candidate = (0..6)
            .asSequence()
            .map { offset -> start.plusDays(offset.toLong()) }
            .firstOrNull { dateTime ->
                val bit = 1 shl (dateTime.dayOfWeek.value - 1)
                (repeatDaysMask and bit) != 0
            } ?: return null

        return candidate.toEpochMillis(zoneId)
    }

    private fun maxOf(first: LocalDate, second: LocalDate): LocalDate =
        if (first >= second) first else second

    private fun LocalDateTime.toEpochMillis(zoneId: ZoneId): Long =
        atZone(zoneId).toInstant().toEpochMilli()
}
