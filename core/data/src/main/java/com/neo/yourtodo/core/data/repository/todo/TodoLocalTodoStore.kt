package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.data.mapper.toDomain
import com.neo.yourtodo.core.database.dao.TodoDao
import com.neo.yourtodo.core.database.entity.TodoEntity
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSyncStatus
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
internal class TodoLocalTodoStore @Inject constructor(
    private val todoDao: TodoDao,
    private val categoryStore: TodoCategoryStore,
    private val outboxStore: TodoOutboxStore,
    private val syncSessionProvider: TodoSyncSessionProvider,
    private val transactionRunner: TodoTransactionRunner,
    private val timeProvider: TodoTimeProvider
) {
    fun observeTodos(): Flow<List<TodoItem>> =
        todoDao.observeTodos().map { entities -> entities.map { it.toDomain() } }

    fun observeTodosByDueDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TodoItem>> =
        todoDao.observeTodosByDueDateRange(
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = endDate.toEpochDay()
        ).map { entities -> entities.map { it.toDomain() } }

    suspend fun getTodo(id: Long): TodoItem? =
        todoDao.getTodoById(id)?.toDomain()

    suspend fun addTodo(
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
    ): Long = transactionRunner.runInTransaction {
        categoryStore.requireCategoryExists(categoryId)
        val now = timeProvider.currentTimeMillis()
        val session = syncSessionProvider.currentSessionForSync()
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
            outboxStore.upsertCreate(
                ownerUserId = session.userId,
                todo = todo.copy(id = id)
            )
        }
        id
    }

    suspend fun updateTodo(
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
    ) = transactionRunner.runInTransaction {
        categoryStore.requireCategoryExists(categoryId)
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
            updatedAt = timeProvider.currentTimeMillis(),
            categoryId = categoryId,
            priority = priority.name
        )
        updateTodoWithOutbox(existing, updated)
    }

    suspend fun deleteTodo(id: Long) = transactionRunner.runInTransaction {
        val existing = todoDao.getTodoById(id) ?: throw IllegalStateException("Todo not found")
        deleteTodoWithOutbox(existing)
    }

    suspend fun toggleTodoDone(id: Long) = transactionRunner.runInTransaction {
        val existing = todoDao.getTodoById(id) ?: throw IllegalStateException("Todo not found")
        val updated = existing.copy(
            isDone = !existing.isDone,
            isReminderEnabled = if (!existing.isDone) false else existing.isReminderEnabled,
            reminderAtEpochMillis = if (!existing.isDone) null else existing.reminderAtEpochMillis,
            reminderRepeatType = if (!existing.isDone) ReminderRepeatType.NONE.name else existing.reminderRepeatType,
            reminderRepeatDaysMask = if (!existing.isDone) 0 else existing.reminderRepeatDaysMask,
            updatedAt = timeProvider.currentTimeMillis()
        )
        updateTodoWithOutbox(existing, updated)
    }

    private suspend fun updateTodoWithOutbox(existing: TodoEntity, updated: TodoEntity) {
        when (existing.syncStatus()) {
            TodoSyncStatus.LOCAL_ONLY -> todoDao.update(updated)
            TodoSyncStatus.PENDING_DELETE -> throw IllegalStateException("Todo is pending delete")
            TodoSyncStatus.PENDING_CREATE -> {
                val pending = updated.copy(syncStatus = TodoSyncStatus.PENDING_CREATE.name, lastSyncError = null)
                todoDao.update(pending)
                pending.ownerUserId?.let { ownerUserId ->
                    outboxStore.upsertCreate(ownerUserId, pending)
                }
            }
            TodoSyncStatus.SYNCED,
            TodoSyncStatus.PENDING_UPDATE,
            TodoSyncStatus.FAILED -> {
                if (!existing.hasRemoteIdentity()) {
                    todoDao.update(updated.copy(syncStatus = TodoSyncStatus.LOCAL_ONLY.name))
                } else {
                    val pending = updated.copy(syncStatus = TodoSyncStatus.PENDING_UPDATE.name, lastSyncError = null)
                    todoDao.update(pending)
                    outboxStore.upsertUpdate(existing.ownerUserId.orEmpty(), pending)
                }
            }
        }
    }

    private suspend fun deleteTodoWithOutbox(existing: TodoEntity) {
        when (existing.syncStatus()) {
            TodoSyncStatus.LOCAL_ONLY -> todoDao.delete(existing)
            TodoSyncStatus.PENDING_CREATE -> {
                outboxStore.deleteByTodoLocalId(existing.id)
                todoDao.delete(existing)
            }
            TodoSyncStatus.PENDING_DELETE -> Unit
            TodoSyncStatus.SYNCED,
            TodoSyncStatus.PENDING_UPDATE,
            TodoSyncStatus.FAILED -> {
                if (!existing.hasRemoteIdentity()) {
                    todoDao.delete(existing)
                } else {
                    val now = timeProvider.currentTimeMillis()
                    val pending = existing.copy(
                        syncStatus = TodoSyncStatus.PENDING_DELETE.name,
                        deletedAt = now,
                        updatedAt = now,
                        lastSyncError = null
                    )
                    todoDao.update(pending)
                    outboxStore.deleteByTodoLocalId(existing.id)
                    outboxStore.enqueueDelete(existing)
                }
            }
        }
    }
}
