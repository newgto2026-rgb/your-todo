package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import java.time.LocalDate

data class TaskSurfaceItem(
    val id: Long,
    val title: String,
    val isDone: Boolean,
    val dueDate: LocalDate?,
    val dueTimeMinutes: Int?,
    val reminderAtEpochMillis: Long?,
    val isReminderEnabled: Boolean,
    val reminderLeadMinutes: Int?,
    val reminderRepeatType: ReminderRepeatType,
    val priority: TodoPriority,
    val source: TaskSurfaceSource,
    val senderNickname: String? = null,
    val assignmentMode: AssignmentMode = AssignmentMode.REQUEST
) {
    val localTodoId: Long?
        get() = (source as? TaskSurfaceSource.Local)?.todoId

    val assignedTodoId: String?
        get() = (source as? TaskSurfaceSource.Assigned)?.assignedTodoId

    val isAssigned: Boolean
        get() = source is TaskSurfaceSource.Assigned
}

sealed interface TaskSurfaceSource {
    data class Local(val todoId: Long) : TaskSurfaceSource
    data class Assigned(val assignedTodoId: String) : TaskSurfaceSource
}

data class TaskSurfaceList(
    val items: List<TaskSurfaceItem>,
    val sections: List<TaskSurfaceSection>,
    val completedLocalTodoIds: List<Long>,
    val completedAssignedTodoIds: List<String>
)

data class TaskSurfaceSection(
    val key: TaskSurfaceSectionKey,
    val items: List<TaskSurfaceItem>
)

sealed interface TaskSurfaceSectionKey {
    data object Open : TaskSurfaceSectionKey
    data object Completed : TaskSurfaceSectionKey
    data class Priority(val priority: TodoPriority) : TaskSurfaceSectionKey
    data class DueDate(val date: LocalDate?) : TaskSurfaceSectionKey
    data class Friend(val nickname: String?) : TaskSurfaceSectionKey
    data object Self : TaskSurfaceSectionKey
}

data class AssignedTaskSurfaceOverrides(
    val completedIds: Set<String> = emptySet(),
    val activeIds: Set<String> = emptySet(),
    val hiddenIds: Set<String> = emptySet()
)
