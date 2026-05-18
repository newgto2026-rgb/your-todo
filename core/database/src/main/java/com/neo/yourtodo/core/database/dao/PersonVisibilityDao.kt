package com.neo.yourtodo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.neo.yourtodo.core.database.entity.ObservedSyncStateEntity
import com.neo.yourtodo.core.database.entity.ObservedTodoEntity
import com.neo.yourtodo.core.database.entity.VisibilityGrantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonVisibilityDao {
    @Query(
        """
        SELECT * FROM visibility_grants
        WHERE currentUserId = :currentUserId
        ORDER BY updatedAtEpochMillis DESC, grantId ASC
        """
    )
    fun observeVisibilityGrants(currentUserId: String): Flow<List<VisibilityGrantEntity>>

    @Query(
        """
        SELECT * FROM visibility_grants
        WHERE currentUserId = :currentUserId
            AND ownerUserId = :ownerUserId
            AND viewerUserId = :viewerUserId
            AND status = 'ACTIVE'
        LIMIT 1
        """
    )
    suspend fun getActiveGrant(
        currentUserId: String,
        ownerUserId: String,
        viewerUserId: String
    ): VisibilityGrantEntity?

    @Query(
        """
        SELECT * FROM observed_todos
        WHERE currentUserId = :currentUserId
        ORDER BY dueDateEpochDay IS NULL, dueDateEpochDay ASC, dueTimeMinutes IS NULL, dueTimeMinutes ASC,
            updatedAtEpochMillis DESC, observedTodoId ASC
        """
    )
    fun observeObservedTodos(currentUserId: String): Flow<List<ObservedTodoEntity>>

    @Query("SELECT * FROM observed_sync_state WHERE currentUserId = :currentUserId LIMIT 1")
    suspend fun getObservedSyncState(currentUserId: String): ObservedSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVisibilityGrants(grants: List<VisibilityGrantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertObservedTodos(todos: List<ObservedTodoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertObservedSyncState(state: ObservedSyncStateEntity)

    @Query("DELETE FROM observed_todos WHERE currentUserId = :currentUserId AND observedTodoId IN (:observedTodoIds)")
    suspend fun deleteObservedTodos(currentUserId: String, observedTodoIds: List<String>)

    @Query("DELETE FROM observed_todos WHERE currentUserId = :currentUserId AND grantId = :grantId")
    suspend fun purgeObservedTodosByGrantId(currentUserId: String, grantId: String)

    @Query("DELETE FROM observed_todos WHERE currentUserId = :currentUserId")
    suspend fun purgeObservedTodosByCurrentUser(currentUserId: String)

    @Query("DELETE FROM observed_todos WHERE currentUserId = :currentUserId AND grantId NOT IN (:grantIds)")
    suspend fun purgeObservedTodosExceptGrantIds(currentUserId: String, grantIds: List<String>)

    @Query("DELETE FROM visibility_grants WHERE currentUserId = :currentUserId")
    suspend fun deleteVisibilityGrantsByCurrentUser(currentUserId: String)

    @Transaction
    suspend fun replaceVisibilityGrants(
        currentUserId: String,
        grants: List<VisibilityGrantEntity>
    ) {
        deleteVisibilityGrantsByCurrentUser(currentUserId)
        if (grants.isNotEmpty()) {
            upsertVisibilityGrants(grants)
        }
    }

    @Transaction
    suspend fun replaceVisibilityGrantsAndPruneObservedTodos(
        currentUserId: String,
        grants: List<VisibilityGrantEntity>,
        activeObservedGrantIds: List<String>
    ) {
        replaceVisibilityGrants(currentUserId, grants)
        if (activeObservedGrantIds.isEmpty()) {
            purgeObservedTodosByCurrentUser(currentUserId)
        } else {
            purgeObservedTodosExceptGrantIds(currentUserId, activeObservedGrantIds)
        }
    }

    @Transaction
    suspend fun applyObservedTodoSync(
        currentUserId: String,
        upserts: List<ObservedTodoEntity>,
        deletedObservedTodoIds: List<String>,
        purgedGrantIds: List<String>,
        state: ObservedSyncStateEntity
    ) {
        if (deletedObservedTodoIds.isNotEmpty()) {
            deleteObservedTodos(currentUserId, deletedObservedTodoIds)
        }
        purgedGrantIds.forEach { grantId ->
            purgeObservedTodosByGrantId(currentUserId, grantId)
        }
        if (upserts.isNotEmpty()) {
            upsertObservedTodos(upserts)
        }
        upsertObservedSyncState(state)
    }
}
