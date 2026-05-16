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
        WHERE ownerUserId = :ownerUserId
            AND receivedCached = 1
            AND receivedTaskHidden = 0
            AND status IN (:statuses)
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

    @Query(
        """
        SELECT MIN(cacheUpdatedAt) FROM assigned_todo
        WHERE ownerUserId = :ownerUserId
            AND receivedCached = 1
            AND receivedTaskHidden = 0
            AND status IN (:statuses)
        """
    )
    fun observeReceivedFeedCacheUpdatedAt(
        ownerUserId: String,
        statuses: List<String>
    ): Flow<Long?>

    @Query(
        """
        SELECT MIN(cacheUpdatedAt) FROM assigned_todo
        WHERE ownerUserId = :ownerUserId
            AND sentCached = 1
            AND status IN (:statuses)
        """
    )
    fun observeSentFeedCacheUpdatedAt(
        ownerUserId: String,
        statuses: List<String>
    ): Flow<Long?>

    @Query(
        """
        SELECT MIN(cacheUpdatedAt) FROM assigned_todo
        WHERE ownerUserId = :ownerUserId
            AND sentCached = 1
            AND receiverUserId = :friendUserId
            AND status IN (:statuses)
        """
    )
    fun observeSentFriendFeedCacheUpdatedAt(
        ownerUserId: String,
        friendUserId: String,
        statuses: List<String>
    ): Flow<Long?>

    @Query(
        """
        SELECT MIN(cacheUpdatedAt) FROM assigned_todo
        WHERE ownerUserId = :ownerUserId
            AND receivedCached = 1
            AND senderUserId = :friendUserId
            AND status IN (:statuses)
        """
    )
    fun observeReceivedFriendFeedCacheUpdatedAt(
        ownerUserId: String,
        friendUserId: String,
        statuses: List<String>
    ): Flow<Long?>

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
        val shouldPreserveFriendHistoryRows = statuses.any { it.isReceivedHistoryStatus() }
        if (retainedIds.isEmpty()) {
            if (shouldPreserveFriendHistoryRows) {
                hideReceivedByStatuses(ownerUserId, statuses)
            } else {
                deleteReceivedByStatuses(ownerUserId, statuses)
            }
        } else {
            if (shouldPreserveFriendHistoryRows) {
                hideReceivedByStatusesExcept(ownerUserId, statuses, retainedIds)
            } else {
                deleteReceivedByStatusesExcept(ownerUserId, statuses, retainedIds)
            }
        }
        upsertAssignedTodoGraph(items, checklistItems)
    }

    private fun String.isReceivedHistoryStatus(): Boolean =
        this == "DONE" || this == "REJECTED" || this == "CANCELED"

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
        val assignedTodoCacheKeys = items.map { it.cacheKey }
        upsertAssignedTodos(items)
        deleteChecklistItems(assignedTodoCacheKeys)
        if (checklistItems.isNotEmpty()) {
            upsertChecklistItems(checklistItems)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignedTodos(items: List<AssignedTodoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklistItems(items: List<AssignedTodoChecklistItemEntity>)

    @Query("DELETE FROM assigned_todo_checklist_item WHERE assignedTodoCacheKey IN (:assignedTodoCacheKeys)")
    suspend fun deleteChecklistItems(assignedTodoCacheKeys: List<String>)

    @Transaction
    suspend fun replaceChecklistItems(
        assignedTodoCacheKey: String,
        checklistItems: List<AssignedTodoChecklistItemEntity>
    ) {
        deleteChecklistItems(listOf(assignedTodoCacheKey))
        if (checklistItems.isNotEmpty()) {
            upsertChecklistItems(checklistItems)
        }
    }

    @Query("DELETE FROM assigned_todo WHERE ownerUserId = :ownerUserId AND receivedCached = 1 AND status IN (:statuses)")
    suspend fun deleteReceivedByStatuses(ownerUserId: String, statuses: List<String>)

    @Query(
        """
        UPDATE assigned_todo
        SET receivedTaskHidden = 1
        WHERE ownerUserId = :ownerUserId AND receivedCached = 1 AND status IN (:statuses)
        """
    )
    suspend fun hideReceivedByStatuses(ownerUserId: String, statuses: List<String>)

    @Query(
        """
        UPDATE assigned_todo
        SET receivedTaskHidden = 1
        WHERE ownerUserId = :ownerUserId AND id = :id AND receivedCached = 1
        """
    )
    suspend fun hideReceivedFromTaskSurface(ownerUserId: String, id: String)

    @Query(
        """
        DELETE FROM assigned_todo
        WHERE ownerUserId = :ownerUserId AND receivedCached = 1 AND status IN (:statuses) AND id NOT IN (:ids)
        """
    )
    suspend fun deleteReceivedByStatusesExcept(ownerUserId: String, statuses: List<String>, ids: List<String>)

    @Query(
        """
        UPDATE assigned_todo
        SET receivedTaskHidden = 1
        WHERE ownerUserId = :ownerUserId AND receivedCached = 1 AND status IN (:statuses) AND id NOT IN (:ids)
        """
    )
    suspend fun hideReceivedByStatusesExcept(ownerUserId: String, statuses: List<String>, ids: List<String>)

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
