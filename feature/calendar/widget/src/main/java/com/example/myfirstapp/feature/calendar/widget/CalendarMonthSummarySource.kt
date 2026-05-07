package com.example.myfirstapp.feature.calendar.widget

import com.example.myfirstapp.core.domain.usecase.ObserveMonthlyTodoSummariesUseCase
import com.example.myfirstapp.core.model.DateTodoSummary
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.first

internal interface CalendarMonthSummarySource {
    suspend fun summariesFor(yearMonth: YearMonth): Map<LocalDate, DateTodoSummary>
}

internal class DomainCalendarMonthSummarySource @Inject constructor(
    private val observeMonthlyTodoSummariesUseCase: ObserveMonthlyTodoSummariesUseCase
) : CalendarMonthSummarySource {
    override suspend fun summariesFor(yearMonth: YearMonth): Map<LocalDate, DateTodoSummary> =
        observeMonthlyTodoSummariesUseCase(yearMonth).first()
}
