package com.neo.yourtodo.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder",
    indices = [
        Index(value = ["isEnabled", "triggerAtEpochMillis"]),
        Index(value = ["updatedAt"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val note: String?,
    val triggerAtEpochMillis: Long,
    val repeatType: String,
    val repeatDaysMask: Int,
    val isEnabled: Boolean,
    val status: String,
    val lastTriggeredAtEpochMillis: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
