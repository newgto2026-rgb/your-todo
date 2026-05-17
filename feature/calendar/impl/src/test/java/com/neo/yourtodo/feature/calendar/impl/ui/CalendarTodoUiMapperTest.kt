package com.neo.yourtodo.feature.calendar.impl.ui

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.usecase.BuildTaskSurfaceDateTodosUseCase
import com.neo.yourtodo.core.model.TodoItem
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoReminder
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoUser
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class CalendarTodoUiMapperTest {

    @Test
    fun buildSelectedDateTodos_filtersSortsAndMapsLocalThenAssignedTodos() {
        val selectedDate = LocalDate.of(2026, 5, 9)
        val zoneId = ZoneId.of("Asia/Seoul")
        val reminderAt = selectedDate
            .atTime(LocalTime.of(8, 30))
            .atZone(zoneId)
            .toInstant()
            .toString()
        val todos = listOf(
            todo(id = 3, title = "Done high", dueDate = selectedDate, isDone = true, priority = TodoPriority.HIGH),
            todo(id = 2, title = "Selected low", dueDate = selectedDate, priority = TodoPriority.LOW),
            todo(id = 1, title = "Selected high", dueDate = selectedDate, priority = TodoPriority.HIGH),
            todo(id = 4, title = "Other date", dueDate = selectedDate.plusDays(1), priority = TodoPriority.HIGH)
        )
        val assignedTodos = listOf(
            assignedTodo(
                id = "assigned-low",
                title = "Assigned low",
                dueDate = selectedDate,
                priority = TodoPriority.LOW,
                dueTimeMinutes = 9 * 60
            ),
            assignedTodo(
                id = "assigned-high",
                title = "Assigned high",
                dueDate = selectedDate,
                priority = TodoPriority.HIGH,
                dueTimeMinutes = 9 * 60,
                assignmentMode = AssignmentMode.DIRECT,
                reminder = AssignedTodoReminder(reminderAt = reminderAt, enabled = true)
            ),
            assignedTodo(
                id = "assigned-other-date",
                title = "Assigned other date",
                dueDate = selectedDate.plusDays(1),
                priority = TodoPriority.HIGH
            )
        )

        val taskSurfaceItems = BuildTaskSurfaceDateTodosUseCase()(
            selectedDate = selectedDate,
            localTodos = todos,
            assignedTodos = assignedTodos,
            zoneId = zoneId
        )
        val uiModels = buildSelectedDateTodos(taskSurfaceItems, zoneId)

        assertThat(uiModels.map { it.title })
            .containsExactly("Selected high", "Selected low", "Done high", "Assigned high", "Assigned low")
            .inOrder()
        assertThat(uiModels.take(3).all { it.assignedTodoId == null }).isTrue()
        assertThat(uiModels[3].assignedTodoId).isEqualTo("assigned-high")
        assertThat(uiModels[3].sourceLabel).isEqualTo("@monday")
        assertThat(uiModels[3].assignmentMode).isEqualTo(AssignmentMode.DIRECT)
        assertThat(uiModels[3].reminderLeadMinutes).isEqualTo(30)
    }

    private fun todo(
        id: Long,
        title: String,
        dueDate: LocalDate?,
        isDone: Boolean = false,
        priority: TodoPriority
    ): TodoItem =
        TodoItem(
            id = id,
            title = title,
            isDone = isDone,
            dueDate = dueDate,
            createdAt = id,
            updatedAt = id,
            categoryId = null,
            priority = priority
        )

    private fun assignedTodo(
        id: String,
        title: String,
        dueDate: LocalDate,
        priority: TodoPriority = TodoPriority.MEDIUM,
        dueTimeMinutes: Int? = null,
        assignmentMode: AssignmentMode = AssignmentMode.REQUEST,
        reminder: AssignedTodoReminder? = null
    ): AssignedTodo =
        AssignedTodo(
            id = id,
            bundleId = "bundle-$id",
            title = title,
            description = null,
            dueDate = dueDate,
            dueTimeMinutes = dueTimeMinutes,
            priority = priority,
            category = null,
            status = AssignedTodoStatus.ACCEPTED,
            terminalReason = null,
            progressPercent = 0,
            sender = AssignedTodoUser(id = "friend-1", nickname = "monday"),
            receiver = AssignedTodoUser(id = "me", nickname = "tester"),
            assignmentMode = assignmentMode,
            reminder = reminder,
            checklist = emptyList()
        )
}
