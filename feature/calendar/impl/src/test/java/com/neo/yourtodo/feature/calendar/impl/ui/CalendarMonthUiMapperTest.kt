package com.neo.yourtodo.feature.calendar.impl.ui

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

class CalendarMonthUiMapperTest {

    @Test
    fun buildCalendarUiState_normalizesSelectedDateAndMapsTodayCount() {
        val targetMonth = YearMonth.of(2026, 2)
        val today = LocalDate.of(2026, 2, 9)
        val summaries = mapOf(
            today to DateTodoSummary(
                date = today,
                todos = emptyList(),
                indicatorCount = 3,
                overflowCount = 2
            )
        )

        val state = buildCalendarUiState(
            profileInitial = "tester",
            currentMonth = targetMonth,
            selectedDate = LocalDate.of(2026, 1, 31),
            summariesByDate = summaries,
            selectedDateTodos = emptyList(),
            today = today
        )

        assertThat(state.profileInitial).isEqualTo("tester")
        assertThat(state.selectedDate).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(state.todayTaskCount).isEqualTo(5)
        assertThat(state.days.single { it.date == today }.isToday).isTrue()
        assertThat(state.days.single { it.date == LocalDate.of(2026, 2, 28) }.isSelected).isTrue()
        assertThat(state.selectedWeekDays).hasSize(7)
        assertThat(state.selectedWeekDays.map { it.date }).contains(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun buildCalendarUiState_sectionsSelectedDateTodosBySource() {
        val selectedDate = LocalDate.of(2026, 5, 9)

        val state = buildCalendarUiState(
            profileInitial = null,
            currentMonth = YearMonth.of(2026, 5),
            selectedDate = selectedDate,
            summariesByDate = emptyMap(),
            selectedDateTodos = listOf(
                selectedTodo(id = 1, title = "Mine"),
                selectedTodo(id = -2, title = "Friend 1", source = CalendarTodoSource.FRIEND),
                selectedTodo(id = -3, title = "Friend 2", source = CalendarTodoSource.FRIEND),
                selectedTodo(id = -4, title = "Friend 3", source = CalendarTodoSource.FRIEND),
                selectedTodo(id = -5, title = "Friend 4", source = CalendarTodoSource.FRIEND)
            ),
            isFriendTodosExpanded = false,
            today = selectedDate
        )

        assertThat(state.selectedDateTodoSections.map { it.source })
            .containsExactly(CalendarTodoSource.MINE, CalendarTodoSource.FRIEND)
            .inOrder()
        assertThat(state.selectedDateTodoSections[0].visibleTodos.map { it.title })
            .containsExactly("Mine")
        assertThat(state.selectedDateTodoSections[1].totalCount).isEqualTo(4)
        assertThat(state.selectedDateTodoSections[1].isCollapsible).isTrue()
        assertThat(state.selectedDateTodoSections[1].isExpanded).isFalse()
        assertThat(state.selectedDateTodoSections[1].visibleTodos.map { it.title })
            .containsExactly("Friend 1", "Friend 2", "Friend 3")
            .inOrder()
    }

    @Test
    fun buildCalendarUiState_hidesFriendSectionWhenEmptyAndExpandsWhenRequested() {
        val selectedDate = LocalDate.of(2026, 5, 9)

        val withoutFriendTodos = buildCalendarUiState(
            profileInitial = null,
            currentMonth = YearMonth.of(2026, 5),
            selectedDate = selectedDate,
            summariesByDate = emptyMap(),
            selectedDateTodos = listOf(selectedTodo(id = 1, title = "Mine")),
            today = selectedDate
        )

        val expandedFriendTodos = buildCalendarUiState(
            profileInitial = null,
            currentMonth = YearMonth.of(2026, 5),
            selectedDate = selectedDate,
            summariesByDate = emptyMap(),
            selectedDateTodos = listOf(
                selectedTodo(id = -1, title = "Friend 1", source = CalendarTodoSource.FRIEND),
                selectedTodo(id = -2, title = "Friend 2", source = CalendarTodoSource.FRIEND),
                selectedTodo(id = -3, title = "Friend 3", source = CalendarTodoSource.FRIEND),
                selectedTodo(id = -4, title = "Friend 4", source = CalendarTodoSource.FRIEND),
                selectedTodo(id = -5, title = "Friend 5", source = CalendarTodoSource.FRIEND),
                selectedTodo(id = -6, title = "Friend 6", source = CalendarTodoSource.FRIEND)
            ),
            isFriendTodosExpanded = true,
            today = selectedDate
        )

        assertThat(withoutFriendTodos.selectedDateTodoSections.map { it.source })
            .containsExactly(CalendarTodoSource.MINE)
        assertThat(expandedFriendTodos.selectedDateTodoSections.single().visibleTodos)
            .hasSize(6)
        assertThat(expandedFriendTodos.selectedDateTodoSections.single().isExpanded).isTrue()
    }

    @Test
    fun buildMonthCells_onlyShowsIndicatorsForCurrentMonthCells() {
        val yearMonth = YearMonth.of(2026, 5)
        val inMonthDate = LocalDate.of(2026, 5, 7)
        val adjacentDate = LocalDate.of(2026, 4, 30)
        val summaries = mapOf(
            inMonthDate to summary(inMonthDate, indicatorCount = 2, overflowCount = 1),
            adjacentDate to summary(adjacentDate, indicatorCount = 3, overflowCount = 4)
        )

        val cells = buildMonthCells(
            yearMonth = yearMonth,
            selectedDate = inMonthDate,
            today = inMonthDate,
            summariesByDate = summaries
        )

        val inMonthCell = cells.single { it.date == inMonthDate }
        val adjacentCell = cells.single { it.date == adjacentDate }

        assertThat(inMonthCell.isCurrentMonth).isTrue()
        assertThat(inMonthCell.indicatorCount).isEqualTo(2)
        assertThat(inMonthCell.overflowCount).isEqualTo(1)
        assertThat(adjacentCell.isCurrentMonth).isFalse()
        assertThat(adjacentCell.indicatorCount).isEqualTo(0)
        assertThat(adjacentCell.overflowCount).isEqualTo(0)
    }

    private fun summary(
        date: LocalDate,
        indicatorCount: Int,
        overflowCount: Int
    ): DateTodoSummary =
        DateTodoSummary(
            date = date,
            todos = listOf(
                TodoSummary(
                    id = date.dayOfMonth.toLong(),
                    title = "Todo",
                    isDone = false
                )
            ),
            indicatorCount = indicatorCount,
            overflowCount = overflowCount
        )

    private fun selectedTodo(
        id: Long,
        title: String,
        assignedTodoId: String? = null,
        source: CalendarTodoSource = CalendarTodoSource.MINE
    ): CalendarSelectedTodoUiModel =
        CalendarSelectedTodoUiModel(
            id = id,
            title = title,
            isDone = false,
            priority = TodoPriority.MEDIUM,
            isReminderEnabled = false,
            dueTimeLabel = null,
            reminderLeadMinutes = null,
            source = source,
            assignedTodoId = assignedTodoId
        )
}
