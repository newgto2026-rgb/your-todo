package com.neo.yourtodo.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

data class AssignedTodoWithChecklist(
    @Embedded val assignedTodo: AssignedTodoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "assignedTodoId"
    )
    val checklist: List<AssignedTodoChecklistItemEntity>
)
