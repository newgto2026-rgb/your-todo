package com.neo.yourtodo.core.data.mapper

import com.neo.yourtodo.core.database.entity.ReminderEntity
import com.neo.yourtodo.core.model.Reminder
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.ReminderStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReminderMapperTest {

    @Test
    fun `toDomain maps entity to reminder`() {
        val entity = ReminderEntity(
            id = 9L,
            title = "Medicine",
            note = "after lunch",
            triggerAtEpochMillis = 1_710_000_000_000L,
            repeatType = ReminderRepeatType.WEEKLY.name,
            repeatDaysMask = 0b0010000,
            isEnabled = true,
            status = ReminderStatus.SCHEDULED.name,
            lastTriggeredAtEpochMillis = 1_709_000_000_000L,
            createdAt = 10L,
            updatedAt = 20L
        )

        val reminder = entity.toDomain()

        assertThat(reminder.id).isEqualTo(9L)
        assertThat(reminder.title).isEqualTo("Medicine")
        assertThat(reminder.note).isEqualTo("after lunch")
        assertThat(reminder.triggerAtEpochMillis).isEqualTo(1_710_000_000_000L)
        assertThat(reminder.repeatType).isEqualTo(ReminderRepeatType.WEEKLY)
        assertThat(reminder.repeatDaysMask).isEqualTo(0b0010000)
        assertThat(reminder.isEnabled).isTrue()
        assertThat(reminder.status).isEqualTo(ReminderStatus.SCHEDULED)
        assertThat(reminder.lastTriggeredAtEpochMillis).isEqualTo(1_709_000_000_000L)
        assertThat(reminder.createdAtEpochMillis).isEqualTo(10L)
        assertThat(reminder.updatedAtEpochMillis).isEqualTo(20L)
    }

    @Test
    fun `toEntity maps reminder to entity`() {
        val reminder = Reminder(
            id = 3L,
            title = "Workout",
            note = null,
            triggerAtEpochMillis = 1_700_000_000_000L,
            repeatType = ReminderRepeatType.CUSTOM_DAYS,
            repeatDaysMask = 0b0101010,
            isEnabled = false,
            status = ReminderStatus.COMPLETED,
            lastTriggeredAtEpochMillis = null,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 200L
        )

        val entity = reminder.toEntity()

        assertThat(entity.id).isEqualTo(3L)
        assertThat(entity.title).isEqualTo("Workout")
        assertThat(entity.note).isNull()
        assertThat(entity.triggerAtEpochMillis).isEqualTo(1_700_000_000_000L)
        assertThat(entity.repeatType).isEqualTo(ReminderRepeatType.CUSTOM_DAYS.name)
        assertThat(entity.repeatDaysMask).isEqualTo(0b0101010)
        assertThat(entity.isEnabled).isFalse()
        assertThat(entity.status).isEqualTo(ReminderStatus.COMPLETED.name)
        assertThat(entity.lastTriggeredAtEpochMillis).isNull()
        assertThat(entity.createdAt).isEqualTo(100L)
        assertThat(entity.updatedAt).isEqualTo(200L)
    }
}
