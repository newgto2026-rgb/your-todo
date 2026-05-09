package com.neo.yourtodo.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todo_outbox",
    indices = [
        Index(value = ["clientMutationId"], unique = true),
        Index(value = ["todoLocalId"]),
        Index(value = ["ownerUserId", "createdAt"])
    ]
)
data class TodoOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val ownerUserId: String,
    val clientMutationId: String,
    val todoLocalId: Long?,
    val serverId: String?,
    val clientId: String?,
    val type: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
)
