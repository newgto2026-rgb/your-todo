package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.core.model.TodoSortOption
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class BuildTaskSurfaceListUseCase @Inject constructor() {
    operator fun invoke(
        localTodos: List<TodoItem>,
        assignedTodos: List<AssignedTodo>,
        selectedFilter: TodoFilter,
        selectedPriorityFilter: TodoPriorityFilter,
        selectedSortOption: TodoSortOption,
        today: LocalDate,
        zoneId: ZoneId,
        assignedOverrides: AssignedTaskSurfaceOverrides = AssignedTaskSurfaceOverrides()
    ): TaskSurfaceList {
        val surfaceItems = localTodos.map { it.toTaskSurfaceItem() } +
            assignedTodos
                .asSequence()
                .filterNot { it.id in assignedOverrides.hiddenIds }
                .map { assignedTodo ->
                    assignedTodo.toTaskSurfaceItem(
                        zoneId = zoneId,
                        isDoneOverride = when {
                            assignedTodo.id in assignedOverrides.activeIds -> false
                            assignedTodo.id in assignedOverrides.completedIds -> true
                            else -> null
                        }
                    )
                }
                .toList()

        return surfaceItems.toTaskSurfaceList(
            selectedFilter = selectedFilter,
            selectedPriorityFilter = selectedPriorityFilter,
            selectedSortOption = selectedSortOption,
            today = today
        )
    }

    private fun List<TaskSurfaceItem>.toTaskSurfaceList(
        selectedFilter: TodoFilter,
        selectedPriorityFilter: TodoPriorityFilter,
        selectedSortOption: TodoSortOption,
        today: LocalDate
    ): TaskSurfaceList {
        val filteredItems = filterBy(selectedFilter, today)
            .filterByPriority(selectedPriorityFilter)
        val sections = filteredItems.sectionsFor(
            filter = selectedFilter,
            sortOption = selectedSortOption
        )
        val sortedItems = if (sections.isEmpty()) {
            filteredItems.sortedWith(selectedSortOption.comparatorFor(selectedFilter))
        } else {
            sections.flatMap { it.items }
        }

        return TaskSurfaceList(
            items = sortedItems,
            sections = sections,
            completedLocalTodoIds = mapNotNull { item ->
                item.localTodoId?.takeIf { item.isDone }
            },
            completedAssignedTodoIds = mapNotNull { item ->
                item.assignedTodoId?.takeIf { item.isDone }
            }
        )
    }
}

private fun List<TaskSurfaceItem>.filterBy(
    filter: TodoFilter,
    today: LocalDate
): List<TaskSurfaceItem> =
    when (filter) {
        TodoFilter.ALL -> this
        TodoFilter.TODAY -> filter {
            !it.isDone && (
                it.dueDate == today ||
                    it.dueDate?.isBefore(today) == true
                )
        }
        TodoFilter.COMPLETED -> filter { it.isDone }
    }

private fun List<TaskSurfaceItem>.filterByPriority(
    filter: TodoPriorityFilter
): List<TaskSurfaceItem> = when (filter) {
    TodoPriorityFilter.ALL -> this
    TodoPriorityFilter.LOW -> filter { it.priority == TodoPriority.LOW }
    TodoPriorityFilter.MEDIUM -> filter { it.priority == TodoPriority.MEDIUM }
    TodoPriorityFilter.HIGH -> filter { it.priority == TodoPriority.HIGH }
}

private fun TodoSortOption.comparatorFor(filter: TodoFilter): Comparator<TaskSurfaceItem> =
    if (filter == TodoFilter.ALL) {
        completionLastComparator(itemComparator())
    } else {
        contextualTaskSurfaceComparator()
    }

