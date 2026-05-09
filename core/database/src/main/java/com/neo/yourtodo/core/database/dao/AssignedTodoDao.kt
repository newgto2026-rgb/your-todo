package com.neo.yourtodo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.neo.yourtodo.core.database.entity.AssignedTodoChecklistItemEntity
import com.neo.yourtodo.core.database.entity.AssignedTodoEntity
import com.neo.yourtodo.core.database.entity.AssignedTodoWithChecklist
import kotlinx.coroutines.flow.Flow

@Dao
interface AssignedTodoDao {
    @Transaction
    @Query(
        """
        SELECT * FROM assigned_todo
        WHERE ownerUserId = :ownerUserId AND receivedCached = 1 AND status IN (:statuses)
        ORDER BY createdAtEpochMillis DESC, id ASC
        """
    )
    fun observeReceivedAssignedTodos(
        ownerUserId: String,
        statuses: List<String>
    ): Flow<List<AssignedTodoWithChecklist>>

    @Transaction
    @Query(
        """
        SELECT * FROM assigned_todo
        WHERE ownerUserId = :ownerUserId AND sentCached = 1 AND status IN (:statuses)
        ORDER BY createdAtEpochMillis DESC, id ASC
        """
    )
    fun observeSentAssignedTodos(
        ownerUserId: String,
        statuses: List<String>
    ): Flow<List<AssignedTodoWithChecklist>>

    @Transaction
    @Query(
        """
        SELECT * FROM assigned_todo
        WHERE ownerUserId = :ownerUserId
            AND sentCached = 1
            AND receiverUserId = :friendUserId
            AND status IN (:statuses)
        ORDER BY createdAtEpochMillis DESC, id ASC
        """
    )
    fun observeSentAssignedTodosByFriend(
        ownerUserId: String,
        friendUserId: String,
        statuses: List<String>
    ): Flow<List<AssignedTodoWithChecklist>>

    @Transaction
    @Query(
        """
        SELECT * FROM assigned_todo
        WHERE ownerUserId = :ownerUserId
            AND receivedCached = 1
            AND senderUserId = :friendUserId
            AND status IN (:statuses)
        ORDER BY createdAtEpochMillis DESC, id ASC
        """
    )
    fun observeReceivedAssignedTodosByFriend(
        ownerUserId: String,
        friendUserId: String,
        statuses: List<String>
    ): Flow<List<AssignedTodoWithChecklist>>

    @Query("SELECT * FROM assigned_todo WHERE ownerUserId = :ownerUserId AND id = :id LIMIT 1")
    suspend fun getAssignedTodoById(ownerUserId: String, id: String): AssignedTodoEntity?

