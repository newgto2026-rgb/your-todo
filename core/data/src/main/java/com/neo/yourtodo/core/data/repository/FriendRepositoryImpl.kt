package com.neo.yourtodo.core.data.repository

import com.neo.yourtodo.core.datastore.source.AuthSessionData
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendRequestStatus
import com.neo.yourtodo.core.model.friends.FriendUser
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import com.neo.yourtodo.core.network.auth.AuthNetworkDataSource
import com.neo.yourtodo.core.network.auth.NetworkAuthSession
import com.neo.yourtodo.core.network.friends.FriendAuthRequiredException
import com.neo.yourtodo.core.network.friends.FriendNetworkDataSource
import com.neo.yourtodo.core.network.friends.NetworkFriend
import com.neo.yourtodo.core.network.friends.NetworkFriendRequest
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class FriendRepositoryImpl @Inject constructor(
    private val userPreferencesDataSource: UserPreferencesDataSource,
    private val friendNetworkDataSource: FriendNetworkDataSource,
    private val authNetworkDataSource: AuthNetworkDataSource
) : FriendRepository {
    override suspend fun getFriends(): Result<List<Friend>> =
        authenticatedRequest { accessToken ->
            friendNetworkDataSource.getFriends(accessToken).friends.map { it.toDomain() }
        }

    override suspend fun getIncomingRequests(): Result<List<FriendRequest>> =
        authenticatedRequest { accessToken ->
            friendNetworkDataSource.getIncomingRequests(accessToken).requests.map { it.toDomain() }
        }

    override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> =
        authenticatedRequest { accessToken ->
            friendNetworkDataSource.getOutgoingRequests(accessToken).requests.map { it.toDomain() }
        }

    override suspend fun sendRequest(nickname: String): Result<Unit> =
        authenticatedRequest { accessToken ->
            friendNetworkDataSource.sendRequest(accessToken, nickname)
            Unit
        }

    override suspend fun acceptRequest(requestId: String): Result<Unit> =
        authenticatedRequest { accessToken ->
            friendNetworkDataSource.acceptRequest(accessToken, requestId)
            Unit
        }

    override suspend fun declineRequest(requestId: String): Result<Unit> =
        authenticatedRequest { accessToken ->
            friendNetworkDataSource.declineRequest(accessToken, requestId)
            Unit
        }

    override suspend fun removeFriend(friendshipId: String): Result<Unit> =
        authenticatedRequest { accessToken ->
            friendNetworkDataSource.removeFriend(accessToken, friendshipId)
            Unit
        }

    private suspend fun <T> authenticatedRequest(block: suspend (String) -> T): Result<T> =
        runCatching {
            val session = currentSession() ?: throw AuthRequiredException()
            try {
                block(session.accessToken)
            } catch (throwable: FriendAuthRequiredException) {
                val refreshedSession = refreshSessionOrNull(session.refreshToken)
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

    private suspend fun refreshSessionOrNull(refreshToken: String): NetworkAuthSession? =
        runCatching {
            authNetworkDataSource.refreshSession(refreshToken)
        }.getOrNull()?.also { networkSession ->
            userPreferencesDataSource.saveAuthSession(networkSession.toAuthSessionData())
        }

    private suspend fun authRequired(): Nothing {
        userPreferencesDataSource.clearAuthSession()
        throw AuthRequiredException()
    }

    private fun NetworkAuthSession.toAuthSessionData() =
        AuthSessionData(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            nickname = user.nickname,
            email = user.email,
            onboardingRequired = user.onboardingRequired
        )

    private fun NetworkFriend.toDomain() =
        Friend(
            friendshipId = friendshipId,
            userId = userId,
            nickname = nickname,
            status = enumValueOf(status),
            createdAt = createdAt,
            removedAt = removedAt
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
}
