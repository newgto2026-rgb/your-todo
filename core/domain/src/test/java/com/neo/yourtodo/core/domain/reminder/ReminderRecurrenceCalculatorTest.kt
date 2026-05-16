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
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}
