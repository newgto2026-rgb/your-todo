package com.neo.yourtodo.core.model

import java.time.LocalDate

data class TodoSummary(
    val id: Long,
    val title: String,
    val isDone: Boolean
)

data class DateTodoSummary(
    val date: LocalDate,
    val todos: List<TodoSummary>,
    val indicatorCount: Int,
    val overflowCount: Int
)
