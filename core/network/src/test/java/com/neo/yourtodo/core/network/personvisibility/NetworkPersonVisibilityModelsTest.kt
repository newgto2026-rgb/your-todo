package com.neo.yourtodo.core.network.personvisibility

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.network.di.NetworkProvidesModule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class NetworkPersonVisibilityModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `create visibility grant request serializes viewer user id`() {
        val requestJson = NetworkProvidesModule.provideJson().encodeToString(
            NetworkCreateVisibilityGrantRequest(viewerUserId = "friend-id")
        )

        assertThat(requestJson).contains("\"viewerUserId\":\"friend-id\"")
    }

    @Test
    fun `visibility grants response parses given and received grants`() {
        val response = json.decodeFromString<NetworkVisibilityGrantsResponse>(
            """
            {
              "given": [
                {
                  "id": "grant-given",
                  "owner": {
                    "id": "me",
                    "nickname": "나"
                  },
                  "viewer": {
                    "id": "friend-id",
                    "nickname": "친구"
                  },
                  "status": "ACTIVE",
                  "createdAt": "2026-05-17T00:00:00.000Z",
                  "updatedAt": "2026-05-17T00:00:00.000Z"
                }
              ],
              "received": [
                {
                  "id": "grant-received",
                  "owner": {
                    "id": "friend-id",
                    "nickname": "친구"
                  },
                  "viewer": {
                    "id": "me",
                    "nickname": "나"
                  },
                  "status": "REVOKED",
                  "createdAt": "2026-05-16T00:00:00.000Z",
                  "updatedAt": "2026-05-17T00:01:00.000Z",
                  "revokedAt": "2026-05-17T00:01:00.000Z"
                }
              ]
            }
            """.trimIndent()
        )

        assertThat(response.given.single().id).isEqualTo("grant-given")
        assertThat(response.given.single().viewer.id).isEqualTo("friend-id")
        assertThat(response.received.single().revokedAt).isEqualTo("2026-05-17T00:01:00.000Z")
    }

    @Test
    fun `visibility grant mutation response parses wrapped grant`() {
        val response = json.decodeFromString<NetworkVisibilityGrantMutationResponse>(
            """
            {
              "grant": {
                "id": "grant-id",
                "owner": {
                  "id": "me",
                  "nickname": "나"
                },
                "viewer": {
                  "id": "friend-id",
                  "nickname": "친구"
                },
                "status": "ACTIVE",
                "createdAt": "2026-05-17T00:00:00.000Z",
                "updatedAt": "2026-05-17T00:00:00.000Z",
                "revokedAt": null
              }
            }
            """.trimIndent()
        )

        assertThat(response.grant.owner.id).isEqualTo("me")
        assertThat(response.grant.viewer.id).isEqualTo("friend-id")
    }

    @Test
    fun `observed todo sync response parses items deletions and purges`() {
        val response = json.decodeFromString<NetworkObservedTodoSyncResponse>(
            """
            {
              "items": [
                {
                  "id": "observed-id",
                  "clientId": "todo-id",
                  "source": "OBSERVED",
                  "grantId": "grant-id",
                  "owner": {
                    "id": "owner-id",
                    "nickname": "민지",
                    "avatarUrl": null
                  },
                  "title": "병원 예약",
                  "dueDate": "2026-05-20",
                  "dueTime": "14:30",
                  "status": "ACTIVE",
                  "revision": "11",
                  "createdAt": "2026-05-17T00:00:00.000Z",
                  "updatedAt": "2026-05-17T00:00:00.000Z"
                }
              ],
              "deleted": [
                {
                  "id": "observed-old",
                  "grantId": "grant-id",
                  "ownerUserId": "owner-id",
                  "revision": "12",
                  "updatedAt": "2026-05-17T00:01:00.000Z",
                  "deletedAt": "2026-05-17T00:01:00.000Z"
                }
              ],
              "purgedGrantIds": ["revoked-grant-id"],
              "nextCursor": "cursor-2"
            }
            """.trimIndent()
        )

        assertThat(response.items.single().owner.nickname).isEqualTo("민지")
        assertThat(response.items.single().dueTime).isEqualTo("14:30")
        assertThat(response.items.single().revision).isEqualTo("11")
        assertThat(response.deleted.single().observedTodoId).isEqualTo("observed-old")
        assertThat(response.purgedGrantIds).containsExactly("revoked-grant-id")
        assertThat(response.nextCursor).isEqualTo("cursor-2")
    }
}