    @Query("DELETE FROM assigned_todo WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)

    @Transaction
    suspend fun replaceReceivedCache(
        ownerUserId: String,
        statuses: List<String>,
        retainedIds: List<String>,
        items: List<AssignedTodoEntity>,
        checklistItems: List<AssignedTodoChecklistItemEntity>
    ) {
        if (retainedIds.isEmpty()) {
            deleteReceivedByStatuses(ownerUserId, statuses)
        } else {
            deleteReceivedByStatusesExcept(ownerUserId, statuses, retainedIds)
        }
        upsertAssignedTodoGraph(items, checklistItems)
    }

    @Transaction
    suspend fun replaceSentCache(
        ownerUserId: String,
        statuses: List<String>,
        retainedIds: List<String>,
        items: List<AssignedTodoEntity>,
        checklistItems: List<AssignedTodoChecklistItemEntity>
    ) {
        if (retainedIds.isEmpty()) {
            deleteSentByStatuses(ownerUserId, statuses)
        } else {
            deleteSentByStatusesExcept(ownerUserId, statuses, retainedIds)
        }
        upsertAssignedTodoGraph(items, checklistItems)
    }

    @Transaction
    suspend fun replaceReceivedFriendCache(
        ownerUserId: String,
        friendUserId: String,
        statuses: List<String>,
        retainedIds: List<String>,
        items: List<AssignedTodoEntity>,
        checklistItems: List<AssignedTodoChecklistItemEntity>
    ) {
        if (retainedIds.isEmpty()) {
            deleteReceivedByFriendAndStatuses(ownerUserId, friendUserId, statuses)
        } else {
            deleteReceivedByFriendAndStatusesExcept(ownerUserId, friendUserId, statuses, retainedIds)
        }
        upsertAssignedTodoGraph(items, checklistItems)
    }

    @Transaction
    suspend fun replaceSentFriendCache(
        ownerUserId: String,
        friendUserId: String,
        statuses: List<String>,
        retainedIds: List<String>,
        items: List<AssignedTodoEntity>,
        checklistItems: List<AssignedTodoChecklistItemEntity>
    ) {
        if (retainedIds.isEmpty()) {
            deleteSentByFriendAndStatuses(ownerUserId, friendUserId, statuses)
        } else {
            deleteSentByFriendAndStatusesExcept(ownerUserId, friendUserId, statuses, retainedIds)
        }
        upsertAssignedTodoGraph(items, checklistItems)
    }

    @Transaction
    suspend fun upsertAssignedTodoGraph(
        items: List<AssignedTodoEntity>,
        checklistItems: List<AssignedTodoChecklistItemEntity>
    ) {
        if (items.isEmpty()) return
        val assignedTodoIds = items.map { it.id }
        upsertAssignedTodos(items)
        deleteChecklistItems(assignedTodoIds)
        if (checklistItems.isNotEmpty()) {
            upsertChecklistItems(checklistItems)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignedTodos(items: List<AssignedTodoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklistItems(items: List<AssignedTodoChecklistItemEntity>)

    @Query("DELETE FROM assigned_todo_checklist_item WHERE assignedTodoId IN (:assignedTodoIds)")
    suspend fun deleteChecklistItems(assignedTodoIds: List<String>)

    @Query("DELETE FROM assigned_todo WHERE ownerUserId = :ownerUserId AND receivedCached = 1 AND status IN (:statuses)")
    suspend fun deleteReceivedByStatuses(ownerUserId: String, statuses: List<String>)

    @Query(
        """
        DELETE FROM assigned_todo
        WHERE ownerUserId = :ownerUserId AND receivedCached = 1 AND status IN (:statuses) AND id NOT IN (:ids)
        """
    )
    suspend fun deleteReceivedByStatusesExcept(ownerUserId: String, statuses: List<String>, ids: List<String>)

    @Query("DELETE FROM assigned_todo WHERE ownerUserId = :ownerUserId AND sentCached = 1 AND status IN (:statuses)")
    suspend fun deleteSentByStatuses(ownerUserId: String, statuses: List<String>)

    @Query(
        """
        DELETE FROM assigned_todo
        WHERE ownerUserId = :ownerUserId AND sentCached = 1 AND status IN (:statuses) AND id NOT IN (:ids)
        """
    )
    suspend fun deleteSentByStatusesExcept(ownerUserId: String, statuses: List<String>, ids: List<String>)

    @Query(
        """
        DELETE FROM assigned_todo
        WHERE ownerUserId = :ownerUserId
            AND sentCached = 1
            AND receiverUserId = :friendUserId
            AND status IN (:statuses)
        """
    )
    suspend fun deleteSentByFriendAndStatuses(ownerUserId: String, friendUserId: String, statuses: List<String>)

    @Query(
        """
        DELETE FROM assigned_todo
        WHERE ownerUserId = :ownerUserId
            AND sentCached = 1
            AND receiverUserId = :friendUserId
            AND status IN (:statuses)
            AND id NOT IN (:ids)
        """
    )
    suspend fun deleteSentByFriendAndStatusesExcept(
        ownerUserId: String,
        friendUserId: String,
        statuses: List<String>,
        ids: List<String>
    )

    @Query(
        """
        DELETE FROM assigned_todo
        WHERE ownerUserId = :ownerUserId
            AND receivedCached = 1
            AND senderUserId = :friendUserId
            AND status IN (:statuses)
        """
    )
    suspend fun deleteReceivedByFriendAndStatuses(ownerUserId: String, friendUserId: String, statuses: List<String>)

    @Query(
        """
        DELETE FROM assigned_todo
        WHERE ownerUserId = :ownerUserId
            AND receivedCached = 1
            AND senderUserId = :friendUserId
            AND status IN (:statuses)
            AND id NOT IN (:ids)
        """
    )
    suspend fun deleteReceivedByFriendAndStatusesExcept(
        ownerUserId: String,
        friendUserId: String,
        statuses: List<String>,
        ids: List<String>
    )
}
