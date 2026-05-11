package com.neo.yourtodo.core.model.aitodo

import com.neo.yourtodo.core.model.TodoPriority
import java.time.LocalDate

data class AiTodoPerson(
    val id: String,
    val displayName: String,
    val aliases: List<String>,
    val isSelf: Boolean
)

data class AiTodoDraft(
    val title: String,
    val assigneeId: String?,
    val dueDate: LocalDate?,
    val dueTimeMinutes: Int?,
    val priority: TodoPriority,
    val needsReview: Boolean,
    val reviewReason: String?
)

data class AiTodoDraftResult(
    val items: List<AiTodoDraft>,
    val model: String?,
    val fallbackUsed: Boolean
)
