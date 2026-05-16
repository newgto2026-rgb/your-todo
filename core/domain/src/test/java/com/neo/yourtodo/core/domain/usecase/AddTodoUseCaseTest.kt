package com.neo.yourtodo.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.error.AppError
import com.neo.yourtodo.core.domain.error.appErrorOrNull
import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AddTodoUseCaseTest {

    private val repository = FakeTodoRepository()
    private val useCase = AddTodoUseCase(repository)

    @Test
    fun `blank title returns failure`() = runTest {
        val result = useCase("   ", null, null)

        assertThat(result.isFailure).isTrue()
        assertThat(result.appErrorOrNull()).isEqualTo(AppError.ValidationError("Title must not be blank"))
    }

    @Test
    fun `valid title returns created id`() = runTest {
        val result = useCase("todo", null, null)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(1L)
    }

    @Test
    fun `repository missing local data failure maps to app error`() = runTest {
        val result = useCase("todo", null, 999L)

        assertThat(result.isFailure).isTrue()
        assertThat(result.appErrorOrNull()).isEqualTo(AppError.LocalDataMissing("Category not found"))
    }
}
