package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import javax.inject.Inject

class GetAssignedTodosUseCase @Inject constructor(
    private val repository: AssignmentRepository
) {
    suspend fun received(status: AssignmentFeedStatus) = repository.getReceivedAssignedTodos(status)

    suspend fun sent(status: AssignmentFeedStatus) = repository.getSentAssignedTodos(status)

    suspend fun byFriend(
        friendUserId: String,
        direction: AssignmentDirection,
        status: AssignmentFeedStatus
    ) = repository.getFriendAssignedTodos(friendUserId, direction, status)
}
