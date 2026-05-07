package com.neo.yourtodo.core.model

import java.time.LocalDate

data class TodoSummary(
    val id: Long,
    val title: String,
    val isDone: Boolean,
    val dueTimeMinutes: Int? = null,
    val priority: TodoPriority = TodoPriority.MEDIUM,
    val createdAt: Long = 0L
)

data class DateTodoSummary(
    val date: LocalDate,
    val todos: List<TodoSummary>,
    val indicatorCount: Int,
    val overflowCount: Int
)
