package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.testing.repository.FakeTodoRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AddTodoUseCaseTest {

    private val repository = FakeTodoRepository()
    private val useCase = AddTodoUseCase(repository)

    @Test
    fun `blank title returns failure`() = runTest {
        val result = useCase("   ", null, null)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `valid title returns created id`() = runTest {
        val result = useCase("todo", null, null)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(1L)
    }
}
