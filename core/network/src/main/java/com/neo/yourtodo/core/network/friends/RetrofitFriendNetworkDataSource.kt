package com.neo.yourtodo.core.network.friends

import javax.inject.Inject
import retrofit2.HttpException

internal class RetrofitFriendNetworkDataSource @Inject constructor(
    private val api: FriendApi
) : FriendNetworkDataSource {
    override suspend fun getFriends(accessToken: String): NetworkFriendsResponse =
        runFriendRequest { api.getFriends(accessToken.authorizationHeader()) }

    override suspend fun getIncomingRequests(accessToken: String): NetworkFriendRequestsResponse =
        runFriendRequest { api.getIncomingRequests(accessToken.authorizationHeader()) }

    override suspend fun getOutgoingRequests(accessToken: String): NetworkFriendRequestsResponse =
        runFriendRequest { api.getOutgoingRequests(accessToken.authorizationHeader()) }

    override suspend fun sendRequest(
        accessToken: String,
        nickname: String
    ): NetworkSendFriendRequestResponse =
        runFriendRequest {
            api.sendRequest(
                authorization = accessToken.authorizationHeader(),
                request = NetworkSendFriendRequest(nickname)
            )
        }

    override suspend fun acceptRequest(
        accessToken: String,
        requestId: String
    ): NetworkAcceptFriendRequestResponse =
        runFriendRequest { api.acceptRequest(accessToken.authorizationHeader(), requestId) }

    override suspend fun declineRequest(
        accessToken: String,
        requestId: String
    ): NetworkDeclineFriendRequestResponse =
        runFriendRequest { api.declineRequest(accessToken.authorizationHeader(), requestId) }

    override suspend fun removeFriend(
        accessToken: String,
        friendshipId: String
    ): NetworkRemoveFriendResponse =
        runFriendRequest { api.removeFriend(accessToken.authorizationHeader(), friendshipId) }

    private suspend fun <T> runFriendRequest(block: suspend () -> T): T =
        try {
            block()
        } catch (throwable: HttpException) {
            if (throwable.code() == 401) {
                throw FriendAuthRequiredException()
            }
            throw throwable
        }

    private fun String.authorizationHeader() = "Bearer $this"
}
