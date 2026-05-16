package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.data.di.TodoSyncPayloadJson
import com.neo.yourtodo.core.data.repository.AssignmentFeedFreshnessTracker
import com.neo.yourtodo.core.data.repository.AuthSessionRefresher
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.RESULT_APPLIED
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.RESULT_DUPLICATE_APPLIED
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.RESULT_DUPLICATE_CLIENT_ID
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.RESULT_REJECTED_DELETED
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.RESULT_REJECTED_IDEMPOTENCY_CONFLICT
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.RESULT_REJECTED_NOT_FOUND
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.RESULT_REJECTED_VALIDATION
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.STATUS_DELETED
import com.neo.yourtodo.core.data.repository.todo.TodoSyncConstants.SYNC_HALT_AUTH_REQUIRED
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.entity.TodoOutboxEntity
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.model.TodoSyncStatus
import com.neo.yourtodo.core.network.sync.NetworkTodo
import com.neo.yourtodo.core.network.sync.NetworkTodoSyncPushRequest
import com.neo.yourtodo.core.network.sync.TodoSyncAuthRequiredException
import com.neo.yourtodo.core.network.sync.TodoSyncNetworkDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json

@Singleton
internal class TodoSyncCoordinator @Inject constructor(
    private val todoDao: TodoDao,
    private val outboxStore: TodoOutboxStore,
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val todoSyncNetworkDataSource: TodoSyncNetworkDataSource,
    private val authSessionRefresher: AuthSessionRefresher,
    private val syncSessionProvider: TodoSyncSessionProvider,
    private val assignmentFeedFreshnessTracker: AssignmentFeedFreshnessTracker,
    @TodoSyncPayloadJson
    private val json: Json
) {
    private val syncMutex = Mutex()

    suspend fun syncTodos() {
        if (!syncMutex.tryLock()) return

        try {
            val session = syncSessionProvider.currentSessionForSync() ?: return
            userPreferencesDataSource.setTodoSyncHaltReason(null)
            try {
                syncTodosWithSession(session.accessToken, session.userId)
            } catch (throwable: TodoSyncAuthRequiredException) {
                val refreshedSession = authSessionRefresher.refresh(session.refreshToken)
                if (refreshedSession == null) {
                    userPreferencesDataSource.setTodoSyncHaltReason(SYNC_HALT_AUTH_REQUIRED)
                    clearAuthSession()
                    throw throwable
                }

                try {
                    userPreferencesDataSource.setTodoSyncHaltReason(null)
                    syncTodosWithSession(refreshedSession.accessToken, refreshedSession.userId)
                } catch (retryThrowable: TodoSyncAuthRequiredException) {
                    userPreferencesDataSource.setTodoSyncHaltReason(SYNC_HALT_AUTH_REQUIRED)
                    clearAuthSession()
                    throw retryThrowable
                }
            }
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun syncTodosWithSession(accessToken: String, ownerUserId: String) {
        pullTodos(accessToken, ownerUserId)
        pushTodos(accessToken, ownerUserId)
        pullTodos(accessToken, ownerUserId)
    }

    private suspend fun clearAuthSession() {
        assignmentFeedFreshnessTracker.clear()
        userPreferencesDataSource.clearAssignmentFeedRefreshTimes()
        userPreferencesDataSource.clearAuthSession()
    }

    private suspend fun pullTodos(accessToken: String, ownerUserId: String) {
        var cursor = userPreferencesDataSource.todoSyncCursor.first()
        do {
            val response = todoSyncNetworkDataSource.pullTodos(accessToken, cursor)
            response.todos.forEach { todo -> applyRemoteTodo(ownerUserId, todo) }
            userPreferencesDataSource.setTodoSyncCursor(response.nextCursor)
            cursor = response.nextCursor
        } while (response.hasMore)
    }

    private suspend fun pushTodos(accessToken: String, ownerUserId: String) {
        val outboxItems = outboxStore.getPendingMutations(ownerUserId)
        if (outboxItems.isEmpty()) return
        val fallbackFieldsByTodoId = getFallbackFieldsByTodoId(outboxItems)

        val response = todoSyncNetworkDataSource.pushTodos(
            accessToken = accessToken,
            request = NetworkTodoSyncPushRequest(
                baseCursor = userPreferencesDataSource.todoSyncCursor.first(),
                mutations = outboxItems.map { outbox ->
                    outbox.toNetworkMutation(
                        json = json,
                        fallbackFields = outbox.todoLocalId?.let(fallbackFieldsByTodoId::get)
                    )
                }
            )
        )

        response.results.forEach { result ->
            val outbox = outboxItems.firstOrNull { it.clientMutationId == result.clientMutationId } ?: return@forEach
            when (result.status) {
                RESULT_APPLIED,
                RESULT_DUPLICATE_APPLIED,
                RESULT_DUPLICATE_CLIENT_ID -> {
                    result.todo?.let { todo -> applyRemoteTodo(ownerUserId, todo) }
                    outboxStore.deleteById(outbox.id)
                }
                RESULT_REJECTED_DELETED -> {
                    result.todo?.let { todo -> applyRemoteTodo(ownerUserId, todo) }
                    outboxStore.deleteById(outbox.id)
                }
                RESULT_REJECTED_VALIDATION,
                RESULT_REJECTED_NOT_FOUND,
                RESULT_REJECTED_IDEMPOTENCY_CONFLICT -> {
                    outbox.todoLocalId?.let { localId ->
                        todoDao.getTodoById(localId)?.let { todo ->
                            todoDao.update(
                                todo.copy(
                                    syncStatus = TodoSyncStatus.FAILED.name,
                                    lastSyncError = result.error?.code ?: result.status
                                )
                            )
                        }
                    }
                    outboxStore.deleteById(outbox.id)
                }
            }
        }
        userPreferencesDataSource.setTodoSyncCursor(response.nextCursor)
    }

    private suspend fun getFallbackFieldsByTodoId(
        outboxItems: List<TodoOutboxEntity>
    ): Map<Long, TodoSyncFallbackFields> {
        val todoLocalIds = outboxItems.mapNotNull { it.todoLocalId }.distinct()
        if (todoLocalIds.isEmpty()) return emptyMap()

        return todoDao.getTodosByIds(todoLocalIds)
            .associate { todo ->
                todo.id to TodoSyncFallbackFields(
                    priority = todo.priority,
                    categoryId = todo.categoryId,
                    dueTimeMinutes = todo.dueTimeMinutes
                )
            }
    }

    private suspend fun applyRemoteTodo(ownerUserId: String, remote: NetworkTodo) {
        val existing = todoDao.getTodoByServerId(ownerUserId, remote.id)
            ?: todoDao.getTodoByClientId(ownerUserId, remote.clientId)
        val remoteDeleted = remote.status == STATUS_DELETED || remote.deletedAt != null
        if (existing == null) {
            if (!remoteDeleted) {
                todoDao.insert(remote.toTodoEntity(ownerUserId))
            }
            return
        }

        val localStatus = existing.syncStatus()
        when {
            remoteDeleted -> {
                outboxStore.deleteByTodoLocalId(existing.id)
                todoDao.update(
                    remote.toTodoEntity(
                        ownerUserId = ownerUserId,
                        localId = existing.id,
                        fallbackPriority = existing.priority
                    )
                )
            }
            localStatus == TodoSyncStatus.PENDING_DELETE -> Unit
            localStatus == TodoSyncStatus.PENDING_UPDATE -> Unit
            else -> {
                todoDao.update(
                    remote.toTodoEntity(
                        ownerUserId = ownerUserId,
                        localId = existing.id,
                        fallbackPriority = existing.priority,
                        preservedLocalFields = existing
                    )
                )
                outboxStore.deleteByTodoLocalId(existing.id)
            }
        }
    }
}
