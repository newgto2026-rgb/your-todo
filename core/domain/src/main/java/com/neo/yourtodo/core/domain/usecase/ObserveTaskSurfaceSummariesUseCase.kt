package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.model.DateTodoSummary
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveTaskSurfaceSummariesUseCase @Inject constructor(
    private val observeMonthlyTodoSummariesUseCase: ObserveMonthlyTodoSummariesUseCase,
    private val getAssignedTodosUseCase: GetAssignedTodosUseCase
) {
    operator fun invoke(
        yearMonth: YearMonth,
        maxIndicatorsPerDate: Int = DEFAULT_TASK_SURFACE_MAX_INDICATORS
    ): Flow<Map<LocalDate, DateTodoSummary>> =
        invoke(
            yearMonth = yearMonth,
            assignedTodos = getAssignedTodosUseCase.observeVisibleReceived(),
            maxIndicatorsPerDate = maxIndicatorsPerDate
        )

    operator fun invoke(
        yearMonth: YearMonth,
        assignedTodos: Flow<List<AssignedTodo>>,
        maxIndicatorsPerDate: Int = DEFAULT_TASK_SURFACE_MAX_INDICATORS
    ): Flow<Map<LocalDate, DateTodoSummary>> =
        combine(
            observeMonthlyTodoSummariesUseCase(
                yearMonth = yearMonth,
                maxIndicatorsPerDate = maxIndicatorsPerDate
            ),
            assignedTodos
        ) { localSummaries, visibleAssignedTodos ->
            merge(
                yearMonth = yearMonth,
                localSummaries = localSummaries,
                assignedTodos = visibleAssignedTodos,
                maxIndicatorsPerDate = maxIndicatorsPerDate
            )
        }

    fun merge(
        yearMonth: YearMonth,
        localSummaries: Map<LocalDate, DateTodoSummary>,
        assignedTodos: List<AssignedTodo>,
        maxIndicatorsPerDate: Int = DEFAULT_TASK_SURFACE_MAX_INDICATORS
    ): Map<LocalDate, DateTodoSummary> =
        mergeTaskSurfaceSummaries(
            yearMonth = yearMonth,
            localSummaries = localSummaries,
            assignedTodos = assignedTodos,
            maxIndicatorsPerDate = maxIndicatorsPerDate
        )
}

fun mergeTaskSurfaceSummaries(
    yearMonth: YearMonth,
    localSummaries: Map<LocalDate, DateTodoSummary>,
    assignedTodos: List<AssignedTodo>,
    maxIndicatorsPerDate: Int = DEFAULT_TASK_SURFACE_MAX_INDICATORS
): Map<LocalDate, DateTodoSummary> {
    val safeMaxIndicators = maxIndicatorsPerDate.coerceAtLeast(0)
    val mutable = localSummaries.toMutableMap()
    assignedTodos
        .filter { it.dueDate != null && YearMonth.from(it.dueDate) == yearMonth }
        .groupBy { checkNotNull(it.dueDate) }
        .forEach { (date, dateAssignedTodos) ->
            val existing = mutable[date]
            val assignedSummaries = dateAssignedTodos.map { it.toTaskSurfaceSummary() }
            val todos = existing?.todos.orEmpty() + assignedSummaries
            val indicatorCount = min(todos.size, safeMaxIndicators)
            mutable[date] = DateTodoSummary(
                date = date,
                todos = todos,
                indicatorCount = indicatorCount,
                overflowCount = max(todos.size - indicatorCount, 0)
            )
        }
    return mutable
}

const val DEFAULT_TASK_SURFACE_MAX_INDICATORS = 3
