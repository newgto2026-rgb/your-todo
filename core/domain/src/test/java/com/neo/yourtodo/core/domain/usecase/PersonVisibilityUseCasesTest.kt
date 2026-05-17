package com.neo.yourtodo.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.personvisibility.ObservedPersonTodos
import com.neo.yourtodo.core.model.personvisibility.ObservedTodo
import com.neo.yourtodo.core.model.personvisibility.PersonVisibilityGrant
import com.neo.yourtodo.core.model.personvisibility.PersonVisibilityGrantState
import com.neo.yourtodo.core.testing.repository.FakePersonVisibilityRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class PersonVisibilityUseCasesTest {

    @Test
    fun observeVisibilityGrantsDelegatesToRepository() = runTest {
        val grant = testGrant()
        val repository = FakePersonVisibilityRepository()
        val useCase = ObserveVisibilityGrantsUseCase(repository)

        useCase().test {
            assertThat(awaitItem()).isEmpty()

            repository.setVisibilityGrants(listOf(grant))

            assertThat(awaitItem()).containsExactly(grant)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setVisibilityGrantDelegatesFriendUserIdToRepository() = runTest {
        val grant = testGrant(observerUserId = "friend-id")
        val repository = FakePersonVisibilityRepository().apply {
            setVisibilityGrantResult = Result.success(grant)
        }
        val useCase = SetVisibilityGrantUseCase(repository)

        val result = useCase("friend-id")

        assertThat(repository.setVisibilityGrantFriendUserIds).containsExactly("friend-id")
        assertThat(result.getOrNull()).isEqualTo(grant)
    }

    @Test
    fun revokeVisibilityGrantDelegatesFriendUserIdToRepository() = runTest {
        val repository = FakePersonVisibilityRepository().apply {
            revokeVisibilityGrantResult = Result.success(Unit)
        }
        val useCase = RevokeVisibilityGrantUseCase(repository)

        val result = useCase("friend-id")

        assertThat(repository.revokedVisibilityGrantFriendUserIds).containsExactly("friend-id")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun observeObservedTodosProvidesTodosGroupedByOwner() = runTest {
        val friendTodos = ObservedPersonTodos(
            ownerUserId = "friend-id",
            todos = listOf(testObservedTodo(ownerUserId = "friend-id"))
        )
        val repository = FakePersonVisibilityRepository()
        val useCase = ObserveObservedTodosUseCase(repository)

        useCase().test {
            assertThat(awaitItem()).isEmpty()

            repository.setObservedTodos(listOf(friendTodos))

            assertThat(awaitItem()).containsExactly(friendTodos)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refreshPersonVisibilityRefreshesGrantsAndObservedTodos() = runTest {
        val repository = FakePersonVisibilityRepository()
        val useCase = RefreshPersonVisibilityUseCase(repository)
        val windowStart = LocalDate.of(2026, 5, 1)
        val windowEnd = LocalDate.of(2026, 5, 31)

        val result = useCase(windowStart, windowEnd)

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.refreshVisibilityGrantCount).isEqualTo(1)
        assertThat(repository.syncObservedTodoWindows).containsExactly(windowStart to windowEnd)
    }

    @Test
    fun refreshPersonVisibilityReturnsFirstFailure() = runTest {
        val failure = IllegalStateException("sync failed")
        val repository = FakePersonVisibilityRepository().apply {
            syncObservedTodosResult = Result.failure(failure)
        }
        val useCase = RefreshPersonVisibilityUseCase(repository)

        val result = useCase()

        assertThat(result.exceptionOrNull()).isEqualTo(failure)
    }

    private fun testGrant(
        id: String = "grant-id",
        ownerUserId: String = "me",
        observerUserId: String = "friend-id"
    ): PersonVisibilityGrant =
        PersonVisibilityGrant(
            id = id,
            ownerUserId = ownerUserId,
            observerUserId = observerUserId,
            state = PersonVisibilityGrantState.ACTIVE,
            createdAt = "2026-05-17T00:00:00.000Z",
            updatedAt = "2026-05-17T00:00:00.000Z",
            revokedAt = null
        )

    private fun testObservedTodo(ownerUserId: String): ObservedTodo =
        ObservedTodo(
            id = "todo-id",
            ownerUserId = ownerUserId,
            title = "Shared todo",
            isDone = false,
            dueDate = LocalDate.of(2026, 5, 17),
            dueTimeMinutes = 9 * 60,
            priority = TodoPriority.MEDIUM,
            categoryName = "Work",
            updatedAt = "2026-05-17T00:00:00.000Z"
        )
}
