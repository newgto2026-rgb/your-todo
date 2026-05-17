package com.neo.yourtodo.core.network.personvisibility

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class NetworkVisibilityGrantsResponse(
    val given: List<NetworkVisibilityGrant> = emptyList(),
    val received: List<NetworkVisibilityGrant> = emptyList()
)

@Serializable
data class NetworkCreateVisibilityGrantRequest(
    val viewerUserId: String
)

@Serializable
data class NetworkVisibilityGrantMutationResponse(
    val grant: NetworkVisibilityGrant
)

@Serializable
data class NetworkVisibilityGrant(
    val id: String,
    val owner: NetworkVisibilityUser,
    val viewer: NetworkVisibilityUser,
    val status: String,
    val createdAt: String,
    val updatedAt: String? = null,
    val revokedAt: String? = null,
    val version: Long = 0
)

@Serializable
data class NetworkVisibilityUser(
    val id: String,
    val nickname: String = "",
    val avatarUrl: String? = null
)

@Serializable
data class NetworkRevokeVisibilityGrantResponse(
    val grantId: String,
    val status: String,
    val version: Long = 0,
    val revokedAt: String? = null
)

@Serializable
data class NetworkObservedTodoSyncResponse(
    val items: List<NetworkObservedTodo> = emptyList(),
    val deleted: List<NetworkDeletedObservedTodo> = emptyList(),
    val purgedGrantIds: List<String> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)

@Serializable
data class NetworkObservedTodo(
    @SerialName("id")
    val observedTodoId: String,
    @SerialName("clientId")
    val sourceTodoId: String,
    val grantId: String,
    val owner: NetworkObservedTodoOwner,
    val title: String,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val status: String = "ACTIVE",
    val isDone: Boolean? = null,
    val recurrenceOccurrenceId: String? = null,
    val projectionVersion: Long = 0,
    @SerialName("revision")
    val revision: String? = null,
    val updatedAt: String
)

@Serializable
data class NetworkObservedTodoOwner(
    val id: String,
    val nickname: String,
    val avatarUrl: String? = null
)

@Serializable
data class NetworkDeletedObservedTodo(
    @SerialName("id")
    val observedTodoId: String,
    val projectionVersion: Long = 0,
    @SerialName("revision")
    val revision: String? = null
)
