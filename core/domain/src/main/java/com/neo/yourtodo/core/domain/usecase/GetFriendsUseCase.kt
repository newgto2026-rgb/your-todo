package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.FriendRepository
import javax.inject.Inject

class GetFriendsUseCase @Inject constructor(
    private val repository: FriendRepository
) {
    suspend operator fun invoke() = repository.getFriends()
}
