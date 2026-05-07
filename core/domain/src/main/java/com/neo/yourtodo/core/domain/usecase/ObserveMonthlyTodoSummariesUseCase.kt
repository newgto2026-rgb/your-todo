package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.mapper.toSummary
import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.TodoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class ObserveMonthlyTodoSummariesUseCase @Inject constructor(
    private val observeMonthlyTodosUseCase: ObserveMonthlyTodosUseCase
) {
    operator fun invoke(
        yearMonth: YearMonth,
        maxIndicatorsPerDate: Int = DEFAULT_MAX_INDICATORS
    ): Flow<Map<LocalDate, DateTodoSummary>> {
        val safeMaxIndicators = maxIndicatorsPerDate.coerceAtLeast(0)
        return observeMonthlyTodosUseCase(yearMonth).map { monthlyTodos ->
            monthlyTodos
                .groupByDate()
                .toSortedMap()
                .mapValues { (date, todos) ->
                    val summaries = todos.map { it.toSummary() }
                    val indicatorCount = min(summaries.size, safeMaxIndicators)
                    DateTodoSummary(
                        date = date,
                        todos = summaries,
                        indicatorCount = indicatorCount,
                        overflowCount = max(summaries.size - indicatorCount, 0)
                    )
                }
        }
    }

    private fun List<TodoItem>.groupByDate(): Map<LocalDate, List<TodoItem>> =
        this
            .asSequence()
            .filter { it.dueDate != null }
            .groupBy { it.dueDate!! }

    private companion object {
        private const val DEFAULT_MAX_INDICATORS = 3
    }
}
