package com.neo.yourtodo.core.data.repository

import android.util.Log
import com.neo.yourtodo.core.data.mapper.toDomain
import com.neo.yourtodo.core.data.sync.TodoSyncPayload
import com.neo.yourtodo.core.database.dao.CategoryDao
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.dao.TodoOutboxDao
import com.neo.yourtodo.core.database.entity.CategoryEntity
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.database.entity.TodoOutboxEntity
import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.repository.TodoCategoryRepository
import com.neo.yourtodo.core.domain.repository.TodoFilterRepository
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.repository.TodoReminderRepository
import com.neo.yourtodo.core.model.Category
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoCategoryFilter
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSyncStatus
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import com.neo.yourtodo.core.network.sync.NetworkTodo
import com.neo.yourtodo.core.network.sync.NetworkTodoMutation
import com.neo.yourtodo.core.network.sync.NetworkTodoMutationPayload
import com.neo.yourtodo.core.network.sync.NetworkTodoSyncPushRequest
import com.neo.yourtodo.core.network.sync.TodoSyncAuthRequiredException
import com.neo.yourtodo.core.network.sync.TodoSyncNetworkDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class TodoRepositoryImpl @Inject constructor(
    private val todoDao: TodoDao,
    private val categoryDao: CategoryDao,
    private val todoOutboxDao: TodoOutboxDao,
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val todoSyncNetworkDataSource: TodoSyncNetworkDataSource,
    private val authNetworkDataSource: AuthNetworkDataSource
) : TodoItemRepository, TodoCategoryRepository, TodoFilterRepository, TodoReminderRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val syncMutex = Mutex()

    override fun observeTodos(): Flow<List<TodoItem>> =
        todoDao.observeTodos().map { entities -> entities.map { it.toDomain() } }

    override fun observeTodosByDueDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TodoItem>> =
        todoDao.observeTodosByDueDateRange(
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = endDate.toEpochDay()
        ).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTodo(id: Long): TodoItem? = todoDao.getTodoById(id)?.toDomain()

    override suspend fun getTodosWithActiveReminder(): List<TodoItem> =
        todoDao.getTodosWithActiveReminder().map { it.toDomain() }

    override suspend fun addTodo(
        title: String,
        dueDate: LocalDate?,
        categoryId: Long?,
        dueTimeMinutes: Int?,
        reminderAtEpochMillis: Long?,
        isReminderEnabled: Boolean,
        reminderRepeatType: ReminderRepeatType,
        reminderRepeatDaysMask: Int,
        reminderLeadMinutes: Int?,
        priority: TodoPriority
    ): Result<Long> = runCatching {
        validateCategoryId(categoryId)
        val now = System.currentTimeMillis()
        val session = currentSessionForSync()
        val syncStatus = if (session == null) TodoSyncStatus.LOCAL_ONLY else TodoSyncStatus.PENDING_CREATE
        val clientId = session?.let { UUID.randomUUID().toString() }
        val todo = TodoEntity(
            title = title,
            isDone = false,
            dueDateEpochDay = dueDate?.toEpochDay(),
            dueTimeMinutes = dueTimeMinutes,
            reminderAtEpochMillis = reminderAtEpochMillis,
            isReminderEnabled = isReminderEnabled && reminderAtEpochMillis != null,
            reminderRepeatType = reminderRepeatType.name,
            reminderRepeatDaysMask = reminderRepeatDaysMask,
            reminderLeadMinutes = reminderLeadMinutes,
            createdAt = now,
            updatedAt = now,
            categoryId = categoryId,
            priority = priority.name,
            clientId = clientId,
            ownerUserId = session?.userId,
            syncStatus = syncStatus.name
        )
        val id = todoDao.insert(todo)
        if (session != null && clientId != null) {
            upsertCreateOutbox(
                ownerUserId = session.userId,
                todo = todo.copy(id = id)
            )
        }
        id
    }.onFailure { throwable ->
        logError("addTodo", throwable)
    }

    override suspend fun updateTodo(
        id: Long,
        title: String,
        dueDate: LocalDate?,
        categoryId: Long?,
        dueTimeMinutes: Int?,
        reminderAtEpochMillis: Long?,
        isReminderEnabled: Boolean,
        reminderRepeatType: ReminderRepeatType,
        reminderRepeatDaysMask: Int,
        reminderLeadMinutes: Int?,
        priority: TodoPriority
    ): Result<Unit> = runCatching {
        validateCategoryId(categoryId)
        val existing = todoDao.getTodoById(id) ?: throw IllegalStateException("Todo not found")
        val updated = existing.copy(
            title = title,
            dueDateEpochDay = dueDate?.toEpochDay(),
            dueTimeMinutes = dueTimeMinutes,
            reminderAtEpochMillis = reminderAtEpochMillis,
            isReminderEnabled = isReminderEnabled && reminderAtEpochMillis != null,
            reminderRepeatType = reminderRepeatType.name,
            reminderRepeatDaysMask = reminderRepeatDaysMask,
            reminderLeadMinutes = reminderLeadMinutes,
            updatedAt = System.currentTimeMillis(),
            categoryId = categoryId,
            priority = priority.name
        )
        updateTodoWithOutbox(existing, updated)
    }.onFailure { throwable ->
        logError("updateTodo", throwable)
    }

    override suspend fun deleteTodo(id: Long): Result<Unit> = runCatching {
        val existing = todoDao.getTodoById(id) ?: throw IllegalStateException("Todo not found")
        deleteTodoWithOutbox(existing)
    }.onFailure { throwable ->
        logError("deleteTodo", throwable)
    }

    override suspend fun toggleTodoDone(id: Long): Result<Unit> = runCatching {
        val existing = todoDao.getTodoById(id) ?: throw IllegalStateException("Todo not found")
        val updated = existing.copy(
            isDone = !existing.isDone,
            isReminderEnabled = if (!existing.isDone) false else existing.isReminderEnabled,
            reminderAtEpochMillis = if (!existing.isDone) null else existing.reminderAtEpochMillis,
            reminderRepeatType = if (!existing.isDone) ReminderRepeatType.NONE.name else existing.reminderRepeatType,
            reminderRepeatDaysMask = if (!existing.isDone) 0 else existing.reminderRepeatDaysMask,
            updatedAt = System.currentTimeMillis()
        )
        updateTodoWithOutbox(existing, updated)
    }.onFailure { throwable ->
        logError("toggleTodoDone", throwable)
    }

    override suspend fun syncTodos(): Result<Unit> =
        if (!syncMutex.tryLock()) {
            Result.success(Unit)
        } else {
            try {
                runCatching {
                    val session = currentSessionForSync() ?: return@runCatching
                    userPreferencesDataSource.setTodoSyncHaltReason(null)
                    try {
                        syncTodosWithSession(session.accessToken, session.userId)
                    } catch (throwable: TodoSyncAuthRequiredException) {
                        val refreshedSession = refreshSessionOrNull(session.refreshToken)
                        if (refreshedSession == null) {
                            userPreferencesDataSource.setTodoSyncHaltReason(SYNC_HALT_AUTH_REQUIRED)
                            userPreferencesDataSource.clearAuthSession()
                            throw throwable
                        }

                        try {
                            userPreferencesDataSource.setTodoSyncHaltReason(null)
                            syncTodosWithSession(refreshedSession.accessToken, refreshedSession.user.id)
                        } catch (retryThrowable: TodoSyncAuthRequiredException) {
                            userPreferencesDataSource.setTodoSyncHaltReason(SYNC_HALT_AUTH_REQUIRED)
                            userPreferencesDataSource.clearAuthSession()
                            throw retryThrowable
                        }
                    }
                }.onFailure { throwable ->
                    logError("syncTodos", throwable)
                }
            } finally {
                syncMutex.unlock()
            }
        }

    override fun observeSelectedFilter(): Flow<TodoFilter> = userPreferencesDataSource.selectedTodoFilter

    override suspend fun setSelectedFilter(filter: TodoFilter): Result<Unit> = runCatching {
        userPreferencesDataSource.setSelectedTodoFilter(filter)
    }.onFailure { throwable ->
        logError("setSelectedFilter", throwable)
    }

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeCategories().map { categories -> categories.map { it.toDomain() } }

    override suspend fun addCategory(name: String, colorHex: String?, icon: String?): Result<Long> = runCatching {
        ensureUniqueCategoryName(name, excludeCategoryId = null)
        val now = System.currentTimeMillis()
        categoryDao.insert(
            CategoryEntity(
                name = name,
                colorHex = colorHex,
                icon = icon,
                createdAt = now,
                updatedAt = now
            )
        )
    }.onFailure { throwable ->
        logError("addCategory", throwable)
    }

    override suspend fun updateCategory(id: Long, name: String, colorHex: String?, icon: String?): Result<Unit> = runCatching {
        val existing = categoryDao.getCategoryById(id) ?: throw IllegalStateException("Category not found")
        ensureUniqueCategoryName(name, excludeCategoryId = id)
        categoryDao.update(
            existing.copy(
                name = name,
                colorHex = colorHex,
                icon = icon,
                updatedAt = System.currentTimeMillis()
            )
        )
    }.onFailure { throwable ->
        logError("updateCategory", throwable)
    }

    override suspend fun deleteCategory(id: Long): Result<Unit> = runCatching {
        val existing = categoryDao.getCategoryById(id) ?: throw IllegalStateException("Category not found")
        categoryDao.delete(existing)

        // 선택된 카테고리가 삭제되면 전체(All)로 fallback
        if (userPreferencesDataSource.selectedTodoCategoryFilter.first() == id) {
            userPreferencesDataSource.setSelectedTodoCategoryFilter(null)
        }
    }.onFailure { throwable ->
        logError("deleteCategory", throwable)
    }

    override fun observeSelectedCategoryFilter(): Flow<Long?> =
        userPreferencesDataSource.selectedTodoCategoryFilter

    override suspend fun setSelectedCategoryFilter(categoryId: Long?): Result<Unit> = runCatching {
        val isUncategorized = categoryId == TodoCategoryFilter.UNCATEGORIZED_FILTER_ID
        if (categoryId != null && !isUncategorized && categoryDao.getCategoryById(categoryId) == null) {
            throw IllegalArgumentException("Category not found")
        }
        userPreferencesDataSource.setSelectedTodoCategoryFilter(categoryId)
    }.onFailure { throwable ->
        logError("setSelectedCategoryFilter", throwable)
    }

    override fun observeSelectedPriorityFilter(): Flow<TodoPriorityFilter> =
        userPreferencesDataSource.selectedTodoPriorityFilter

    override suspend fun setSelectedPriorityFilter(filter: TodoPriorityFilter): Result<Unit> = runCatching {
        userPreferencesDataSource.setSelectedTodoPriorityFilter(filter)
    }.onFailure { throwable ->
        logError("setSelectedPriorityFilter", throwable)
    }

    private suspend fun validateCategoryId(categoryId: Long?) {
        if (categoryId != null && categoryDao.getCategoryById(categoryId) == null) {
            throw IllegalArgumentException("Category not found")
        }
    }

    private suspend fun currentSessionForSync() =
        userPreferencesDataSource.authSession.first()
            ?.takeUnless { it.onboardingRequired }

    private suspend fun syncTodosWithSession(accessToken: String, ownerUserId: String) {
        pullTodos(accessToken, ownerUserId)
        pushTodos(accessToken, ownerUserId)
        pullTodos(accessToken, ownerUserId)
    }

    private suspend fun refreshSessionOrNull(refreshToken: String): NetworkAuthSession? =
        runCatching {
            authNetworkDataSource.refreshSession(refreshToken)
        }.getOrNull()?.also { networkSession ->
            userPreferencesDataSource.saveAuthSession(networkSession.toAuthSessionData())
        }

    private suspend fun updateTodoWithOutbox(existing: TodoEntity, updated: TodoEntity) {
        when (syncStatusOf(existing)) {
            TodoSyncStatus.LOCAL_ONLY -> todoDao.update(updated)
            TodoSyncStatus.PENDING_DELETE -> throw IllegalStateException("Todo is pending delete")
            TodoSyncStatus.PENDING_CREATE -> {
                val pending = updated.copy(syncStatus = TodoSyncStatus.PENDING_CREATE.name, lastSyncError = null)
                todoDao.update(pending)
                pending.ownerUserId?.let { ownerUserId ->
                    upsertCreateOutbox(ownerUserId, pending)
                }
            }
            TodoSyncStatus.SYNCED,
            TodoSyncStatus.PENDING_UPDATE,
            TodoSyncStatus.FAILED -> {
                val ownerUserId = existing.ownerUserId
                val serverId = existing.serverId
                if (ownerUserId.isNullOrBlank() || serverId.isNullOrBlank()) {
                    todoDao.update(updated.copy(syncStatus = TodoSyncStatus.LOCAL_ONLY.name))
                } else {
                    val pending = updated.copy(syncStatus = TodoSyncStatus.PENDING_UPDATE.name, lastSyncError = null)
                    todoDao.update(pending)
                    upsertUpdateOutbox(ownerUserId, pending)
                }
            }
        }
    }

    private suspend fun deleteTodoWithOutbox(existing: TodoEntity) {
        when (syncStatusOf(existing)) {
            TodoSyncStatus.LOCAL_ONLY -> todoDao.delete(existing)
            TodoSyncStatus.PENDING_CREATE -> {
                todoOutboxDao.deleteByTodoLocalId(existing.id)
                todoDao.delete(existing)
            }
            TodoSyncStatus.PENDING_DELETE -> Unit
            TodoSyncStatus.SYNCED,
            TodoSyncStatus.PENDING_UPDATE,
            TodoSyncStatus.FAILED -> {
                val ownerUserId = existing.ownerUserId
                val serverId = existing.serverId
                if (ownerUserId.isNullOrBlank() || serverId.isNullOrBlank()) {
                    todoDao.delete(existing)
                } else {
                    val pending = existing.copy(
                        syncStatus = TodoSyncStatus.PENDING_DELETE.name,
                        deletedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        lastSyncError = null
                    )
                    todoDao.update(pending)
                    todoOutboxDao.deleteByTodoLocalId(existing.id)
                    todoOutboxDao.insert(
                        TodoOutboxEntity(
                            ownerUserId = ownerUserId,
                            clientMutationId = UUID.randomUUID().toString(),
                            todoLocalId = existing.id,
                            serverId = serverId,
                            clientId = existing.clientId,
                            type = MUTATION_DELETE,
                            payloadJson = "{}",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private suspend fun upsertCreateOutbox(ownerUserId: String, todo: TodoEntity) {
        val existingOutbox = todoOutboxDao.getByTodoLocalId(todo.id)
        val outbox = TodoOutboxEntity(
            id = existingOutbox?.id ?: 0L,
            ownerUserId = ownerUserId,
            clientMutationId = existingOutbox?.clientMutationId ?: UUID.randomUUID().toString(),
            todoLocalId = todo.id,
            serverId = todo.serverId,
            clientId = todo.clientId,
            type = MUTATION_CREATE,
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

    private suspend fun upsertUpdateOutbox(ownerUserId: String, todo: TodoEntity) {
        val existingOutbox = todoOutboxDao.getByTodoLocalId(todo.id)
        val type = if (existingOutbox?.type == MUTATION_CREATE) MUTATION_CREATE else MUTATION_UPDATE
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
        val outboxItems = todoOutboxDao.getPendingMutations(ownerUserId)
        if (outboxItems.isEmpty()) return

        val response = todoSyncNetworkDataSource.pushTodos(
            accessToken = accessToken,
            request = NetworkTodoSyncPushRequest(
                baseCursor = userPreferencesDataSource.todoSyncCursor.first(),
                mutations = outboxItems.map { it.toNetworkMutation() }
            )
        )

        response.results.forEach { result ->
            val outbox = outboxItems.firstOrNull { it.clientMutationId == result.clientMutationId } ?: return@forEach
            when (result.status) {
                RESULT_APPLIED,
                RESULT_DUPLICATE_APPLIED,
                RESULT_DUPLICATE_CLIENT_ID -> {
                    result.todo?.let { todo -> applyRemoteTodo(ownerUserId, todo) }
                    todoOutboxDao.deleteById(outbox.id)
                }
                RESULT_REJECTED_DELETED -> {
                    result.todo?.let { todo -> applyRemoteTodo(ownerUserId, todo) }
                    todoOutboxDao.deleteById(outbox.id)
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
                    todoOutboxDao.deleteById(outbox.id)
                }
            }
        }
        userPreferencesDataSource.setTodoSyncCursor(response.nextCursor)
    }

    private suspend fun applyRemoteTodo(ownerUserId: String, remote: NetworkTodo) {
        val existing = todoDao.getTodoByServerId(ownerUserId, remote.id)
            ?: todoDao.getTodoByClientId(ownerUserId, remote.clientId)
        val remoteDeleted = remote.status == STATUS_DELETED || remote.deletedAt != null
        if (existing == null) {
            if (!remoteDeleted) {
                todoDao.insert(remote.toEntity(ownerUserId))
            }
            return
        }

        val localStatus = syncStatusOf(existing)
        when {
            remoteDeleted -> {
                todoOutboxDao.deleteByTodoLocalId(existing.id)
                todoDao.update(remote.toEntity(ownerUserId, existing.id))
            }
            localStatus == TodoSyncStatus.PENDING_DELETE -> Unit
            localStatus == TodoSyncStatus.PENDING_UPDATE -> Unit
            else -> {
                todoDao.update(remote.toEntity(ownerUserId, existing.id))
                todoOutboxDao.deleteByTodoLocalId(existing.id)
            }
        }
    }

    private fun TodoOutboxEntity.toNetworkMutation(): NetworkTodoMutation {
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
                    status = it.status
                )
            }
        )
    }

    private fun TodoEntity.toSyncPayload(): TodoSyncPayload =
        TodoSyncPayload(
            title = title,
            dueDate = dueDateEpochDay?.let { LocalDate.ofEpochDay(it).toString() },
            status = if (isDone) STATUS_COMPLETED else STATUS_ACTIVE
        )

    private fun NetworkTodo.toEntity(ownerUserId: String, localId: Long = 0L): TodoEntity =
        TodoEntity(
            id = localId,
            title = title,
            isDone = status == STATUS_COMPLETED,
            dueDateEpochDay = dueDate?.let(LocalDate::parse)?.toEpochDay(),
            createdAt = parseInstantMillis(createdAt),
            updatedAt = parseInstantMillis(updatedAt),
            categoryId = null,
            priority = TodoPriority.MEDIUM.name,
            serverId = id,
            clientId = clientId,
            ownerUserId = ownerUserId,
            syncStatus = TodoSyncStatus.SYNCED.name,
            serverRevision = revision,
            deletedAt = deletedAt?.let(::parseInstantMillis),
            lastSyncError = null
        )

    private fun NetworkAuthSession.toAuthSessionData() =
        AuthSessionData(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            nickname = user.nickname,
            email = user.email,
            onboardingRequired = user.onboardingRequired
        )

    private fun parseInstantMillis(value: String): Long =
        Instant.parse(value).toEpochMilli()

    private fun syncStatusOf(todo: TodoEntity): TodoSyncStatus =
        TodoSyncStatus.entries.find { it.name == todo.syncStatus } ?: TodoSyncStatus.LOCAL_ONLY

    private suspend fun ensureUniqueCategoryName(name: String, excludeCategoryId: Long?) {
        val duplicate = categoryDao.getCategoryByName(name)
        if (duplicate != null && duplicate.id != excludeCategoryId) {
            throw IllegalArgumentException("Category name already exists")
        }

        // room의 NOCASE index가 locale 독립적이지 않을 수 있어 repo 레벨에서 한 번 더 정규화 검증
        val normalized = name.lowercase(Locale.ROOT)
        if (duplicate != null && duplicate.id != excludeCategoryId && duplicate.name.lowercase(Locale.ROOT) == normalized) {
            throw IllegalArgumentException("Category name already exists")
        }
    }

    private fun logError(action: String, throwable: Throwable) {
        Log.e(TAG, "action=$action failure=${throwable.message}", throwable)
    }

    private companion object {
        private const val TAG = "TodoRepository"
        private const val MUTATION_CREATE = "CREATE"
        private const val MUTATION_UPDATE = "UPDATE"
        private const val MUTATION_DELETE = "DELETE"
        private const val STATUS_ACTIVE = "ACTIVE"
        private const val STATUS_COMPLETED = "COMPLETED"
        private const val STATUS_DELETED = "DELETED"
        private const val RESULT_APPLIED = "APPLIED"
        private const val RESULT_DUPLICATE_APPLIED = "DUPLICATE_APPLIED"
        private const val RESULT_DUPLICATE_CLIENT_ID = "DUPLICATE_CLIENT_ID"
        private const val RESULT_REJECTED_VALIDATION = "REJECTED_VALIDATION"
        private const val RESULT_REJECTED_NOT_FOUND = "REJECTED_NOT_FOUND"
        private const val RESULT_REJECTED_DELETED = "REJECTED_DELETED"
        private const val RESULT_REJECTED_IDEMPOTENCY_CONFLICT = "REJECTED_IDEMPOTENCY_CONFLICT"
        private const val SYNC_HALT_AUTH_REQUIRED = "AUTH_REQUIRED"
    }
}
