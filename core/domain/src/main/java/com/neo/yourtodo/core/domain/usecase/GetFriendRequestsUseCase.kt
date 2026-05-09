package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.FriendRepository
import javax.inject.Inject

class GetFriendRequestsUseCase @Inject constructor(
    private val repository: FriendRepository
) {
    suspend fun incoming() = repository.getIncomingRequests()
    suspend fun outgoing() = repository.getOutgoingRequests()
}
