package com.neo.yourtodo.feature.calendar.widget

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodoStatus
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Test

class CalendarMonthWidgetPresenterMappersTest {

    @Test
    fun buildCalendarMonthWidgetState_mapsDateGridSummariesAndAdjacentDays() {
        val today = LocalDate.of(2026, 5, 7)
        val adjacentDate = LocalDate.of(2026, 4, 26)
        val summaries = mapOf(
            today to DateTodoSummary(
                date = today,
                todos = listOf(todo(id = 1, title = "Today todo")),
                indicatorCount = 1,
                overflowCount = 0
            ),
            adjacentDate to DateTodoSummary(
                date = adjacentDate,
                todos = listOf(todo(id = 2, title = "Adjacent todo")),
                indicatorCount = 1,
                overflowCount = 0
            )
        )

        val state = buildCalendarMonthWidgetState(
            monthLabel = formatWidgetMonthLabel(YearMonth.of(2026, 5), Locale.US),
            currentMonth = YearMonth.of(2026, 5),
            today = today,
            locale = Locale.US,
            summaries = summaries
        )

        val todayDay = state.weeks.flatten().single { it.date == today }
        val adjacentDay = state.weeks.flatten().single { it.date == adjacentDate }

        assertThat(state.monthLabel).isEqualTo("2026 May")
        assertThat(state.weekdayLabels).hasSize(7)
        assertThat(state.weeks).hasSize(6)
        assertThat(todayDay.isToday).isTrue()
        assertThat(todayDay.isCurrentMonth).isTrue()
        assertThat(todayDay.taskCountLabel).isEqualTo("1")
        assertThat(todayDay.todoChips.map { it.label }).containsExactly("Today todo")
        assertThat(adjacentDay.isCurrentMonth).isFalse()
        assertThat(adjacentDay.taskCountLabel).isEqualTo("1")
    }

    @Test
    fun withWidgetAssignedTodos_mergesAssignedTodosWithExistingSummaryAndOverflow() {
        val date = LocalDate.of(2026, 5, 8)
        val outOfMonthDate = LocalDate.of(2026, 6, 1)
        val summaries = mapOf(
            date to DateTodoSummary(
                date = date,
                todos = listOf(todo(id = 1, title = "Local")),
                indicatorCount = 1,
                overflowCount = 0
            )
        )

        val merged = summaries.withWidgetAssignedTodos(
            yearMonth = YearMonth.of(2026, 5),
            assignedTodos = listOf(
                assignedTodo(id = "assigned-1", title = "Assigned 1", dueDate = date),
                assignedTodo(id = "assigned-2", title = "Assigned 2", dueDate = date),
                assignedTodo(id = "assigned-3", title = "Assigned 3", dueDate = date),
                assignedTodo(id = "assigned-out", title = "Assigned out", dueDate = outOfMonthDate)
            )
        )

        val summary = merged.getValue(date)
        assertThat(summary.todos.map { it.title })
            .containsExactly("Local", "Assigned 1", "Assigned 2", "Assigned 3")
            .inOrder()
        assertThat(summary.indicatorCount).isEqualTo(3)
        assertThat(summary.overflowCount).isEqualTo(1)
        val assignedCreatedAt = Instant.parse("2026-05-07T00:00:00Z").toEpochMilli()
        assertThat(summary.todos.drop(1).map { it.createdAt })
            .containsExactly(assignedCreatedAt, assignedCreatedAt, assignedCreatedAt)
            .inOrder()
        assertThat(merged).doesNotContainKey(outOfMonthDate)
    }

    @Test
    fun calendarMonthWidgetPresentationErrorState_keepsMonthAndWeekdays() {
        val state = calendarMonthWidgetPresentationErrorState(
            monthLabel = "2026 May",
            locale = Locale.US
        )

        assertThat(state.monthLabel).isEqualTo("2026 May")
        assertThat(state.weekdayLabels).hasSize(7)
        assertThat(state.weeks).isEmpty()
        assertThat(state.isError).isTrue()
    }

    private fun todo(
        id: Long,
        title: String,
        isDone: Boolean = false,
        priority: TodoPriority = TodoPriority.MEDIUM,
        createdAt: Long = 0L
    ): TodoSummary =
        TodoSummary(
            id = id,
            title = title,
            isDone = isDone,
            priority = priority,
            createdAt = createdAt
        )

    private fun assignedTodo(
        id: String,
        title: String,
        dueDate: LocalDate,
        status: AssignedTodoStatus = AssignedTodoStatus.ACCEPTED,
        assignmentMode: AssignmentMode = AssignmentMode.REQUEST
    ): AssignedTodo =
        AssignedTodo(
            id = id,
            bundleId = "bundle-$id",
            title = title,
            description = null,
            dueDate = dueDate,
            dueTimeMinutes = null,
            priority = TodoPriority.MEDIUM,
            category = null,
            status = status,
            terminalReason = null,
            progressPercent = if (status == AssignedTodoStatus.DONE) 100 else 0,
            sender = null,
            receiver = null,
            assignmentMode = assignmentMode,
            reminder = null,
            createdAt = Instant.parse("2026-05-07T00:00:00Z"),
            completedAt = null
        )
}
