package com.neo.yourtodo.core.domain.reminder

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.model.ReminderRepeatType
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class ReminderRecurrenceCalculatorTest {

    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul")

    @Test
    fun `none repeat has no next trigger`() {
        val current = epochMillis(2026, 5, 18, 9, 0)

        val nextTrigger = ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = ReminderRepeatType.NONE,
            repeatDaysMask = 0,
            nowEpochMillis = current,
            zoneId = zoneId
        )

        assertThat(nextTrigger).isNull()
    }

    @Test
    fun `daily and weekly repeat preserve scheduled time while skipping past occurrences`() {
        val current = epochMillis(2026, 5, 18, 9, 0)
        val now = epochMillis(2026, 5, 20, 10, 30)

        val daily = ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = ReminderRepeatType.DAILY,
            repeatDaysMask = 0,
            nowEpochMillis = now,
            zoneId = zoneId
        )
        val weekly = ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = ReminderRepeatType.WEEKLY,
            repeatDaysMask = 0,
            nowEpochMillis = now,
            zoneId = zoneId
        )

        assertThat(daily).isEqualTo(epochMillis(2026, 5, 21, 9, 0))
        assertThat(weekly).isEqualTo(epochMillis(2026, 5, 25, 9, 0))
    }

    @Test
    fun `daily repeat preserves local wall time across spring DST gap`() {
        val newYork = ZoneId.of("America/New_York")
        val current = epochMillis(newYork, 2026, 3, 7, 2, 30)
        val now = epochMillis(newYork, 2026, 3, 7, 3, 0)

        val nextTrigger = ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = ReminderRepeatType.DAILY,
            repeatDaysMask = 0,
            nowEpochMillis = now,
            zoneId = newYork
        )

        assertThat(nextTrigger).isEqualTo(epochMillis(newYork, 2026, 3, 8, 2, 30))
    }

    @Test
    fun `weekly repeat preserves local wall time across fall DST overlap`() {
        val newYork = ZoneId.of("America/New_York")
        val current = epochMillis(newYork, 2026, 10, 25, 1, 30)
        val now = epochMillis(newYork, 2026, 10, 25, 2, 0)

        val nextTrigger = ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = ReminderRepeatType.WEEKLY,
            repeatDaysMask = 0,
            nowEpochMillis = now,
            zoneId = newYork
        )

        assertThat(nextTrigger).isEqualTo(epochMillis(newYork, 2026, 11, 1, 1, 30))
    }

    @Test
    fun `custom days repeat picks first enabled weekday after anchor`() {
        val current = epochMillis(2026, 5, 18, 9, 15)
        val monday = 1 shl 0
        val thursday = 1 shl 3

        val nextTrigger = ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = ReminderRepeatType.CUSTOM_DAYS,
            repeatDaysMask = monday or thursday,
            nowEpochMillis = current,
            zoneId = zoneId
        )

        assertThat(nextTrigger).isEqualTo(epochMillis(2026, 5, 21, 9, 15))
    }

    @Test
    fun `custom days repeat preserves scheduled time when matching day is already past`() {
        val current = epochMillis(2026, 5, 18, 9, 15)
        val now = epochMillis(2026, 5, 21, 10, 30)
        val monday = 1 shl 0
        val thursday = 1 shl 3

        val nextTrigger = ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = ReminderRepeatType.CUSTOM_DAYS,
            repeatDaysMask = monday or thursday,
            nowEpochMillis = now,
            zoneId = zoneId
        )

        assertThat(nextTrigger).isEqualTo(epochMillis(2026, 5, 25, 9, 15))
    }

    @Test
    fun `custom days repeat can wrap to sunday and ignores unsupported mask bits`() {
        val current = epochMillis(2026, 5, 22, 9, 15)
        val sunday = 1 shl 6
        val unsupportedEighthDay = 1 shl 7

        val nextTrigger = ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = ReminderRepeatType.CUSTOM_DAYS,
            repeatDaysMask = sunday or unsupportedEighthDay,
            nowEpochMillis = current,
            zoneId = zoneId
        )

        assertThat(nextTrigger).isEqualTo(epochMillis(2026, 5, 24, 9, 15))
    }

    @Test
    fun `custom days repeat uses supplied zone when date differs from utc`() {
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val current = epochMillis(losAngeles, 2026, 5, 18, 23, 30)
        val now = epochMillis(losAngeles, 2026, 5, 19, 0, 30)
        val tuesday = 1 shl 1

        val nextTrigger = ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = ReminderRepeatType.CUSTOM_DAYS,
            repeatDaysMask = tuesday,
            nowEpochMillis = now,
            zoneId = losAngeles
        )

        assertThat(nextTrigger).isEqualTo(epochMillis(losAngeles, 2026, 5, 19, 23, 30))
    }

    @Test
    fun `custom days repeat with empty mask has no next trigger`() {
        val current = epochMillis(2026, 5, 18, 9, 0)

        val nextTrigger = ReminderRecurrenceCalculator.nextTriggerAt(
            currentTriggerAt = current,
            repeatType = ReminderRepeatType.CUSTOM_DAYS,
            repeatDaysMask = 0,
            nowEpochMillis = current,
            zoneId = zoneId
        )

        assertThat(nextTrigger).isNull()
    }

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = epochMillis(zoneId, year, month, day, hour, minute)

    private fun epochMillis(
        zoneId: ZoneId,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}
