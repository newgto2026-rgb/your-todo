package com.neo.yourtodo.core.network.friends

interface FriendNetworkDataSource {
    suspend fun getFriends(accessToken: String): NetworkFriendsResponse
    suspend fun getIncomingRequests(accessToken: String): NetworkFriendRequestsResponse
    suspend fun getOutgoingRequests(accessToken: String): NetworkFriendRequestsResponse
    suspend fun sendRequest(accessToken: String, nickname: String): NetworkSendFriendRequestResponse
    suspend fun acceptRequest(accessToken: String, requestId: String): NetworkAcceptFriendRequestResponse
    suspend fun declineRequest(accessToken: String, requestId: String): NetworkDeclineFriendRequestResponse
    suspend fun removeFriend(accessToken: String, friendshipId: String): NetworkRemoveFriendResponse
}
