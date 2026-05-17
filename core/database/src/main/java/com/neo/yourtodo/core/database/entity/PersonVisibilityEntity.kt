package com.neo.yourtodo.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "visibility_grants",
    primaryKeys = ["currentUserId", "grantId"],
    indices = [
        Index(value = ["currentUserId", "ownerUserId", "viewerUserId"], unique = true),
        Index(value = ["currentUserId", "status"])
    ]
)
data class VisibilityGrantEntity(
    val currentUserId: String,
    val grantId: String,
    val ownerUserId: String,
    val viewerUserId: String,
    val status: String,
    val version: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val revokedAtEpochMillis: Long?
)

@Entity(
    tableName = "observed_todos",
    primaryKeys = ["currentUserId", "observedTodoId"],
    indices = [
        Index(value = ["currentUserId", "ownerUserId", "dueDateEpochDay"]),
        Index(value = ["currentUserId", "grantId"]),
        Index(value = ["currentUserId", "sourceTodoId"])
    ]
)
data class ObservedTodoEntity(
    val currentUserId: String,
    val observedTodoId: String,
    val sourceTodoId: String,
    val grantId: String,
    val ownerUserId: String,
    val ownerNickname: String,
    val ownerAvatarUrl: String?,
    val title: String,
    val dueDateEpochDay: Long?,
    val dueTimeMinutes: Int?,
    val isDone: Boolean,
    val recurrenceOccurrenceId: String?,
    val projectionVersion: Long,
    val updatedAtEpochMillis: Long,
    val cacheUpdatedAtEpochMillis: Long
)

@Entity(
    tableName = "observed_sync_state",
    primaryKeys = ["currentUserId"]
)
data class ObservedSyncStateEntity(
    val currentUserId: String,
    val cursor: String?,
    val syncedAtEpochMillis: Long
)
