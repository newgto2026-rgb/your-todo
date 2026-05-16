package com.neo.yourtodo.feature.calendar.widget

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

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
        val monthLabel = formatWidgetMonthLabel(currentMonth, locale)

        return runCatching {
            val summaries = summarySource.summariesFor(currentMonth)
            buildCalendarMonthWidgetState(
                monthLabel = monthLabel,
                currentMonth = currentMonth,
                today = today,
                locale = locale,
                summaries = summaries
            )
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            calendarMonthWidgetPresentationErrorState(
                monthLabel = monthLabel,
                locale = locale
            )
        }
    }
}
