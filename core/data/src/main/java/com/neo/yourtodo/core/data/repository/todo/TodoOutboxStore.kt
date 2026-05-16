package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.data.di.TodoSyncPayloadJson
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.MUTATION_CREATE
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.MUTATION_DELETE
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.MUTATION_UPDATE
import com.neo.yourtodo.core.database.dao.TodoOutboxDao
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.database.entity.TodoOutboxEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
internal class TodoOutboxStore @Inject constructor(
    private val todoOutboxDao: TodoOutboxDao,
    @TodoSyncPayloadJson
    private val json: Json
) {
    suspend fun getPendingMutations(ownerUserId: String): List<TodoOutboxEntity> =
        todoOutboxDao.getPendingMutations(ownerUserId)

    suspend fun upsertCreate(ownerUserId: String, todo: TodoEntity) =
        upsertMutation(ownerUserId, todo, MUTATION_CREATE)

    suspend fun upsertUpdate(ownerUserId: String, todo: TodoEntity) =
        upsertMutation(ownerUserId, todo, MUTATION_UPDATE)

    suspend fun enqueueDelete(todo: TodoEntity) {
        todoOutboxDao.insert(
            TodoOutboxEntity(
                ownerUserId = todo.ownerUserId.orEmpty(),
                clientMutationId = UUID.randomUUID().toString(),
                todoLocalId = todo.id,
                serverId = todo.serverId,
                clientId = todo.clientId,
                type = MUTATION_DELETE,
                payloadJson = "{}",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteById(id: Long) {
        todoOutboxDao.deleteById(id)
    }

    suspend fun deleteByTodoLocalId(todoLocalId: Long) {
        todoOutboxDao.deleteByTodoLocalId(todoLocalId)
    }

    private suspend fun upsertMutation(
        ownerUserId: String,
        todo: TodoEntity,
        mutationType: String
    ) {
        val existingOutbox = todoOutboxDao.getByTodoLocalId(todo.id)
        val type = if (mutationType == MUTATION_UPDATE && existingOutbox?.type == MUTATION_CREATE) {
            MUTATION_CREATE
        } else {
            mutationType
        }
        val outbox = TodoOutboxEntity(
            id = existingOutbox?.id ?: 0L,
            ownerUserId = ownerUserId,
            clientMutationId = existingOutbox?.clientMutationId ?: UUID.randomUUID().toString(),
            todoLocalId = todo.id,
            serverId = todo.serverId,
            clientId = todo.clientId,
            type = type,
            payloadJson = json.encodeToString(todo.toSyncPayload()),
            createdAt = existingOutbox?.createdAt ?: System.currentTimeMillis(),
            retryCount = existingOutbox?.retryCount ?: 0,
            lastError = null
        )
        if (existingOutbox == null) {
            todoOutboxDao.insert(outbox)
        } else {
            todoOutboxDao.update(outbox)
        }
    }
}
