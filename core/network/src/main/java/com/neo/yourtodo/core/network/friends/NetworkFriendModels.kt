package com.neo.yourtodo.core.network.friends

import com.neo.yourtodo.core.network.assignments.NetworkDirectAssignmentConsentSummary
import kotlinx.serialization.Serializable

@Serializable
data class NetworkFriendsResponse(
    val friends: List<NetworkFriend>
)

@Serializable
data class NetworkFriend(
    val friendshipId: String,
    val userId: String,
    val nickname: String,
    val status: String,
    val createdAt: String,
    val removedAt: String? = null,
    val directAssignment: NetworkDirectAssignmentConsentSummary? = null
)

@Serializable
data class NetworkFriendRequestsResponse(
    val requests: List<NetworkFriendRequest>
)

@Serializable
data class NetworkFriendRequest(
    val id: String,
    val requester: NetworkFriendUser,
    val receiver: NetworkFriendUser,
    val status: String,
    val createdAt: String,
    val respondedAt: String? = null
)

@Serializable
data class NetworkFriendUser(
    val id: String,
    val nickname: String
)

@Serializable
data class NetworkSendFriendRequest(
    val nickname: String
)

@Serializable
data class NetworkSendFriendRequestResponse(
    val request: NetworkFriendRequest,
    val autoAccepted: Boolean,
    val friendship: NetworkFriend? = null
)

@Serializable
data class NetworkAcceptFriendRequestResponse(
    val request: NetworkFriendRequest,
    val friendship: NetworkFriend
)

@Serializable
data class NetworkDeclineFriendRequestResponse(
    val request: NetworkFriendRequest
)

@Serializable
data class NetworkRemoveFriendResponse(
    val friendship: NetworkFriend
)
