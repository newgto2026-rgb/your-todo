package com.neo.yourtodo.feature.calendar.widget

import com.neo.yourtodo.core.domain.usecase.GetAssignedTodosUseCase
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first

internal class CalendarMonthWidgetPresenter @Inject constructor(
    private val summarySource: CalendarMonthSummarySource,
    private val getAssignedTodosUseCase: GetAssignedTodosUseCase,
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
                .withWidgetAssignedTodos(
                    yearMonth = currentMonth,
                    assignedTodos = getAssignedTodosUseCase.observeVisibleReceived().first()
                )
            buildCalendarMonthWidgetState(
                monthLabel = monthLabel,
                currentMonth = currentMonth,
                today = today,
                locale = locale,
                summaries = summaries
            )
        }.getOrElse {
            calendarMonthWidgetPresentationErrorState(
                monthLabel = monthLabel,
                locale = locale
            )
        }
    }
}
