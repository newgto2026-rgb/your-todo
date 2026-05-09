package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.FriendRepository
import javax.inject.Inject

class SendFriendRequestUseCase @Inject constructor(
    private val repository: FriendRepository
) {
    suspend operator fun invoke(nickname: String) = repository.sendRequest(nickname)
}
