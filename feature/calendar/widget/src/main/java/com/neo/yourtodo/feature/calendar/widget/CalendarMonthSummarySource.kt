package com.neo.yourtodo.feature.calendar.widget

import com.neo.yourtodo.core.domain.usecase.ObserveTaskSurfaceSummariesUseCase
import com.neo.yourtodo.core.model.DateTodoSummary
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.first

internal interface CalendarMonthSummarySource {
    suspend fun summariesFor(yearMonth: YearMonth): Map<LocalDate, DateTodoSummary>
}

internal class DomainCalendarMonthSummarySource @Inject constructor(
    private val observeTaskSurfaceSummariesUseCase: ObserveTaskSurfaceSummariesUseCase
) : CalendarMonthSummarySource {
    override suspend fun summariesFor(yearMonth: YearMonth): Map<LocalDate, DateTodoSummary> =
        observeTaskSurfaceSummariesUseCase(yearMonth).first()
}
