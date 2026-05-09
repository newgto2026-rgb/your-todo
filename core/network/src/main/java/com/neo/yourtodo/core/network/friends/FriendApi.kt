package com.neo.yourtodo.core.network.friends

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface FriendApi {
    @GET("api/friends")
    suspend fun getFriends(
        @Header("Authorization") authorization: String
    ): NetworkFriendsResponse

    @GET("api/friend-requests/incoming")
    suspend fun getIncomingRequests(
        @Header("Authorization") authorization: String
    ): NetworkFriendRequestsResponse

    @GET("api/friend-requests/outgoing")
    suspend fun getOutgoingRequests(
        @Header("Authorization") authorization: String
    ): NetworkFriendRequestsResponse

    @POST("api/friend-requests")
    suspend fun sendRequest(
        @Header("Authorization") authorization: String,
        @Body request: NetworkSendFriendRequest
    ): NetworkSendFriendRequestResponse

    @POST("api/friend-requests/{requestId}/accept")
    suspend fun acceptRequest(
        @Header("Authorization") authorization: String,
        @Path("requestId") requestId: String
    ): NetworkAcceptFriendRequestResponse

    @POST("api/friend-requests/{requestId}/decline")
    suspend fun declineRequest(
        @Header("Authorization") authorization: String,
        @Path("requestId") requestId: String
    ): NetworkDeclineFriendRequestResponse

    @DELETE("api/friends/{friendshipId}")
    suspend fun removeFriend(
        @Header("Authorization") authorization: String,
        @Path("friendshipId") friendshipId: String
    ): NetworkRemoveFriendResponse
}
