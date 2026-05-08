package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.FriendRepository
import javax.inject.Inject

class RespondFriendRequestUseCase @Inject constructor(
    private val repository: FriendRepository
) {
    suspend fun accept(requestId: String) = repository.acceptRequest(requestId)
    suspend fun decline(requestId: String) = repository.declineRequest(requestId)
}
