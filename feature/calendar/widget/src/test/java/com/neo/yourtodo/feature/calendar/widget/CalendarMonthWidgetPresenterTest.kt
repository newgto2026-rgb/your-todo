package com.neo.yourtodo.feature.calendar.widget

import com.neo.yourtodo.core.model.DateTodoSummary
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
}
