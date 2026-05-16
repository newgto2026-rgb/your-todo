package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.database.dao.ReminderDao
import com.neo.yourtodo.core.database.entity.ReminderEntity
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.ReminderStatus
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReminderRepositoryImplTest {

    @Test
    fun `observe and get active reminders map correctly`() = runTest {
        val dao = FakeReminderDao().apply {
            seed(
                ReminderEntity(
                    id = 1L,
                    title = "A",
                    note = null,
                    triggerAtEpochMillis = 10L,
                    repeatType = ReminderRepeatType.DAILY.name,
                    repeatDaysMask = 0,
                    isEnabled = true,
                    status = ReminderStatus.SCHEDULED.name,
                    lastTriggeredAtEpochMillis = null,
                    createdAt = 1L,
                    updatedAt = 1L
                ),
                ReminderEntity(
                    id = 2L,
                    title = "B",
                    note = null,
                    triggerAtEpochMillis = 20L,
                    repeatType = ReminderRepeatType.NONE.name,
                    repeatDaysMask = 0,
                    isEnabled = false,
                    status = ReminderStatus.COMPLETED.name,
                    lastTriggeredAtEpochMillis = 3L,
                    createdAt = 2L,
                    updatedAt = 2L
                )
            )
        }
        val repository = ReminderRepositoryImpl(dao)

        val observed = repository.observeReminders().first()
        val active = repository.getActiveReminders()
        val single = repository.getReminder(1L)

        assertThat(observed).hasSize(2)
        assertThat(active.map { it.id }).containsExactly(1L)
        assertThat(single?.title).isEqualTo("A")
        assertThat(single?.repeatType).isEqualTo(ReminderRepeatType.DAILY)
    }

    @Test
    fun `add update delete and enable operations succeed`() = runTest {
        val dao = FakeReminderDao()
        val repository = ReminderRepositoryImpl(dao)

        val addResult = repository.addReminder(
            title = "Read",
            note = "book",
            triggerAtEpochMillis = 100L,
            repeatType = ReminderRepeatType.NONE,
            repeatDaysMask = 0,
            isEnabled = true
        )
        val id = addResult.getOrNull()!!
        assertThat(dao.getReminderById(id)).isNotNull()

        val updateResult = repository.updateReminder(
            id = id,
            title = "Read updated",
            note = null,
            triggerAtEpochMillis = 200L,
            repeatType = ReminderRepeatType.WEEKLY,
            repeatDaysMask = 0,
            isEnabled = false
        )
        assertThat(updateResult.isSuccess).isTrue()
        val updated = dao.getReminderById(id)!!
        assertThat(updated.title).isEqualTo("Read updated")
        assertThat(updated.isEnabled).isFalse()
        assertThat(updated.repeatType).isEqualTo(ReminderRepeatType.WEEKLY.name)

        val enableResult = repository.setReminderEnabled(id, true)
        assertThat(enableResult.isSuccess).isTrue()
        assertThat(dao.getReminderById(id)?.isEnabled).isTrue()

        val deleteResult = repository.deleteReminder(id)
        assertThat(deleteResult.isSuccess).isTrue()
        assertThat(dao.getReminderById(id)).isNull()
    }

    @Test
    fun `complete reminder handles none and repeating modes`() = runTest {
        val base = ReminderEntity(
            id = 10L,
            title = "Task",
            note = null,
            triggerAtEpochMillis = System.currentTimeMillis() - 60_000L,
            repeatType = ReminderRepeatType.NONE.name,
            repeatDaysMask = 0,
            isEnabled = true,
            status = ReminderStatus.SCHEDULED.name,
            lastTriggeredAtEpochMillis = null,
            createdAt = 1L,
            updatedAt = 1L
        )
        val dao = FakeReminderDao().apply { seed(base) }
        val repository = ReminderRepositoryImpl(dao)

        val noneResult = repository.completeReminder(10L)
        assertThat(noneResult.isSuccess).isTrue()
        val completed = dao.getReminderById(10L)!!
        assertThat(completed.status).isEqualTo(ReminderStatus.COMPLETED.name)
        assertThat(completed.isEnabled).isFalse()
        assertThat(completed.lastTriggeredAtEpochMillis).isNotNull()

        dao.seed(
            base.copy(
                id = 11L,
                repeatType = ReminderRepeatType.DAILY.name,
                isEnabled = true,
                status = ReminderStatus.SCHEDULED.name
            )
        )
        val repeatResult = repository.completeReminder(11L)
        assertThat(repeatResult.isSuccess).isTrue()
        val repeated = dao.getReminderById(11L)!!
        assertThat(repeated.status).isEqualTo(ReminderStatus.SCHEDULED.name)
        assertThat(repeated.isEnabled).isTrue()
        assertThat(repeated.triggerAtEpochMillis).isGreaterThan(base.triggerAtEpochMillis)
    }

    @Test
    fun `custom days with empty mask completes and snooze schedules`() = runTest {
        val now = System.currentTimeMillis()
        val dao = FakeReminderDao().apply {
            seed(
                ReminderEntity(
                    id = 21L,
                    title = "Custom",
                    note = null,
                    triggerAtEpochMillis = now - 1_000L,
                    repeatType = ReminderRepeatType.CUSTOM_DAYS.name,
                    repeatDaysMask = 0,
                    isEnabled = true,
                    status = ReminderStatus.SCHEDULED.name,
                    lastTriggeredAtEpochMillis = null,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        }
        val repository = ReminderRepositoryImpl(dao)

        val completeResult = repository.completeReminder(21L)
        assertThat(completeResult.isSuccess).isTrue()
        assertThat(dao.getReminderById(21L)?.status).isEqualTo(ReminderStatus.COMPLETED.name)

        val snoozeResult = repository.snoozeReminder(21L, 15)
        assertThat(snoozeResult.isSuccess).isTrue()
        val snoozed = dao.getReminderById(21L)!!
        assertThat(snoozed.status).isEqualTo(ReminderStatus.SCHEDULED.name)
        assertThat(snoozed.isEnabled).isTrue()
        assertThat(snoozed.triggerAtEpochMillis).isGreaterThan(now)
    }

    @Test
    fun `complete reminder schedules custom days with shared recurrence policy`() = runTest {
        val zoneId = ZoneId.systemDefault()
        val triggerAt = LocalDateTime.of(2100, 1, 4, 9, 0)
        val expectedNextTriggerAt = triggerAt.plusDays(2)
        val repeatDaysMask = 1 shl (expectedNextTriggerAt.dayOfWeek.value - 1)
        val dao = FakeReminderDao().apply {
            seed(
                ReminderEntity(
                    id = 31L,
                    title = "Custom",
                    note = null,
                    triggerAtEpochMillis = triggerAt.atZone(zoneId).toInstant().toEpochMilli(),
                    repeatType = ReminderRepeatType.CUSTOM_DAYS.name,
                    repeatDaysMask = repeatDaysMask,
                    isEnabled = true,
                    status = ReminderStatus.SCHEDULED.name,
                    lastTriggeredAtEpochMillis = null,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        }
        val repository = ReminderRepositoryImpl(dao)

        val completeResult = repository.completeReminder(31L)

        assertThat(completeResult.isSuccess).isTrue()
        val repeated = dao.getReminderById(31L)!!
        assertThat(repeated.status).isEqualTo(ReminderStatus.SCHEDULED.name)
        assertThat(repeated.isEnabled).isTrue()
        assertThat(repeated.triggerAtEpochMillis)
            .isEqualTo(expectedNextTriggerAt.atZone(zoneId).toInstant().toEpochMilli())
    }

    private class FakeReminderDao : ReminderDao {
        private val remindersFlow = MutableStateFlow<List<ReminderEntity>>(emptyList())
        private var idSeed = 1L

        fun seed(vararg reminders: ReminderEntity) {
            remindersFlow.value = reminders.toList().sortedBy { it.triggerAtEpochMillis }
            idSeed = (reminders.maxOfOrNull { it.id } ?: 0L) + 1L
        }

        override fun observeReminders(): Flow<List<ReminderEntity>> = remindersFlow.asStateFlow()

        override suspend fun getActiveReminders(): List<ReminderEntity> =
            remindersFlow.value.filter { it.isEnabled }.sortedBy { it.triggerAtEpochMillis }

        override suspend fun insert(reminder: ReminderEntity): Long {
            val id = if (reminder.id == 0L) idSeed++ else reminder.id
            remindersFlow.value = (remindersFlow.value + reminder.copy(id = id))
                .sortedBy { it.triggerAtEpochMillis }
            return id
        }

        override suspend fun update(reminder: ReminderEntity) {
            remindersFlow.value = remindersFlow.value
                .map { if (it.id == reminder.id) reminder else it }
                .sortedBy { it.triggerAtEpochMillis }
        }

        override suspend fun delete(reminder: ReminderEntity) {
            remindersFlow.value = remindersFlow.value.filterNot { it.id == reminder.id }
        }

        override suspend fun getReminderById(id: Long): ReminderEntity? =
            remindersFlow.value.firstOrNull { it.id == id }
    }
}
