package com.neo.yourtodo.feature.calendar.impl.ui

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.model.DateTodoSummary
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
}
