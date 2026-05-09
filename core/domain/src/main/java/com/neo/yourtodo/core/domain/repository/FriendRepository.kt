package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest

interface FriendRepository {
    suspend fun getFriends(): Result<List<Friend>>
    suspend fun getIncomingRequests(): Result<List<FriendRequest>>
    suspend fun getOutgoingRequests(): Result<List<FriendRequest>>
    suspend fun sendRequest(nickname: String): Result<Unit>
    suspend fun acceptRequest(requestId: String): Result<Unit>
    suspend fun declineRequest(requestId: String): Result<Unit>
    suspend fun removeFriend(friendshipId: String): Result<Unit>
}
