package com.neo.yourtodo.feature.calendar.widget

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

internal class CalendarMonthWidgetPresenter @Inject constructor(
    private val summarySource: CalendarMonthSummarySource,
    private val clock: Clock
) {
    suspend fun present(
        locale: Locale = Locale.getDefault(),
        displayedMonth: YearMonth? = null
    ): CalendarMonthWidgetState {
        val today = LocalDate.now(clock)
        val currentMonth = displayedMonth ?: YearMonth.from(today)
        val monthLabel = currentMonth.atDay(1)
            .format(DateTimeFormatter.ofPattern("yyyy MMMM", locale))

        return runCatching {
            val summaries = summarySource.summariesFor(currentMonth)
            CalendarMonthWidgetState(
                monthLabel = monthLabel,
                weekdayLabels = buildWeekdayLabels(locale),
                weeks = buildCalendarMonthWidgetDateGrid(
                    yearMonth = currentMonth,
                    locale = locale
                ).map { week ->
                    week.map { cell ->
                        val summary = summaries[cell.date]
                        CalendarMonthWidgetDay(
                            date = cell.date,
                            dayLabel = cell.date.dayOfMonth.toString(),
                            taskCountLabel = summary?.totalCount?.toTaskCountLabel(),
                            isCurrentMonth = cell.isCurrentMonth,
                            isToday = cell.date == today
                        )
                    }
                }
            )
        }.getOrElse {
            CalendarMonthWidgetState(
                monthLabel = monthLabel,
                weekdayLabels = buildWeekdayLabels(locale),
                weeks = emptyList(),
                isError = true
            )
        }
    }

    private fun buildWeekdayLabels(locale: Locale): List<String> {
        val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
        return List(WEEK_DAY_COUNT) { offset ->
            firstDayOfWeek
                .plus(offset.toLong())
                .getDisplayName(TextStyle.NARROW_STANDALONE, locale)
        }
    }

    private fun Int.toTaskCountLabel(): String? = when {
        this <= 0 -> null
        this > MAX_VISIBLE_TASK_COUNT -> "$MAX_VISIBLE_TASK_COUNT+"
        else -> toString()
    }

    private val com.neo.yourtodo.core.model.DateTodoSummary.totalCount: Int
        get() = indicatorCount + overflowCount

    private companion object {
        private const val WEEK_DAY_COUNT = 7
        private const val MAX_VISIBLE_TASK_COUNT = 9
    }
}