private fun List<TaskSurfaceItem>.sectionsFor(
    filter: TodoFilter,
    sortOption: TodoSortOption
): List<TaskSurfaceSection> {
    if (filter != TodoFilter.ALL) return emptyList()

    val itemComparator = sortOption.itemComparator()
    val activeItems = filterNot { it.isDone }.sortedWith(itemComparator)
    val completedItems = filter { it.isDone }.sortedWith(itemComparator)
    val activeSections = when (sortOption) {
        TodoSortOption.DEFAULT -> if (completedItems.isEmpty()) {
            emptyList()
        } else {
            listOfNotNull(
                activeItems.takeIf { it.isNotEmpty() }?.let {
                    TaskSurfaceSection(TaskSurfaceSectionKey.Open, it)
                }
            )
        }
        TodoSortOption.DUE_DATE -> activeItems.groupByInOrder(
            keySelector = { TaskSurfaceSectionKey.DueDate(it.dueDate) }
        )
        TodoSortOption.PRIORITY -> listOf(
            TodoPriority.HIGH,
            TodoPriority.MEDIUM,
            TodoPriority.LOW
        ).mapNotNull { priority ->
            activeItems
                .filter { it.priority == priority }
                .takeIf { it.isNotEmpty() }
                ?.let { TaskSurfaceSection(TaskSurfaceSectionKey.Priority(priority), it) }
        }
        TodoSortOption.FRIEND -> activeItems.groupByInOrder(
            keySelector = { it.friendSectionKey() }
        )
    }
    val completedSection = completedItems
        .takeIf { it.isNotEmpty() }
        ?.let { TaskSurfaceSection(TaskSurfaceSectionKey.Completed, it) }

    return activeSections + listOfNotNull(completedSection)
}

private fun List<TaskSurfaceItem>.groupByInOrder(
    keySelector: (TaskSurfaceItem) -> TaskSurfaceSectionKey
): List<TaskSurfaceSection> {
    val sections = linkedMapOf<TaskSurfaceSectionKey, MutableList<TaskSurfaceItem>>()
    forEach { item ->
        sections.getOrPut(keySelector(item)) { mutableListOf() }.add(item)
    }
    return sections.map { (key, items) -> TaskSurfaceSection(key, items) }
}

private fun TodoSortOption.itemComparator(): Comparator<TaskSurfaceItem> =
    when (this) {
        TodoSortOption.DEFAULT -> defaultTaskSurfaceComparator()
        TodoSortOption.DUE_DATE -> dueDateTaskSurfaceComparator()
        TodoSortOption.PRIORITY -> priorityTaskSurfaceComparator()
        TodoSortOption.FRIEND -> friendTaskSurfaceComparator()
    }

private fun completionLastComparator(
    itemComparator: Comparator<TaskSurfaceItem>
): Comparator<TaskSurfaceItem> =
    compareBy<TaskSurfaceItem> { it.isDone }
        .then(itemComparator)

private fun defaultTaskSurfaceComparator(): Comparator<TaskSurfaceItem> =
    compareByDescending { it.id }

private fun contextualTaskSurfaceComparator(): Comparator<TaskSurfaceItem> =
    compareBy<TaskSurfaceItem> { it.isDone }
        .thenByDescending { it.priority.taskSurfaceSortRank() }
        .thenBy { it.id }

private fun dueDateTaskSurfaceComparator(): Comparator<TaskSurfaceItem> =
    compareBy<TaskSurfaceItem> { it.dueDate == null }
        .thenBy { it.dueDate ?: LocalDate.MAX }
        .thenBy { it.dueTimeMinutes == null }
        .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
        .thenByDescending { it.priority.taskSurfaceSortRank() }
        .thenBy { it.id }

private fun priorityTaskSurfaceComparator(): Comparator<TaskSurfaceItem> =
    compareByDescending<TaskSurfaceItem> { it.priority.taskSurfaceSortRank() }
        .thenBy { it.dueDate == null }
        .thenBy { it.dueDate ?: LocalDate.MAX }
        .thenBy { it.dueTimeMinutes == null }
        .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
        .thenBy { it.id }

private fun friendTaskSurfaceComparator(): Comparator<TaskSurfaceItem> =
    compareBy<TaskSurfaceItem> { if (it.isAssigned) 0 else 1 }
        .thenBy { it.senderNickname?.trim()?.lowercase().orEmpty() }
        .thenBy { it.dueDate == null }
        .thenBy { it.dueDate ?: LocalDate.MAX }
        .thenBy { it.dueTimeMinutes == null }
        .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
        .thenByDescending { it.priority.taskSurfaceSortRank() }
        .thenBy { it.id }

private fun TaskSurfaceItem.friendSectionKey(): TaskSurfaceSectionKey =
    if (isAssigned) {
        TaskSurfaceSectionKey.Friend(senderNickname?.trim()?.takeIf { it.isNotEmpty() })
    } else {
        TaskSurfaceSectionKey.Self
    }
