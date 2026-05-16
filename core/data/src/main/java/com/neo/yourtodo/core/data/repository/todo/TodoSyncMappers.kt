package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.MUTATION_DELETE
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.STATUS_COMPLETED
import com.neo.yourtodo.core.data.sync.TodoSyncPayload
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.database.entity.TodoOutboxEntity
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSyncStatus
import com.neo.yourtodo.core.network.sync.NetworkTodo
import com.neo.yourtodo.core.network.sync.NetworkTodoMutation
import com.neo.yourtodo.core.network.sync.NetworkTodoMutationPayload
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal fun TodoEntity.hasRemoteIdentity(): Boolean =
    !ownerUserId.isNullOrBlank() && !serverId.isNullOrBlank()

internal fun TodoEntity.syncStatus(): TodoSyncStatus =
    TodoSyncStatus.entries.find { it.name == syncStatus } ?: TodoSyncStatus.LOCAL_ONLY

internal fun TodoEntity.toSyncPayload(): TodoSyncPayload =
    TodoSyncFieldPolicy.createPayload(this)

internal fun TodoOutboxEntity.toNetworkMutation(
    json: Json,
    fallbackPriority: String?
): NetworkTodoMutation {
    val payload = if (type == MUTATION_DELETE) null else json.decodeFromString<TodoSyncPayload>(payloadJson)
    return NetworkTodoMutation(
        clientMutationId = clientMutationId,
        type = type,
        id = serverId,
        clientId = clientId,
        payload = payload?.let {
            NetworkTodoMutationPayload(
                title = it.title,
                description = it.description,
                dueDate = it.dueDate,
                status = it.status,
                priority = it.priority.toTodoPriorityNameOrNull(fallbackPriority)
            )
        }
    )
}

internal fun NetworkTodo.toTodoEntity(
    ownerUserId: String,
    localId: Long = 0L,
    fallbackPriority: String? = null
): TodoEntity =
    TodoEntity(
        id = localId,
        title = title,
        isDone = status == STATUS_COMPLETED,
        dueDateEpochDay = dueDate?.let(LocalDate::parse)?.toEpochDay(),
        createdAt = parseInstantMillis(createdAt),
        updatedAt = parseInstantMillis(updatedAt),
        categoryId = null,
        priority = priority.toTodoPriorityName(fallbackPriority),
        serverId = id,
        clientId = clientId,
        ownerUserId = ownerUserId,
        syncStatus = TodoSyncStatus.SYNCED.name,
        serverRevision = revision,
        deletedAt = deletedAt?.let(::parseInstantMillis),
        lastSyncError = null
    )

private fun String?.toTodoPriorityName(fallbackPriority: String?): String =
    toTodoPriorityNameOrNull(fallbackPriority) ?: TodoPriority.MEDIUM.name

private fun String?.toTodoPriorityNameOrNull(fallbackPriority: String?): String? {
    val entries = TodoPriority.entries
    return entries.firstOrNull { it.name.equals(this, ignoreCase = true) }?.name
        ?: entries.firstOrNull { it.name.equals(fallbackPriority, ignoreCase = true) }?.name
}

private fun parseInstantMillis(value: String): Long =
    Instant.parse(value).toEpochMilli()
