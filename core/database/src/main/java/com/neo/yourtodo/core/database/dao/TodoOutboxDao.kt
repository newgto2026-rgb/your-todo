package com.neo.yourtodo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.neo.yourtodo.core.database.entity.TodoOutboxEntity

@Dao
interface TodoOutboxDao {
    @Query(
        """
        SELECT * FROM todo_outbox
        WHERE ownerUserId = :ownerUserId
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getPendingMutations(ownerUserId: String): List<TodoOutboxEntity>

    @Query("SELECT * FROM todo_outbox WHERE todoLocalId = :todoLocalId LIMIT 1")
    suspend fun getByTodoLocalId(todoLocalId: Long): TodoOutboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outbox: TodoOutboxEntity): Long

    @Update
    suspend fun update(outbox: TodoOutboxEntity)

    @Delete
    suspend fun delete(outbox: TodoOutboxEntity)

    @Query("DELETE FROM todo_outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM todo_outbox WHERE todoLocalId = :todoLocalId")
    suspend fun deleteByTodoLocalId(todoLocalId: Long)

    @Query("DELETE FROM todo_outbox WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)
}
