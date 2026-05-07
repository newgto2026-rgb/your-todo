package com.neo.yourtodo.core.database.dao

import androidx.room.Room
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.database.entity.ReminderEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ReminderDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ReminderDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.reminderDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeAndGetActiveReminders_orderByTriggerTime() = runTest {
        dao.insert(
            ReminderEntity(
                title = "disabled",
                note = null,
                triggerAtEpochMillis = 30L,
                repeatType = "NONE",
                repeatDaysMask = 0,
                isEnabled = false,
                status = "COMPLETED",
                lastTriggeredAtEpochMillis = null,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        dao.insert(
            ReminderEntity(
                title = "enabled-late",
                note = null,
                triggerAtEpochMillis = 20L,
                repeatType = "DAILY",
                repeatDaysMask = 0,
                isEnabled = true,
                status = "SCHEDULED",
                lastTriggeredAtEpochMillis = null,
                createdAt = 2L,
                updatedAt = 2L
            )
        )
        dao.insert(
            ReminderEntity(
                title = "enabled-early",
                note = null,
                triggerAtEpochMillis = 10L,
                repeatType = "WEEKLY",
                repeatDaysMask = 0,
                isEnabled = true,
                status = "SCHEDULED",
                lastTriggeredAtEpochMillis = null,
                createdAt = 3L,
                updatedAt = 3L
            )
        )

        val observed = dao.observeReminders().first()
        assertThat(observed.map { it.title }).containsExactly("enabled-early", "enabled-late", "disabled").inOrder()

        val active = dao.getActiveReminders()
        assertThat(active.map { it.title }).containsExactly("enabled-early", "enabled-late").inOrder()
    }

    @Test
    fun updateAndDelete_workAsExpected() = runTest {
        val id = dao.insert(
            ReminderEntity(
                title = "before",
                note = "note",
                triggerAtEpochMillis = 100L,
                repeatType = "NONE",
                repeatDaysMask = 0,
                isEnabled = true,
                status = "SCHEDULED",
                lastTriggeredAtEpochMillis = null,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        val existing = dao.getReminderById(id)!!

        dao.update(
            existing.copy(
                title = "after",
                isEnabled = false,
                status = "COMPLETED",
                updatedAt = 2L
            )
        )
        val updated = dao.getReminderById(id)
        assertThat(updated?.title).isEqualTo("after")
        assertThat(updated?.isEnabled).isFalse()
        assertThat(updated?.status).isEqualTo("COMPLETED")

        dao.delete(updated!!)
        assertThat(dao.getReminderById(id)).isNull()
    }
}
