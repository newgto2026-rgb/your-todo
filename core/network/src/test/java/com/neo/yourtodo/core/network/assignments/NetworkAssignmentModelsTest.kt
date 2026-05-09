package com.neo.yourtodo.core.network.assignments

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.network.di.NetworkProvidesModule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class NetworkAssignmentModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `create bundle request serializes due time minutes`() {
        val requestJson = NetworkProvidesModule.provideJson().encodeToString(
            NetworkCreateAssignmentBundleRequest(
                receiverUserId = "receiver-1",
                items = listOf(
                    NetworkCreateAssignmentItem(
                        clientItemId = "client-item-1",
                        title = "Buy milk",
                        dueDate = "2026-05-10",
                        dueTimeMinutes = 14 * 60 + 30,
                        priority = "MEDIUM"
                    )
                )
            )
        )

        assertThat(requestJson).contains("\"dueDate\":\"2026-05-10\"")
        assertThat(requestJson).contains("\"dueTimeMinutes\":870")
    }

    @Test
    fun `assigned todo response parses due time minutes`() {
        val response = json.decodeFromString<NetworkAssignedTodosResponse>(
            """
            {
              "items": [
                {
                  "id": "assigned-1",
                  "title": "Buy milk",
                  "dueDate": "2026-05-10",
                  "dueTimeMinutes": 870,
                  "priority": "MEDIUM",
                  "status": "ACCEPTED",
                  "progressPercent": 0,
                  "completedAt": "2026-05-09T00:00:00Z"
                }
              ]
            }
            """.trimIndent()
        )

        assertThat(response.items.single().dueTimeMinutes).isEqualTo(14 * 60 + 30)
        assertThat(response.items.single().completedAt).isEqualTo("2026-05-09T00:00:00Z")
    }
}
