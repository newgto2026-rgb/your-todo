package com.neo.yourtodo.core.data.repository.todo

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.data.sync.TodoSyncPayload
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSyncStatus
import com.neo.yourtodo.core.network.sync.NetworkTodo
import java.time.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class TodoSyncFieldPolicyTest {

    private val contractJson = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    @Test
    fun `sync payload serializes only server contract fields`() {
        val payload = TodoSyncFieldPolicy.createPayload(localTodoWithLocalOnlyFields())
        val payloadFields = contractJson
            .parseToJsonElement(contractJson.encodeToString(payload))
            .jsonObject
            .keys

        assertThat(TodoSyncFieldPolicy.syncedPayloadFields).containsExactly(
            "title",
            "description",
            "dueDate",
            "status",
            "priority"
        ).inOrder()
        assertThat(payloadFields).containsExactlyElementsIn(TodoSyncFieldPolicy.syncedPayloadFields)
        assertThat(payloadFields).containsNoneIn(TodoSyncFieldPolicy.localOnlyTodoFields)
        assertThat(payload).isEqualTo(
            TodoSyncPayload(
                title = "server visible",
                description = null,
                dueDate = "2026-05-10",
                status = TodoSyncConstants.STATUS_COMPLETED,
                priority = TodoPriority.HIGH.name
            )
        )
    }

    @Test
    fun `policy names local only todo fields excluded from sync contract`() {
        assertThat(TodoSyncFieldPolicy.localOnlyTodoFields).containsExactly(
            "categoryId",
            "dueTimeMinutes",
            "reminderAtEpochMillis",
            "isReminderEnabled",
            "reminderRepeatType",
            "reminderRepeatDaysMask",
            "reminderLeadMinutes"
        ).inOrder()
        assertThat(TodoSyncFieldPolicy.localOnlyTodoFields)
            .containsNoneIn(TodoSyncFieldPolicy.syncedPayloadFields)
    }

    @Test
    fun `remote todo mapper does not populate android local only fields`() {
        val entity = NetworkTodo(
            id = "server-id",
            clientId = "client-id",
            title = "remote",
            dueDate = "2026-05-10",
            status = TodoSyncConstants.STATUS_ACTIVE,
            priority = TodoPriority.LOW.name,
            revision = "3",
            createdAt = "2026-05-08T00:00:00.000Z",
            updatedAt = "2026-05-08T00:00:01.000Z"
        ).toTodoEntity(ownerUserId = "user-id")

        assertThat(entity.categoryId).isNull()
        assertThat(entity.dueTimeMinutes).isNull()
        assertThat(entity.reminderAtEpochMillis).isNull()
        assertThat(entity.isReminderEnabled).isFalse()
        assertThat(entity.reminderRepeatType).isEqualTo(ReminderRepeatType.NONE.name)
        assertThat(entity.reminderRepeatDaysMask).isEqualTo(0)
        assertThat(entity.reminderLeadMinutes).isNull()
    }

    private fun localTodoWithLocalOnlyFields(): TodoEntity =
        TodoEntity(
            id = 1L,
            title = "server visible",
            isDone = true,
            dueDateEpochDay = LocalDate.of(2026, 5, 10).toEpochDay(),
            createdAt = 1L,
            updatedAt = 2L,
            categoryId = 9L,
            reminderAtEpochMillis = 1_778_400_000_000L,
            isReminderEnabled = true,
            reminderRepeatType = ReminderRepeatType.WEEKLY.name,
            reminderRepeatDaysMask = 0b0101010,
            dueTimeMinutes = 14 * 60 + 30,
            reminderLeadMinutes = 30,
            priority = TodoPriority.HIGH.name,
            serverId = "server-id",
            clientId = "client-id",
            ownerUserId = "user-id",
            syncStatus = TodoSyncStatus.PENDING_UPDATE.name,
            serverRevision = "2"
        )
}
