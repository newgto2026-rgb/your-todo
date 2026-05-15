package com.neo.yourtodo.core.model.friends

data class Friend(
    val friendshipId: String,
    val userId: String,
    val nickname: String,
    val status: FriendshipStatus,
    val createdAt: String,
    val removedAt: String?,
    val directAssignment: DirectAssignmentConsentSummary = DirectAssignmentConsentSummary()
) {
    val initial: String
        get() = nickname.trim().firstOrNull()?.uppercase() ?: "?"
}

enum class FriendshipStatus {
    ACTIVE,
    REMOVED
}

data class DirectAssignmentConsentSummary(
    val grantedByMe: DirectAssignmentConsentState = DirectAssignmentConsentState.NONE,
    val grantedToMe: DirectAssignmentConsentState = DirectAssignmentConsentState.NONE
) {
    val canFriendDirectAssignToMe: Boolean
        get() = grantedByMe == DirectAssignmentConsentState.ACTIVE

    val canDirectAssignToFriend: Boolean
        get() = grantedToMe == DirectAssignmentConsentState.ACTIVE
}

enum class DirectAssignmentConsentState {
    NONE,
    PENDING,
    ACTIVE,
    REVOKED,
    EXPIRED
}
