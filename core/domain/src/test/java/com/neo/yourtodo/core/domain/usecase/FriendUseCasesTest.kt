package com.neo.yourtodo.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendRequestStatus
import com.neo.yourtodo.core.model.friends.FriendUser
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FriendUseCasesTest {

    @Test
    fun getFriendsDelegatesToRepository() = runTest {
        val friends = listOf(testFriend())
        val repository = FakeFriendRepository(friendsResult = Result.success(friends))
        val useCase = GetFriendsUseCase(repository)

        val result = useCase()

        assertThat(repository.getFriendsCount).isEqualTo(1)
        assertThat(result.getOrNull()).isEqualTo(friends)
    }

    @Test
    fun getFriendRequestsDelegatesIncomingAndOutgoingToRepository() = runTest {
        val incoming = listOf(testRequest(id = "incoming-request"))
        val outgoing = listOf(testRequest(id = "outgoing-request"))
        val repository = FakeFriendRepository(
            incomingResult = Result.success(incoming),
            outgoingResult = Result.success(outgoing)
        )
        val useCase = GetFriendRequestsUseCase(repository)

        val incomingResult = useCase.incoming()
        val outgoingResult = useCase.outgoing()

        assertThat(repository.getIncomingCount).isEqualTo(1)
        assertThat(repository.getOutgoingCount).isEqualTo(1)
        assertThat(incomingResult.getOrNull()).isEqualTo(incoming)
        assertThat(outgoingResult.getOrNull()).isEqualTo(outgoing)
    }

    @Test
    fun sendFriendRequestDelegatesNicknameToRepository() = runTest {
        val repository = FakeFriendRepository(sendResult = Result.success(Unit))
        val useCase = SendFriendRequestUseCase(repository)

        val result = useCase("taeyun")

        assertThat(repository.sentNicknames).containsExactly("taeyun")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun respondFriendRequestDelegatesAcceptAndDeclineToRepository() = runTest {
        val repository = FakeFriendRepository(
            acceptResult = Result.success(Unit),
            declineResult = Result.success(Unit)
        )
        val useCase = RespondFriendRequestUseCase(repository)

        val acceptResult = useCase.accept("request-accept")
        val declineResult = useCase.decline("request-decline")

        assertThat(repository.acceptedRequestIds).containsExactly("request-accept")
        assertThat(repository.declinedRequestIds).containsExactly("request-decline")
        assertThat(acceptResult.isSuccess).isTrue()
        assertThat(declineResult.isSuccess).isTrue()
    }

    @Test
    fun removeFriendDelegatesFriendshipIdToRepository() = runTest {
        val repository = FakeFriendRepository(removeResult = Result.success(Unit))
        val useCase = RemoveFriendUseCase(repository)

        val result = useCase("friendship-id")

        assertThat(repository.removedFriendshipIds).containsExactly("friendship-id")
        assertThat(result.isSuccess).isTrue()
    }

    private fun testFriend(): Friend =
        Friend(
            friendshipId = "friendship-id",
            userId = "friend-id",
            nickname = "friend",
            status = FriendshipStatus.ACTIVE,
            createdAt = "2026-05-09T00:00:00.000Z",
            removedAt = null
        )

    private fun testRequest(id: String): FriendRequest =
        FriendRequest(
            id = id,
            requester = FriendUser(id = "requester-id", nickname = "requester"),
            receiver = FriendUser(id = "receiver-id", nickname = "receiver"),
            status = FriendRequestStatus.PENDING,
            createdAt = "2026-05-09T00:00:00.000Z",
            respondedAt = null
        )

    private class FakeFriendRepository(
        private val friendsResult: Result<List<Friend>> =
            Result.failure(UnsupportedOperationException()),
        private val incomingResult: Result<List<FriendRequest>> =
            Result.failure(UnsupportedOperationException()),
        private val outgoingResult: Result<List<FriendRequest>> =
            Result.failure(UnsupportedOperationException()),
        private val sendResult: Result<Unit> =
            Result.failure(UnsupportedOperationException()),
        private val acceptResult: Result<Unit> =
            Result.failure(UnsupportedOperationException()),
        private val declineResult: Result<Unit> =
            Result.failure(UnsupportedOperationException()),
        private val removeResult: Result<Unit> =
            Result.failure(UnsupportedOperationException())
    ) : FriendRepository {
        var getFriendsCount = 0
            private set
        var getIncomingCount = 0
            private set
        var getOutgoingCount = 0
            private set
        val sentNicknames = mutableListOf<String>()
        val acceptedRequestIds = mutableListOf<String>()
        val declinedRequestIds = mutableListOf<String>()
        val removedFriendshipIds = mutableListOf<String>()

        override suspend fun getFriends(): Result<List<Friend>> {
            getFriendsCount += 1
            return friendsResult
        }

        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> {
            getIncomingCount += 1
            return incomingResult
        }

        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> {
            getOutgoingCount += 1
            return outgoingResult
        }

        override suspend fun sendRequest(nickname: String): Result<Unit> {
            sentNicknames += nickname
            return sendResult
        }

        override suspend fun acceptRequest(requestId: String): Result<Unit> {
            acceptedRequestIds += requestId
            return acceptResult
        }

        override suspend fun declineRequest(requestId: String): Result<Unit> {
            declinedRequestIds += requestId
            return declineResult
        }

        override suspend fun removeFriend(friendshipId: String): Result<Unit> {
            removedFriendshipIds += friendshipId
            return removeResult
        }
    }
}
