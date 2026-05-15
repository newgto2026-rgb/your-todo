package com.neo.yourtodo.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "assigned_todo",
    primaryKeys = ["ownerUserId", "id"],
    indices = [
        Index(value = ["ownerUserId", "receivedCached", "status"]),
        Index(value = ["ownerUserId", "receivedTaskHidden", "status"]),
        Index(value = ["ownerUserId", "sentCached", "status"]),
        Index(value = ["ownerUserId", "senderUserId", "status"]),
        Index(value = ["ownerUserId", "receiverUserId", "status"]),
        Index(value = ["cacheKey"], unique = true)
    ]
)
data class AssignedTodoEntity(
    val ownerUserId: String,
    val id: String,
    val cacheKey: String,
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
    val assignmentMode: String,
    val reminderAt: String?,
    val reminderEnabled: Boolean?,
    val createdAtEpochMillis: Long?,
    val completedAtEpochMillis: Long?,
    val receivedCached: Boolean,
    val receivedTaskHidden: Boolean,
    val sentCached: Boolean,
    val cacheUpdatedAt: Long
)

@Entity(
    tableName = "assigned_todo_checklist_item",
    primaryKeys = ["ownerUserId", "assignedTodoId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AssignedTodoEntity::class,
            parentColumns = ["ownerUserId", "id"],
            childColumns = ["ownerUserId", "assignedTodoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ownerUserId", "assignedTodoId"]),
        Index(value = ["assignedTodoCacheKey"])
    ]
)
data class AssignedTodoChecklistItemEntity(
    val ownerUserId: String,
    val assignedTodoId: String,
    val assignedTodoCacheKey: String,
    val id: String,
    val title: String,
    val completed: Boolean,
    val sortOrder: Int
)

fun assignedTodoCacheKey(ownerUserId: String, assignedTodoId: String): String =
    "${ownerUserId.length}:$ownerUserId$assignedTodoId"
