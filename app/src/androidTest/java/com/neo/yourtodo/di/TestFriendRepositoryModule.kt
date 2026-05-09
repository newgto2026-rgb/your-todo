package com.neo.yourtodo.di

import com.neo.yourtodo.core.data.di.FriendRepositoryModule
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendRequestStatus
import com.neo.yourtodo.core.model.friends.FriendUser
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [FriendRepositoryModule::class]
)
object TestFriendRepositoryModule {
    @Provides
    @Singleton
    fun provideTestFriendRepository(): TestFriendRepository = TestFriendRepository()

    @Provides
    @Singleton
    fun provideFriendRepository(repository: TestFriendRepository): FriendRepository = repository
}

class TestFriendRepository : FriendRepository {
    private var friends = emptyList<Friend>()
    private var incoming = emptyList<FriendRequest>()
    private var outgoing = emptyList<FriendRequest>()

    fun reset() {
        friends = listOf(friend("friendship-1", "friend-1", "monday"))
        incoming = listOf(request("incoming-1", requesterId = "friend-2", requesterNickname = "neo"))
        outgoing = listOf(request("outgoing-1", receiverId = "friend-3", receiverNickname = "summer"))
    }

    override suspend fun getFriends(): Result<List<Friend>> = Result.success(friends)

    override suspend fun getIncomingRequests(): Result<List<FriendRequest>> = Result.success(incoming)

    override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> = Result.success(outgoing)

    override suspend fun sendRequest(nickname: String): Result<Unit> {
        outgoing = outgoing + request(
            id = "outgoing-added",
            receiverId = "friend-added",
            receiverNickname = nickname
        )
        return Result.success(Unit)
    }

    override suspend fun acceptRequest(requestId: String): Result<Unit> {
        val accepted = incoming.firstOrNull { it.id == requestId } ?: return Result.success(Unit)
        incoming = incoming.filterNot { it.id == requestId }
        friends = friends + friend(
            friendshipId = "friendship-${accepted.requester.id}",
            userId = accepted.requester.id,
            nickname = accepted.requester.nickname
        )
        return Result.success(Unit)
    }

    override suspend fun declineRequest(requestId: String): Result<Unit> {
        incoming = incoming.filterNot { it.id == requestId }
        return Result.success(Unit)
    }

    override suspend fun removeFriend(friendshipId: String): Result<Unit> {
        friends = friends.filterNot { it.friendshipId == friendshipId }
        return Result.success(Unit)
    }
}

private fun friend(
    friendshipId: String,
    userId: String,
    nickname: String
) = Friend(
    friendshipId = friendshipId,
    userId = userId,
    nickname = nickname,
    status = FriendshipStatus.ACTIVE,
    createdAt = "2026-05-09T00:00:00Z",
    removedAt = null
)

private fun request(
    id: String,
    requesterId: String = "android-test-user",
    requesterNickname: String = "tester",
    receiverId: String = "android-test-user",
    receiverNickname: String = "tester"
) = FriendRequest(
    id = id,
    requester = FriendUser(
        id = requesterId,
        nickname = requesterNickname
    ),
    receiver = FriendUser(
        id = receiverId,
        nickname = receiverNickname
    ),
    status = FriendRequestStatus.PENDING,
    createdAt = "2026-05-09T00:00:00Z",
    respondedAt = null
)
