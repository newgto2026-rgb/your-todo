package com.neo.yourtodo.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todo",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["isReminderEnabled", "reminderAtEpochMillis"])
    ]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val isDone: Boolean,
    val dueDateEpochDay: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val categoryId: Long?,
    val reminderAtEpochMillis: Long? = null,
    val isReminderEnabled: Boolean = false,
    val reminderRepeatType: String = "NONE",
    val reminderRepeatDaysMask: Int = 0,
    val dueTimeMinutes: Int? = null,
    val reminderLeadMinutes: Int? = null,
    val priority: String = "MEDIUM"
)
