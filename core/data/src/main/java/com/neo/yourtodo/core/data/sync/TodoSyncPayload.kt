package com.neo.yourtodo.core.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class TodoSyncPayload(
    val title: String,
    val description: String? = null,
    val dueDate: String? = null,
    val status: String = "ACTIVE",
    val priority: String? = null
)
