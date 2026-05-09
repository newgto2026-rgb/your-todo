package com.neo.yourtodo.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "assigned_todo",
    indices = [
        Index(value = ["ownerUserId", "receivedCached", "status"]),
        Index(value = ["ownerUserId", "sentCached", "status"]),
        Index(value = ["ownerUserId", "senderUserId", "status"]),
        Index(value = ["ownerUserId", "receiverUserId", "status"])
    ]
)
data class AssignedTodoEntity(
    @PrimaryKey
    val id: String,
    val ownerUserId: String,
    val bundleId: String?,
    val title: String,
    val description: String?,
    val dueDateEpochDay: Long?,
    val dueTimeMinutes: Int?,
    val priority: String,
    val category: String?,
    val status: String,
    val terminalReason: String?,
    val progressPercent: Int,
    val senderUserId: String?,
    val senderNickname: String?,
    val receiverUserId: String?,
    val receiverNickname: String?,
    val reminderAt: String?,
    val reminderEnabled: Boolean?,
    val createdAtEpochMillis: Long?,
    val completedAtEpochMillis: Long?,
    val receivedCached: Boolean,
    val sentCached: Boolean,
    val cacheUpdatedAt: Long
)

@Entity(
    tableName = "assigned_todo_checklist_item",
    primaryKeys = ["assignedTodoId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AssignedTodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["assignedTodoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["assignedTodoId"])
    ]
)
data class AssignedTodoChecklistItemEntity(
    val assignedTodoId: String,
    val id: String,
    val title: String,
    val completed: Boolean,
    val sortOrder: Int
)
