package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import java.time.LocalDate
import javax.inject.Inject

class BuildTaskSurfaceDateTodosUseCase @Inject constructor() {
    operator fun invoke(
        selectedDate: LocalDate,
        localTodos: List<TodoItem>,
        assignedTodos: List<AssignedTodo>
    ): List<TaskSurfaceItem> {
        val selectedLocalTodos = localTodos
            .asSequence()
            .filter { it.dueDate == selectedDate }
            .map { it.toTaskSurfaceItem() }
            .sortedWith(selectedDateLocalComparator)
            .toList()

        val selectedAssignedTodos = assignedTodos
            .asSequence()
            .filter { it.dueDate == selectedDate }
            .map { it.toTaskSurfaceItem() }
            .sortedWith(selectedDateAssignedComparator)
            .toList()

        return selectedLocalTodos + selectedAssignedTodos
    }
}

private val selectedDateLocalComparator: Comparator<TaskSurfaceItem> =
    compareBy<TaskSurfaceItem> { it.isDone }
        .thenByDescending { it.priority.taskSurfaceSortRank() }
        .thenBy { it.id }

private val selectedDateAssignedComparator: Comparator<TaskSurfaceItem> =
    compareBy<TaskSurfaceItem> { it.isDone }
        .thenByDescending { it.priority.taskSurfaceSortRank() }
        .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
        .thenBy { it.title }
