package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendRequestStatus
import com.neo.yourtodo.core.model.friends.FriendUser
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentState
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentSummary
import com.neo.yourtodo.core.network.assignments.NetworkDirectAssignmentConsentSummary
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.friends.FriendAuthRequiredException
import com.neo.yourtodo.core.network.friends.FriendNetworkDataSource
import com.neo.yourtodo.core.network.friends.NetworkFriend
import com.neo.yourtodo.core.network.friends.NetworkFriendRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Server-authored Friends implementation.
 *
 * This repository intentionally does not read or write a Room/DataStore friends
 * cache. List methods expose the current server snapshot only; network failures
 * stay as failures so UI can distinguish "not loaded" from "loaded and empty".
 */
class FriendRepositoryImpl @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val friendNetworkDataSource: FriendNetworkDataSource,
    authNetworkDataSource: AuthNetworkDataSource,
    private val authSessionRefresher: AuthSessionRefresher =
        AuthSessionRefresher(userPreferencesDataSource, authNetworkDataSource),
    private val assignmentFeedFreshnessTracker: AssignmentFeedFreshnessTracker =
        AssignmentFeedFreshnessTracker()
) : FriendRepository {
    override suspend fun getFriends(): Result<List<Friend>> =
        onlineOnlyAuthenticatedRequest { accessToken ->
            friendNetworkDataSource.getFriends(accessToken).friends.map { it.toDomain() }
        }

    override suspend fun getIncomingRequests(): Result<List<FriendRequest>> =
        onlineOnlyAuthenticatedRequest { accessToken ->
            friendNetworkDataSource.getIncomingRequests(accessToken).requests.map { it.toDomain() }
        }

    override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> =
        onlineOnlyAuthenticatedRequest { accessToken ->
            friendNetworkDataSource.getOutgoingRequests(accessToken).requests.map { it.toDomain() }
        }

    override suspend fun sendRequest(nickname: String): Result<Unit> =
        onlineOnlyAuthenticatedRequest { accessToken ->
            friendNetworkDataSource.sendRequest(accessToken, nickname)
            Unit
        }

    override suspend fun acceptRequest(requestId: String): Result<Unit> =
        onlineOnlyAuthenticatedRequest { accessToken ->
            friendNetworkDataSource.acceptRequest(accessToken, requestId)
            Unit
        }

    override suspend fun declineRequest(requestId: String): Result<Unit> =
        onlineOnlyAuthenticatedRequest { accessToken ->
            friendNetworkDataSource.declineRequest(accessToken, requestId)
            Unit
        }

    override suspend fun removeFriend(friendshipId: String): Result<Unit> =
        onlineOnlyAuthenticatedRequest { accessToken ->
            friendNetworkDataSource.removeFriend(accessToken, friendshipId)
            Unit
        }

    private suspend fun <T> onlineOnlyAuthenticatedRequest(block: suspend (String) -> T): Result<T> =
        try {
            Result.success(onlineOnlyAuthenticatedValue(block))
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }

    private suspend fun <T> onlineOnlyAuthenticatedValue(block: suspend (String) -> T): T {
        val session = currentSession() ?: throw AuthRequiredException()
        return try {
            block(session.accessToken)
        } catch (throwable: FriendAuthRequiredException) {
            val refreshedSession = authSessionRefresher.refresh(session.refreshToken)
                ?: authRequired()
            try {
                block(refreshedSession.accessToken)
            } catch (retryThrowable: FriendAuthRequiredException) {
                authRequired()
            }
        }
    }

    private suspend fun currentSession() =
        userPreferencesDataSource.authSession.first()
            ?.takeUnless { it.onboardingRequired }

    private suspend fun authRequired(): Nothing {
        assignmentFeedFreshnessTracker.clear()
        userPreferencesDataSource.clearAuthSession()
        throw AuthRequiredException()
    }

    private fun NetworkFriend.toDomain() =
        Friend(
            friendshipId = friendshipId,
            userId = userId,
            nickname = nickname,
            status = enumValueOf(status),
            createdAt = createdAt,
            removedAt = removedAt,
            directAssignment = directAssignment.toDomain()
        )

    private fun NetworkFriendRequest.toDomain() =
        FriendRequest(
            id = id,
            requester = FriendUser(
                id = requester.id,
                nickname = requester.nickname
            ),
            receiver = FriendUser(
                id = receiver.id,
                nickname = receiver.nickname
            ),
            status = enumValueOf(status),
            createdAt = createdAt,
            respondedAt = respondedAt
        )

    private inline fun <reified T : Enum<T>> enumValueOf(value: String): T =
        enumValues<T>().firstOrNull { it.name == value } ?: error("Unknown enum value: $value")

    private fun NetworkDirectAssignmentConsentSummary?.toDomain() =
        DirectAssignmentConsentSummary(
            grantedByMe = this?.canFriendDirectAssignToMe?.toConsentState()
                ?: this?.grantedByMe?.let { enumValueOrDefault(it, DirectAssignmentConsentState.NONE) }
                ?: DirectAssignmentConsentState.NONE,
            grantedToMe = this?.canDirectAssignToFriend?.toConsentState()
                ?: this?.grantedToMe?.let { enumValueOrDefault(it, DirectAssignmentConsentState.NONE) }
                ?: DirectAssignmentConsentState.NONE
        )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default

    private fun Boolean.toConsentState(): DirectAssignmentConsentState =
        if (this) DirectAssignmentConsentState.ACTIVE else DirectAssignmentConsentState.NONE
}
