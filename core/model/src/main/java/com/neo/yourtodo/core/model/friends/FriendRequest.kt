package com.neo.yourtodo.core.model.friends

data class FriendRequest(
    val id: String,
    val requester: FriendUser,
    val receiver: FriendUser,
    val status: FriendRequestStatus,
    val createdAt: String,
    val respondedAt: String?
)

data class FriendUser(
    val id: String,
    val nickname: String
) {
    val initial: String
        get() = nickname.trim().firstOrNull()?.uppercase() ?: "?"
}

enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELED
}
