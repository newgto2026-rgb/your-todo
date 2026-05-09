package com.neo.yourtodo.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

data class AssignedTodoWithChecklist(
    @Embedded val assignedTodo: AssignedTodoEntity,
    @Relation(
        parentColumn = "cacheKey",
        entityColumn = "assignedTodoCacheKey"
    )
    val checklist: List<AssignedTodoChecklistItemEntity>
)
