package com.neo.yourtodo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.neo.yourtodo.core.database.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query(
        """
        SELECT * FROM todo
        WHERE deletedAt IS NULL AND syncStatus != 'PENDING_DELETE'
        ORDER BY createdAt DESC
        """
    )
    fun observeTodos(): Flow<List<TodoEntity>>

    @Query(
        """
        SELECT * FROM todo
        WHERE dueDateEpochDay IS NOT NULL
          AND dueDateEpochDay BETWEEN :startEpochDay AND :endEpochDay
          AND deletedAt IS NULL
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY dueDateEpochDay ASC, createdAt DESC
        """
    )
    fun observeTodosByDueDateRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TodoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("SELECT * FROM todo WHERE id = :id LIMIT 1")
    suspend fun getTodoById(id: Long): TodoEntity?

    @Query("SELECT * FROM todo WHERE id IN (:ids)")
    suspend fun getTodosByIds(ids: List<Long>): List<TodoEntity>

    @Query("SELECT * FROM todo WHERE ownerUserId = :ownerUserId AND serverId = :serverId LIMIT 1")
    suspend fun getTodoByServerId(ownerUserId: String, serverId: String): TodoEntity?

    @Query("SELECT * FROM todo WHERE ownerUserId = :ownerUserId AND clientId = :clientId LIMIT 1")
    suspend fun getTodoByClientId(ownerUserId: String, clientId: String): TodoEntity?

    @Query("DELETE FROM todo WHERE ownerUserId = :ownerUserId AND syncStatus != 'LOCAL_ONLY'")
    suspend fun deleteSyncedTodosByOwner(ownerUserId: String)

    @Query("DELETE FROM todo WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)

    @Query(
        """
        SELECT * FROM todo
        WHERE isReminderEnabled = 1 AND reminderAtEpochMillis IS NOT NULL
        ORDER BY reminderAtEpochMillis ASC
        """
    )
    suspend fun getTodosWithActiveReminder(): List<TodoEntity>
}
