package com.neo.yourtodo.core.model.personvisibility

import com.neo.yourtodo.core.model.TodoPriority
import java.time.LocalDate

data class ObservedTodo(
    val id: String,
    val ownerUserId: String,
    val title: String,
    val isDone: Boolean,
    val dueDate: LocalDate?,
    val dueTimeMinutes: Int?,
    val priority: TodoPriority,
    val categoryName: String? = null,
    val updatedAt: String? = null,
    val sourceTodoId: String? = null,
    val grantId: String? = null,
    val ownerNickname: String? = null,
    val ownerAvatarUrl: String? = null,
    val projectionVersion: Long = 0
)

data class ObservedPersonTodos(
    val ownerUserId: String,
    val todos: List<ObservedTodo>
)
