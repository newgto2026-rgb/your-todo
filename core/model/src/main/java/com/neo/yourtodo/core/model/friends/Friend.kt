package com.neo.yourtodo.core.model.friends

data class Friend(
    val friendshipId: String,
    val userId: String,
    val nickname: String,
    val status: FriendshipStatus,
    val createdAt: String,
    val removedAt: String?
) {
    val initial: String
        get() = nickname.trim().firstOrNull()?.uppercase() ?: "?"
}

enum class FriendshipStatus {
    ACTIVE,
    REMOVED
}
