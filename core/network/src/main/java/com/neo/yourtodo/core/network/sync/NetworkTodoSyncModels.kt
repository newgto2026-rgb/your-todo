package com.neo.yourtodo.core.network.sync

import kotlinx.serialization.Serializable

@Serializable
data class NetworkTodoSyncPullResponse(
    val todos: List<NetworkTodo>,
    val nextCursor: String,
    val hasMore: Boolean = false
)

@Serializable
data class NetworkTodoSyncPushRequest(
    val baseCursor: String?,
    val mutations: List<NetworkTodoMutation>
)

@Serializable
data class NetworkTodoMutation(
    val clientMutationId: String,
    val type: String,
    val id: String? = null,
    val clientId: String? = null,
    val payload: NetworkTodoMutationPayload? = null
)

/**
 * MVP server payload for personal Todo sync.
 *
 * Reminder trigger/repeat/lead settings remain Android-local until the server
 * contract is explicitly expanded for personal reminder sync.
 */
@Serializable
data class NetworkTodoMutationPayload(
    val title: String? = null,
    val description: String? = null,
    val dueDate: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val categoryId: Long? = null,
    val dueTimeMinutes: Int? = null
)

@Serializable
data class NetworkTodoSyncPushResponse(
    val results: List<NetworkTodoMutationResult>,
    val nextCursor: String
)

@Serializable
data class NetworkTodoMutationResult(
    val clientMutationId: String,
    val status: String,
    val todo: NetworkTodo? = null,
    val error: NetworkTodoMutationError? = null
)

@Serializable
data class NetworkTodoMutationError(
    val code: String,
    val message: String
)

/**
 * Server Todo snapshot for sync pull/push results.
 *
 * This mirrors the current server contract only; Android-local reminder fields
 * are not decoded from or encoded into this DTO.
 */
@Serializable
data class NetworkTodo(
    val id: String,
    val clientId: String,
    val title: String,
    val description: String? = null,
    val dueDate: String? = null,
    val status: String,
    val priority: String? = null,
    val categoryId: Long? = null,
    val dueTimeMinutes: Int? = null,
    val revision: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null
)
