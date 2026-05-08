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

@Serializable
data class NetworkTodoMutationPayload(
    val title: String? = null,
    val description: String? = null,
    val dueDate: String? = null,
    val status: String? = null
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

@Serializable
data class NetworkTodo(
    val id: String,
    val clientId: String,
    val title: String,
    val description: String? = null,
    val dueDate: String? = null,
    val status: String,
    val revision: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null
)
