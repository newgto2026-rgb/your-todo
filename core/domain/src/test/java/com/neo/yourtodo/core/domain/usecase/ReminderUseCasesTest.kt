package com.neo.yourtodo.core.domain.usecase

import app.cash.turbine.test
import com.neo.yourtodo.core.domain.repository.ReminderRepository
import com.neo.yourtodo.core.domain.repository.TodoReminderRepository
import com.neo.yourtodo.core.model.Reminder
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.ReminderStatus
import com.neo.yourtodo.core.model.TodoItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class ReminderUseCasesTest {

    @Test
    fun `observe get and active reminder use cases delegate to repository`() = runTest {
        val repository = FakeReminderRepository().apply {
            reminders.value = listOf(
                baseReminder(id = 1L, title = "A", isEnabled = true),
                baseReminder(id = 2L, title = "B", isEnabled = false)
            )
        }

        ObserveRemindersUseCase(repository)().test {
            assertThat(awaitItem()).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(GetReminderUseCase(repository)(1L)?.title).isEqualTo("A")
        assertThat(GetActiveRemindersUseCase(repository)().map { it.id }).containsExactly(1L)
    }

    @Test
    fun `add and update reminder use cases normalize inputs`() = runTest {
        val repository = FakeReminderRepository()
        val addUseCase = AddReminderUseCase(repository)
        val updateUseCase = UpdateReminderUseCase(repository)

        val addResult = addUseCase(
            title = "  Drink water  ",
            note = "  after run  ",
            triggerAtEpochMillis = 100L,
            repeatType = ReminderRepeatType.DAILY,
            repeatDaysMask = 0,
            isEnabled = true
        )
        val id = addResult.getOrNull()!!
        assertThat(repository.lastAddedTitle).isEqualTo("Drink water")
        assertThat(repository.lastAddedNote).isEqualTo("after run")

        val updateResult = updateUseCase(
            id = id,
            title = "  Updated  ",
            note = "   ",
            triggerAtEpochMillis = 200L,
            repeatType = ReminderRepeatType.WEEKLY,
            repeatDaysMask = 0,
            isEnabled = false
        )
        assertThat(updateResult.isSuccess).isTrue()
        assertThat(repository.lastUpdatedTitle).isEqualTo("Updated")
        assertThat(repository.lastUpdatedNote).isNull()
    }

    @Test
    fun `blank title and invalid snooze are rejected`() = runTest {
        val repository = FakeReminderRepository()

        val addInvalid = AddReminderUseCase(repository)(
            title = "   ",
            note = null,
            triggerAtEpochMillis = 1L,
            repeatType = ReminderRepeatType.NONE,
            repeatDaysMask = 0
        )
        val updateInvalid = UpdateReminderUseCase(repository)(
            id = 1L,
            title = "   ",
            note = null,
            triggerAtEpochMillis = 1L,
            repeatType = ReminderRepeatType.NONE,
            repeatDaysMask = 0,
            isEnabled = true
        )
        val snoozeInvalid = SnoozeReminderUseCase(repository)(id = 1L, minutes = 0)

        assertThat(addInvalid.isFailure).isTrue()
        assertThat(updateInvalid.isFailure).isTrue()
        assertThat(snoozeInvalid.isFailure).isTrue()
    }

    @Test
    fun `complete delete setEnabled and snooze delegate to repository`() = runTest {
        val repository = FakeReminderRepository().apply {
            reminders.value = listOf(baseReminder(id = 7L, title = "Task", isEnabled = true))
        }

        val complete = CompleteReminderUseCase(repository)(7L)
        val setEnabled = SetReminderEnabledUseCase(repository)(7L, false)
        val snooze = SnoozeReminderUseCase(repository)(7L, 10)
        val delete = DeleteReminderUseCase(repository)(7L)

        assertThat(complete.isSuccess).isTrue()
        assertThat(setEnabled.isSuccess).isTrue()
        assertThat(snooze.isSuccess).isTrue()
        assertThat(delete.isSuccess).isTrue()
        assertThat(repository.completeCalled).isEqualTo(7L)
        assertThat(repository.lastSetEnabled).isEqualTo(false)
        assertThat(repository.lastSnoozeMinutes).isEqualTo(10)
        assertThat(repository.getReminder(7L)).isNull()
    }

    @Test
    fun `get active todo reminders use case delegates`() = runTest {
        val todos = listOf(
            TodoItem(
                id = 1L,
                title = "todo",
                isDone = false,
                dueDate = LocalDate.of(2026, 4, 12),
                createdAt = 1L,
                updatedAt = 1L,
                categoryId = null,
                reminderAtEpochMillis = 100L,
                isReminderEnabled = true,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            )
        )
        val useCase = GetActiveTodoRemindersUseCase(
            object : TodoReminderRepository {
                override suspend fun getTodosWithActiveReminder(): List<TodoItem> = todos
            }
        )

        val result = useCase()

        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo(1L)
    }

    private class FakeReminderRepository : ReminderRepository {
        val reminders = MutableStateFlow<List<Reminder>>(emptyList())
        private var idSeed = 100L
        var lastAddedTitle: String? = null
        var lastAddedNote: String? = null
        var lastUpdatedTitle: String? = null
        var lastUpdatedNote: String? = null
        var completeCalled: Long? = null
        var lastSetEnabled: Boolean? = null
        var lastSnoozeMinutes: Int? = null

        override fun observeReminders(): Flow<List<Reminder>> = reminders.asStateFlow()

        override suspend fun getReminder(id: Long): Reminder? = reminders.value.firstOrNull { it.id == id }

        override suspend fun getActiveReminders(): List<Reminder> = reminders.value.filter { it.isEnabled }

        override suspend fun addReminder(
            title: String,
            note: String?,
            triggerAtEpochMillis: Long,
            repeatType: ReminderRepeatType,
            repeatDaysMask: Int,
            isEnabled: Boolean
        ): Result<Long> {
            lastAddedTitle = title
            lastAddedNote = note
            val id = idSeed++
            reminders.value = reminders.value + Reminder(
                id = id,
                title = title,
                note = note,
                triggerAtEpochMillis = triggerAtEpochMillis,
                repeatType = repeatType,
                repeatDaysMask = repeatDaysMask,
                isEnabled = isEnabled,
                status = if (isEnabled) ReminderStatus.SCHEDULED else ReminderStatus.COMPLETED,
                lastTriggeredAtEpochMillis = null,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L
            )
            return Result.success(id)
        }

        override suspend fun updateReminder(
            id: Long,
            title: String,
            note: String?,
            triggerAtEpochMillis: Long,
            repeatType: ReminderRepeatType,
            repeatDaysMask: Int,
            isEnabled: Boolean
        ): Result<Unit> {
            lastUpdatedTitle = title
            lastUpdatedNote = note
            reminders.value = reminders.value.map {
                if (it.id == id) {
                    it.copy(
                        title = title,
                        note = note,
                        triggerAtEpochMillis = triggerAtEpochMillis,
                        repeatType = repeatType,
                        isEnabled = isEnabled
                    )
                } else {
                    it
                }
            }
            return Result.success(Unit)
        }

        override suspend fun deleteReminder(id: Long): Result<Unit> {
            reminders.value = reminders.value.filterNot { it.id == id }
            return Result.success(Unit)
        }

        override suspend fun setReminderEnabled(id: Long, enabled: Boolean): Result<Unit> {
            lastSetEnabled = enabled
            reminders.value = reminders.value.map {
                if (it.id == id) it.copy(isEnabled = enabled) else it
            }
            return Result.success(Unit)
        }

        override suspend fun completeReminder(id: Long): Result<Unit> {
            completeCalled = id
            reminders.value = reminders.value.map {
                if (it.id == id) it.copy(status = ReminderStatus.COMPLETED, isEnabled = false) else it
            }
            return Result.success(Unit)
        }

        override suspend fun snoozeReminder(id: Long, minutes: Int): Result<Unit> {
            lastSnoozeMinutes = minutes
            reminders.value = reminders.value.map {
                if (it.id == id) it.copy(triggerAtEpochMillis = it.triggerAtEpochMillis + minutes * 60_000L) else it
            }
            return Result.success(Unit)
        }
    }

    private fun baseReminder(
        id: Long,
        title: String,
        isEnabled: Boolean,
        triggerAt: Long = 1_000L,
        repeatType: ReminderRepeatType = ReminderRepeatType.NONE
    ) = Reminder(
        id = id,
        title = title,
        note = null,
        triggerAtEpochMillis = triggerAt,
        repeatType = repeatType,
        repeatDaysMask = 0,
        isEnabled = isEnabled,
        status = if (isEnabled) ReminderStatus.SCHEDULED else ReminderStatus.COMPLETED,
        lastTriggeredAtEpochMillis = null,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )
}
