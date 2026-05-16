package com.neo.yourtodo.core.network.sync

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.network.di.NetworkProvidesModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class NetworkTodoSyncModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `pull response parses cursor revision strings and tombstone todo`() {
        val response = json.decodeFromString<NetworkTodoSyncPullResponse>(
            """
            {
              "todos": [
                {
                  "id": "todo-1",
                  "clientId": "client-1",
                  "title": "deleted todo",
                  "description": null,
                  "dueDate": "2026-05-08",
                  "status": "DELETED",
                  "priority": "HIGH",
                  "revision": "12",
                  "createdAt": "2026-05-08T00:00:00.000Z",
                  "updatedAt": "2026-05-08T00:00:01.000Z",
                  "deletedAt": "2026-05-08T00:00:02.000Z"
                }
              ],
              "nextCursor": "12",
              "hasMore": false
            }
            """.trimIndent()
        )

        assertThat(response.nextCursor).isEqualTo("12")
        assertThat(response.todos.single().revision).isEqualTo("12")
        assertThat(response.todos.single().status).isEqualTo("DELETED")
        assertThat(response.todos.single().priority).isEqualTo("HIGH")
        assertThat(response.todos.single().deletedAt).isEqualTo("2026-05-08T00:00:02.000Z")
    }

    @Test
    fun `push response parses success duplicate and rejected statuses`() {
        val response = json.decodeFromString<NetworkTodoSyncPushResponse>(
            """
            {
              "results": [
                {
                  "clientMutationId": "m1",
                  "status": "APPLIED",
                  "todo": {
                    "id": "todo-1",
                    "clientId": "client-1",
                    "title": "applied",
                    "status": "ACTIVE",
                    "revision": "13",
                    "createdAt": "2026-05-08T00:00:00.000Z",
                    "updatedAt": "2026-05-08T00:00:00.000Z"
                  }
                },
                { "clientMutationId": "m2", "status": "DUPLICATE_APPLIED" },
                { "clientMutationId": "m3", "status": "DUPLICATE_CLIENT_ID" },
                {
                  "clientMutationId": "m4",
                  "status": "REJECTED_DELETED",
                  "todo": {
                    "id": "todo-2",
                    "clientId": "client-2",
                    "title": "deleted",
                    "status": "DELETED",
                    "revision": "14",
                    "createdAt": "2026-05-08T00:00:00.000Z",
                    "updatedAt": "2026-05-08T00:00:01.000Z",
                    "deletedAt": "2026-05-08T00:00:02.000Z"
                  }
                },
                {
                  "clientMutationId": "m5",
                  "status": "REJECTED_IDEMPOTENCY_CONFLICT",
                  "error": { "code": "IDEMPOTENCY_CONFLICT", "message": "conflict" }
                }
              ],
              "nextCursor": "14"
            }
            """.trimIndent()
        )

        assertThat(response.nextCursor).isEqualTo("14")
        assertThat(response.results.map { it.status }).containsExactly(
            "APPLIED",
            "DUPLICATE_APPLIED",
            "DUPLICATE_CLIENT_ID",
            "REJECTED_DELETED",
            "REJECTED_IDEMPOTENCY_CONFLICT"
        ).inOrder()
        assertThat(response.results[3].todo?.deletedAt).isNotNull()
        assertThat(response.results[4].error?.code).isEqualTo("IDEMPOTENCY_CONFLICT")
    }

    @Test
    fun `auth required error shape keeps expected code`() {
        val response = json.decodeFromString<ErrorResponse>(
            """
            {
              "error": {
                "code": "AUTH_REQUIRED",
                "message": "Authentication is required."
              }
            }
            """.trimIndent()
        )

        assertThat(response.error.code).isEqualTo("AUTH_REQUIRED")
    }

    @Test
    fun `retrofit json keeps explicit nulls for todo sync clear fields`() {
        val requestJson = NetworkProvidesModule.provideJson().encodeToString(
            NetworkTodoSyncPushRequest(
                baseCursor = "20",
                mutations = listOf(
                    NetworkTodoMutation(
                        clientMutationId = "mutation-clear-due-date",
                        type = "UPDATE",
                        id = "todo-1",
                        clientId = "client-1",
                        payload = NetworkTodoMutationPayload(
                            title = "clear due date",
                            description = null,
                            dueDate = null,
                            status = "ACTIVE",
                            priority = "HIGH"
                        )
                    )
                )
            )
        )

        assertThat(requestJson).contains("\"description\":null")
        assertThat(requestJson).contains("\"dueDate\":null")
        assertThat(requestJson).contains("\"priority\":\"HIGH\"")
    }

    @Test
    fun `mutation payload contract excludes android local only todo fields`() {
        val requestJson = NetworkProvidesModule.provideJson().encodeToString(
            NetworkTodoSyncPushRequest(
                baseCursor = "20",
                mutations = listOf(
                    NetworkTodoMutation(
                        clientMutationId = "mutation-sync-fields",
                        type = "UPDATE",
                        id = "todo-1",
                        clientId = "client-1",
                        payload = NetworkTodoMutationPayload(
                            title = "contract fields",
                            description = null,
                            dueDate = "2026-05-10",
                            status = "COMPLETED",
                            priority = "LOW"
                        )
                    )
                )
            )
        )
        val payloadFields = NetworkProvidesModule.provideJson()
            .parseToJsonElement(requestJson)
            .jsonObject["mutations"]!!
            .jsonArray
            .single()
            .jsonObject["payload"]!!
            .jsonObject
            .keys

        assertThat(descriptorElementNames(NetworkTodoMutationPayload.serializer().descriptor))
            .containsExactlyElementsIn(TODO_SYNC_PAYLOAD_FIELDS)
            .inOrder()
        assertThat(payloadFields).containsExactlyElementsIn(TODO_SYNC_PAYLOAD_FIELDS)
        ANDROID_LOCAL_ONLY_TODO_FIELDS.forEach { field ->
            assertThat(requestJson).doesNotContain("\"$field\"")
        }
    }

    @Test
    fun `todo snapshot dto does not expose android local only todo fields`() {
        assertThat(descriptorElementNames(NetworkTodo.serializer().descriptor))
            .containsNoneIn(ANDROID_LOCAL_ONLY_TODO_FIELDS)
    }

    @Serializable
    private data class ErrorResponse(
        val error: NetworkTodoMutationError
    )

    private fun descriptorElementNames(descriptor: SerialDescriptor): List<String> =
        (0 until descriptor.elementsCount).map(descriptor::getElementName)

    private companion object {
        val TODO_SYNC_PAYLOAD_FIELDS = listOf(
            "title",
            "description",
            "dueDate",
            "status",
            "priority"
        )

        val ANDROID_LOCAL_ONLY_TODO_FIELDS = listOf(
            "categoryId",
            "dueTimeMinutes",
            "reminderAtEpochMillis",
            "isReminderEnabled",
            "reminderRepeatType",
            "reminderRepeatDaysMask",
            "reminderLeadMinutes"
        )
    }
}
