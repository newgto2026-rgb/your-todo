package com.neo.yourtodo.core.domain.mapper

import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoSummary

fun TodoItem.toSummary(): TodoSummary =
    TodoSummary(
        id = id,
        title = title,
        isDone = isDone
    )
