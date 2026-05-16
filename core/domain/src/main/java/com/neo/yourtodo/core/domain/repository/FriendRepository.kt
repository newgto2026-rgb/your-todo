package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest

/**
 * Online-only friend relationship access.
 *
 * Friends and friend requests are server-authored snapshots. Query methods must
 * return a fresh network result or `Result.failure`; callers must not treat a
 * failure as an empty local cache.
 */
interface FriendRepository {
    suspend fun getFriends(): Result<List<Friend>>
    suspend fun getIncomingRequests(): Result<List<FriendRequest>>
    suspend fun getOutgoingRequests(): Result<List<FriendRequest>>
    suspend fun sendRequest(nickname: String): Result<Unit>
    suspend fun acceptRequest(requestId: String): Result<Unit>
    suspend fun declineRequest(requestId: String): Result<Unit>
    suspend fun removeFriend(friendshipId: String): Result<Unit>
}
