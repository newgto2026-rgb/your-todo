package com.neo.yourtodo.core.domain.usecase

import app.cash.turbine.test
import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CategoryUseCasesTest {

    @Test
    fun `add category validates blank name`() = runTest {
        val repository = FakeTodoRepository()
        val useCase = AddCategoryUseCase(repository)

        val result = useCase("   ", null, null)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `add and update category use cases work`() = runTest {
        val repository = FakeTodoRepository()
        val addUseCase = AddCategoryUseCase(repository)
        val updateUseCase = UpdateCategoryUseCase(repository)
        val observeUseCase = ObserveCategoriesUseCase(repository)

        val categoryId = addUseCase("Work", "#112233", "briefcase").getOrNull()!!
        val updateResult = updateUseCase(categoryId, "Work Updated", "#445566", "building")

        assertThat(updateResult.isSuccess).isTrue()
        assertThat(observeUseCase().first().first().name).isEqualTo("Work Updated")
    }

    @Test
    fun `delete category and selected category filter use cases work`() = runTest {
        val repository = FakeTodoRepository()
        val addCategoryUseCase = AddCategoryUseCase(repository)
        val deleteCategoryUseCase = DeleteCategoryUseCase(repository)
        val updateSelectedCategoryFilterUseCase = UpdateSelectedCategoryFilterUseCase(repository)
        val observeSelectedCategoryFilterUseCase = ObserveSelectedCategoryFilterUseCase(repository)

        val categoryId = addCategoryUseCase("Personal", null, null).getOrNull()!!

        observeSelectedCategoryFilterUseCase().test {
            assertThat(awaitItem()).isNull()

            updateSelectedCategoryFilterUseCase(categoryId)
            assertThat(awaitItem()).isEqualTo(categoryId)

            deleteCategoryUseCase(categoryId)
            assertThat(awaitItem()).isNull()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
