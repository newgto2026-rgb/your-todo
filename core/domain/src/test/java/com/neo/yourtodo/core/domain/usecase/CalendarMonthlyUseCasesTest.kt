package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthlyUseCasesTest {

    @Test
    fun `observe monthly todos includes 28 29 30 31 month boundaries`() = runTest {
        val repository = FakeTodoRepository().apply {
            addTodo("feb-2025-start", LocalDate.of(2025, 2, 1), null)
            addTodo("feb-2025-end", LocalDate.of(2025, 2, 28), null)
            addTodo("feb-2024-start", LocalDate.of(2024, 2, 1), null)
            addTodo("feb-2024-end", LocalDate.of(2024, 2, 29), null)
            addTodo("apr-2026-start", LocalDate.of(2026, 4, 1), null)
            addTodo("apr-2026-end", LocalDate.of(2026, 4, 30), null)
            addTodo("may-2026-start", LocalDate.of(2026, 5, 1), null)
            addTodo("may-2026-end", LocalDate.of(2026, 5, 31), null)
            addTodo("outside-before", LocalDate.of(2026, 3, 31), null)
            addTodo("outside-after", LocalDate.of(2026, 6, 1), null)
            addTodo("without-date", null, null)
        }
        val useCase = ObserveMonthlyTodosUseCase(repository)

        assertThat(useCase(YearMonth.of(2025, 2)).first().map { it.title })
            .containsExactly("feb-2025-start", "feb-2025-end")
        assertThat(useCase(YearMonth.of(2024, 2)).first().map { it.title })
            .containsExactly("feb-2024-start", "feb-2024-end")
        assertThat(useCase(YearMonth.of(2026, 4)).first().map { it.title })
            .containsExactly("apr-2026-start", "apr-2026-end")
        assertThat(useCase(YearMonth.of(2026, 5)).first().map { it.title })
            .containsExactly("may-2026-start", "may-2026-end")
    }

    @Test
    fun `observe monthly todo summaries groups by date and computes indicator and overflow`() = runTest {
        val repository = FakeTodoRepository().apply {
            addTodo("d1-a", LocalDate.of(2026, 4, 10), null)
            addTodo("d1-b", LocalDate.of(2026, 4, 10), null)
            addTodo("d1-c", LocalDate.of(2026, 4, 10), null)
            addTodo("d1-d", LocalDate.of(2026, 4, 10), null)
            addTodo("d1-e", LocalDate.of(2026, 4, 10), null)
            addTodo("d2-a", LocalDate.of(2026, 4, 11), null)
            addTodo("other-month", LocalDate.of(2026, 5, 1), null)
            addTodo("without-date", null, null)
        }

        val summaries = ObserveMonthlyTodoSummariesUseCase(
            observeMonthlyTodosUseCase = ObserveMonthlyTodosUseCase(repository)
        )(yearMonth = YearMonth.of(2026, 4), maxIndicatorsPerDate = 3).first()

        assertThat(summaries.keys).containsExactly(
            LocalDate.of(2026, 4, 10),
            LocalDate.of(2026, 4, 11)
        )

        val firstDateSummary = summaries.getValue(LocalDate.of(2026, 4, 10))
        assertThat(firstDateSummary.todos).hasSize(5)
        assertThat(firstDateSummary.indicatorCount).isEqualTo(3)
        assertThat(firstDateSummary.overflowCount).isEqualTo(2)

        val secondDateSummary = summaries.getValue(LocalDate.of(2026, 4, 11))
        assertThat(secondDateSummary.todos).hasSize(1)
        assertThat(secondDateSummary.indicatorCount).isEqualTo(1)
        assertThat(secondDateSummary.overflowCount).isEqualTo(0)
    }

    @Test
    fun `observe monthly todo summaries returns empty map when no todo in month`() = runTest {
        val repository = FakeTodoRepository().apply {
            addTodo("other-month", LocalDate.of(2026, 5, 1), null)
            addTodo("without-date", null, null)
        }

        val summaries = ObserveMonthlyTodoSummariesUseCase(
            observeMonthlyTodosUseCase = ObserveMonthlyTodosUseCase(repository)
        )(yearMonth = YearMonth.of(2026, 4)).first()

        assertThat(summaries).isEmpty()
    }
}
