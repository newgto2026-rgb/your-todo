package com.neo.yourtodo.feature.calendar.widget

import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoSummary
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CalendarMonthWidgetPresenterTest {

    @Test
    fun present_mapsMonthlySummariesToWidgetDays() = runTest {
        val targetDate = LocalDate.of(2026, 5, 7)
        val presenter = CalendarMonthWidgetPresenter(
            summarySource = FakeCalendarMonthSummarySource(
                summaries = mapOf(
                    targetDate to DateTodoSummary(
                        date = targetDate,
                        todos = emptyList(),
                        indicatorCount = 2,
                        overflowCount = 1
                    )
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(state.monthLabel).isEqualTo("2026 May")
        assertThat(day.isToday).isTrue()
        assertThat(day.isCurrentMonth).isTrue()
        assertThat(day.taskCountLabel).isEqualTo("3")
    }

    @Test
    fun present_capsLargeTaskCounts() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = CalendarMonthWidgetPresenter(
            summarySource = FakeCalendarMonthSummarySource(
                summaries = mapOf(
                    targetDate to DateTodoSummary(
                        date = targetDate,
                        todos = emptyList(),
                        indicatorCount = 9,
                        overflowCount = 4
                    )
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.taskCountLabel).isEqualTo("9+")
    }

    @Test
    fun present_mapsUpToFourTodosToExpandedPreviewChips() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = CalendarMonthWidgetPresenter(
            summarySource = FakeCalendarMonthSummarySource(
                summaries = mapOf(
                    targetDate to DateTodoSummary(
                        date = targetDate,
                        todos = listOf(
                            todo(title = "Morning review", createdAt = 1L),
                            todo(title = "Project update", createdAt = 2L),
                            todo(title = "Dinner", createdAt = 3L),
                            todo(title = "Read notes", createdAt = 4L)
                        ),
                        indicatorCount = 4,
                        overflowCount = 0
                    )
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.todoChips.map { it.label })
            .containsExactly("Read notes", "Dinner", "Project update", "Morning review")
            .inOrder()
        assertThat(day.todoChips).hasSize(4)
        assertThat(day.todoChips.none { it.isOverflow }).isTrue()
    }

    @Test
    fun present_mapsFiveOrMoreTodosToThreePreviewChipsAndOverflow() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = CalendarMonthWidgetPresenter(
            summarySource = FakeCalendarMonthSummarySource(
                summaries = mapOf(
                    targetDate to DateTodoSummary(
                        date = targetDate,
                        todos = listOf(
                            todo(title = "A", createdAt = 1L),
                            todo(title = "B", createdAt = 2L),
                            todo(title = "C", createdAt = 3L),
                            todo(title = "D", createdAt = 4L),
                            todo(title = "E", createdAt = 5L)
                        ),
                        indicatorCount = 4,
                        overflowCount = 1
                    )
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.todoChips.map { it.label })
            .containsExactly("E", "D", "C", "+2")
            .inOrder()
        assertThat(day.todoChips.last().isOverflow).isTrue()
    }

    @Test
    fun present_ordersPreviewChipsByDoneTimePriorityAndCreatedAt() = runTest {
        val targetDate = LocalDate.of(2026, 5, 8)
        val presenter = CalendarMonthWidgetPresenter(
            summarySource = FakeCalendarMonthSummarySource(
                summaries = mapOf(
                    targetDate to DateTodoSummary(
                        date = targetDate,
                        todos = listOf(
                            todo(title = "Done early", isDone = true, dueTimeMinutes = 540, priority = TodoPriority.HIGH),
                            todo(title = "No time high", dueTimeMinutes = null, priority = TodoPriority.HIGH),
                            todo(title = "Early low", dueTimeMinutes = 540, priority = TodoPriority.LOW),
                            todo(title = "Early high", dueTimeMinutes = 540, priority = TodoPriority.HIGH),
                            todo(title = "Later high", dueTimeMinutes = 600, priority = TodoPriority.HIGH)
                        ),
                        indicatorCount = 4,
                        overflowCount = 1
                    )
                )
            ),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(day.todoChips.map { it.label })
            .containsExactly("Early high", "Early low", "Later high", "+2")
            .inOrder()
    }

    @Test
    fun present_usesDisplayedMonthWhenProvided() = runTest {
        val displayedMonth = YearMonth.of(2026, 7)
        val targetDate = LocalDate.of(2026, 7, 11)
        val summarySource = FakeCalendarMonthSummarySource(
            summaries = mapOf(
                targetDate to DateTodoSummary(
                    date = targetDate,
                    todos = emptyList(),
                    indicatorCount = 1,
                    overflowCount = 1
                )
            )
        )
        val presenter = CalendarMonthWidgetPresenter(
            summarySource = summarySource,
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(locale = Locale.US, displayedMonth = displayedMonth)
        val day = state.weeks.flatten().single { it.date == targetDate }

        assertThat(summarySource.requestedMonths).containsExactly(displayedMonth)
        assertThat(state.monthLabel).isEqualTo("2026 July")
        assertThat(day.isToday).isFalse()
        assertThat(day.isCurrentMonth).isTrue()
        assertThat(day.taskCountLabel).isEqualTo("2")
    }

    @Test
    fun present_returnsErrorStateWhenSourceFails() = runTest {
        val presenter = CalendarMonthWidgetPresenter(
            summarySource = FakeCalendarMonthSummarySource(error = IllegalStateException("boom")),
            clock = fixedClock("2026-05-07T00:00:00Z")
        )

        val state = presenter.present(Locale.US)

        assertThat(state.isError).isTrue()
        assertThat(state.weeks).isEmpty()
    }

    private class FakeCalendarMonthSummarySource(
        private val summaries: Map<LocalDate, DateTodoSummary> = emptyMap(),
        private val error: Throwable? = null
    ) : CalendarMonthSummarySource {
        val requestedMonths: MutableList<YearMonth> = mutableListOf()

        override suspend fun summariesFor(yearMonth: YearMonth): Map<LocalDate, DateTodoSummary> {
            requestedMonths += yearMonth
            error?.let { throw it }
            return summaries
        }
    }

    private fun fixedClock(instant: String): Clock =
        Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"))

    private fun todo(
        title: String,
        isDone: Boolean = false,
        dueTimeMinutes: Int? = null,
        priority: TodoPriority = TodoPriority.MEDIUM,
        createdAt: Long = 0L
    ): TodoSummary =
        TodoSummary(
            id = createdAt,
            title = title,
            isDone = isDone,
            dueTimeMinutes = dueTimeMinutes,
            priority = priority,
            createdAt = createdAt
        )
}
