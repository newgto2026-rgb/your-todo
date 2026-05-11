package com.neo.yourtodo.core.network.aitodo

import kotlinx.serialization.Serializable

@Serializable
data class NetworkAiTodoDraftRequest(
    val text: String,
    val now: String,
    val timezone: String,
    val locale: String,
    val people: List<NetworkAiTodoPerson>
)

@Serializable
data class NetworkAiTodoPerson(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val isSelf: Boolean
)

@Serializable
data class NetworkAiTodoDraftResponse(
    val items: List<NetworkAiTodoDraft>,
    val model: String? = null,
    val fallbackUsed: Boolean = false
)

@Serializable
data class NetworkAiTodoDraft(
    val title: String,
    val assigneeId: String? = null,
    val dueDate: String? = null,
    val dueTimeMinutes: Int? = null,
    val priority: String? = null,
    val needsReview: Boolean = false,
    val reviewReason: String? = null
)
