package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import javax.inject.Inject

class GetAssignedTodosUseCase @Inject constructor(
    private val repository: AssignmentRepository
) {
    suspend fun received(status: AssignmentFeedStatus) = repository.getReceivedAssignedTodos(status)

    suspend fun sent(status: AssignmentFeedStatus) = repository.getSentAssignedTodos(status)

    suspend fun visibleReceived(): Result<List<AssignedTodo>> {
        val active = repository.getReceivedAssignedTodos(AssignmentFeedStatus.ACTIVE)
        val history = repository.getReceivedAssignedTodos(AssignmentFeedStatus.HISTORY)
        return mergeVisibleAssignedTodos(listOf(active, history))
    }

    suspend fun byFriend(
        friendUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus
    ) = repository.getFriendAssignedTodos(friendUserId, direction, status)

    suspend fun visibleByFriend(
        friendUserId: String,
        direction: AssignmentDirection
    ): Result<List<AssignedTodo>> {
        val pending = repository.getFriendAssignedTodos(
            friendUserId = friendUserId,
            direction = direction,
            status = AssignmentFeedStatus.PENDING
        )
        val active = repository.getFriendAssignedTodos(
            friendUserId = friendUserId,
            direction = direction,
            status = AssignmentFeedStatus.ACTIVE
        )
        val history = repository.getFriendAssignedTodos(
            friendUserId = friendUserId,
            direction = direction,
            status = AssignmentFeedStatus.HISTORY
        )
        return mergeVisibleFriendDetailAssignedTodos(listOf(pending, active, history))
    }

    suspend fun completedHistoryByFriend(
        friendUserId: String,
        direction: AssignmentDirection
    ): Result<List<AssignedTodo>> {
        val history = repository.getFriendAssignedTodos(
            friendUserId = friendUserId,
            direction = direction,
            status = AssignmentFeedStatus.HISTORY
        )
        val failure = history.exceptionOrNull()
        if (failure != null) return Result.failure(failure)
        return Result.success(completedFriendDetailHistoryAssignedTodos(history.getOrThrow()))
    }

    private fun mergeVisibleAssignedTodos(
        results: List<Result<List<AssignedTodo>>>
    ): Result<List<AssignedTodo>> {
        val failure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
        if (failure != null) return Result.failure(failure)
        return Result.success(
            visibleTaskSurfaceAssignedTodos(*results.map { it.getOrThrow() }.toTypedArray())
        )
    }

    private fun mergeVisibleFriendDetailAssignedTodos(
        results: List<Result<List<AssignedTodo>>>
    ): Result<List<AssignedTodo>> {
        val failure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
        if (failure != null) return Result.failure(failure)
        return Result.success(
            visibleFriendDetailAssignedTodos(*results.map { it.getOrThrow() }.toTypedArray())
        )
    }
}
