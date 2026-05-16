package com.neo.yourtodo.feature.calendar.widget

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.GregorianCalendar
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

        assertThat(state.monthLabel).isEqualTo("May 2026")
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
    fun calendarMonthWidgetPresentationErrorState_keepsMonthAndWeekdays() {
        val state = calendarMonthWidgetPresentationErrorState(
            monthLabel = "May 2026",
            locale = Locale.US
        )

        assertThat(state.monthLabel).isEqualTo("May 2026")
        assertThat(state.weekdayLabels).hasSize(7)
        assertThat(state.weeks).isEmpty()
        assertThat(state.isError).isTrue()
    }

    @Test
    fun formatWidgetMonthLabel_respectsLocaleMonthYearOrder() {
        val yearMonth = YearMonth.of(2026, 5)

        val usLabel = formatWidgetMonthLabel(yearMonth, Locale.US)
        val koreanLabel = formatWidgetMonthLabel(yearMonth, Locale.KOREA)

        assertThat(usLabel).isEqualTo("May 2026")
        assertThat(koreanLabel.indexOf("2026")).isLessThan(koreanLabel.indexOf("5"))
        assertThat(koreanLabel).doesNotContain("1")
    }

    @Test
    fun toWidgetMonthYearPattern_preservesQuotedLocaleLiteral() {
        val monthYearPattern = "d MMMM y 'г'.".toWidgetMonthYearPattern()

        val label = formatPattern(
            pattern = monthYearPattern,
            locale = Locale.forLanguageTag("ru-RU")
        )

        assertThat(label).contains("2026")
        assertThat(label).contains("г.")
        assertThat(label).doesNotContain("'")
    }

    @Test
    fun toWidgetMonthYearPattern_removesDelimiterBeforeTrailingDayField() {
        val monthYearPattern = "y-MMMM-dd".toWidgetMonthYearPattern()

        val label = formatPattern(
            pattern = monthYearPattern,
            locale = Locale.US
        )

        assertThat(label).isEqualTo("2026-May")
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

    private fun formatPattern(
        pattern: String,
        locale: Locale
    ): String {
        val date = GregorianCalendar(locale).apply {
            clear()
            set(2026, 4, 1)
        }.time
        return SimpleDateFormat(pattern, locale).format(date)
    }
}
