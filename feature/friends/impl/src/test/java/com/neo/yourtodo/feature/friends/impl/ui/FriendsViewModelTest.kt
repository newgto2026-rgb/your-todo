package com.neo.yourtodo.feature.friends.impl.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.usecase.GetFriendRequestsUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.RemoveFriendUseCase
import com.neo.yourtodo.core.domain.usecase.RespondFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.SendFriendRequestUseCase
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendRequestStatus
import com.neo.yourtodo.core.model.friends.FriendUser
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class FriendsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialLoadShowsFriendsAndRequests() = runTest {
        val repository = FakeFriendRepository().apply {
            friends = listOf(friend())
            incoming = listOf(request(id = "incoming-1"))
            outgoing = listOf(request(id = "outgoing-1", requesterId = "me", receiverId = "friend-2"))
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            val loaded = awaitItem()
            assertThat(loaded.isLoading).isFalse()
            assertThat(loaded.friends).hasSize(1)
            assertThat(loaded.incomingRequests).hasSize(1)
            assertThat(loaded.outgoingRequests).hasSize(1)
        }
    }

    @Test
    fun sendRequestClearsInputAndRefreshesOutgoingRequests() = runTest {
        val repository = FakeFriendRepository()
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnNicknameChanged("monday"))
            assertThat(awaitItem().nicknameInput).isEqualTo("monday")

            repository.outgoing = listOf(request(id = "sent-1", requesterId = "me", receiverId = "friend-1"))
            viewModel.onAction(FriendsAction.OnSendRequest)

            val running = awaitItem()
            assertThat(running.runningActionKey).isEqualTo("send")

            val clearedInput = awaitItem()
            assertThat(clearedInput.nicknameInput).isEmpty()

            val refreshed = awaitItem()
            assertThat(repository.lastSentNickname).isEqualTo("monday")
            assertThat(refreshed.nicknameInput).isEmpty()
            assertThat(refreshed.outgoingRequests).hasSize(1)
        }
    }

    @Test
    fun acceptRequestRefreshesFriendsAndRemovesIncomingRequest() = runTest {
        val repository = FakeFriendRepository().apply {
            incoming = listOf(request(id = "request-1"))
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            repository.incoming = emptyList()
            repository.friends = listOf(friend())
            viewModel.onAction(FriendsAction.OnAcceptRequest("request-1"))

            assertThat(awaitItem().runningActionKey).isEqualTo("accept:request-1")
            val refreshed = awaitItem()
            assertThat(repository.acceptedRequestId).isEqualTo("request-1")
            assertThat(refreshed.incomingRequests).isEmpty()
            assertThat(refreshed.friends).hasSize(1)
        }
    }

    @Test
    fun authRequiredFailureShowsAuthError() = runTest {
        val repository = FakeFriendRepository(
            getFriendsResult = Result.failure(AuthRequiredException())
        )
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(1)
            assertThat(awaitItem().error).isEqualTo(FriendsError.AUTH_REQUIRED)
        }
    }

    @Test
    fun acceptFailureKeepsPreviousStateAndShowsNetworkError() = runTest {
        val repository = FakeFriendRepository(
            acceptResult = Result.failure(IllegalStateException())
        ).apply {
            incoming = listOf(request(id = "request-1"))
            friends = emptyList()
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnAcceptRequest("request-1"))
            assertThat(awaitItem().runningActionKey).isEqualTo("accept:request-1")

            val failed = awaitItem()
            assertThat(failed.error).isEqualTo(FriendsError.NETWORK)
            assertThat(failed.incomingRequests).hasSize(1)
            assertThat(failed.friends).isEmpty()
        }
    }

    @Test
    fun acceptAuthFailureKeepsPreviousStateAndShowsAuthError() = runTest {
        val repository = FakeFriendRepository(
            acceptResult = Result.failure(AuthRequiredException())
        ).apply {
            incoming = listOf(request(id = "request-1"))
            friends = emptyList()
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnAcceptRequest("request-1"))
            assertThat(awaitItem().runningActionKey).isEqualTo("accept:request-1")

            val failed = awaitItem()
            assertThat(failed.error).isEqualTo(FriendsError.AUTH_REQUIRED)
            assertThat(failed.incomingRequests).hasSize(1)
            assertThat(failed.friends).isEmpty()
        }
    }

    @Test
    fun sendAuthFailureShowsAuthErrorWithoutClearingInput() = runTest {
        val repository = FakeFriendRepository(
            sendResult = Result.failure(AuthRequiredException())
        )
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onAction(FriendsAction.OnNicknameChanged("monday"))
            assertThat(awaitItem().nicknameInput).isEqualTo("monday")

            viewModel.onAction(FriendsAction.OnSendRequest)
            assertThat(awaitItem().runningActionKey).isEqualTo("send")

            val failed = awaitItem()
            assertThat(failed.error).isEqualTo(FriendsError.AUTH_REQUIRED)
            assertThat(failed.nicknameInput).isEqualTo("monday")
        }
    }

    @Test
    fun removeFailureKeepsFriendAndShowsNetworkError() = runTest {
        val repository = FakeFriendRepository(
            removeResult = Result.failure(IllegalStateException())
        ).apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnRemoveFriend("friendship-1"))
            assertThat(awaitItem().runningActionKey).isEqualTo("remove:friendship-1")

            val failed = awaitItem()
            assertThat(failed.error).isEqualTo(FriendsError.NETWORK)
            assertThat(failed.friends).hasSize(1)
        }
    }

    @Test
    fun removeAuthFailureKeepsFriendAndShowsAuthError() = runTest {
        val repository = FakeFriendRepository(
            removeResult = Result.failure(AuthRequiredException())
        ).apply {
            friends = listOf(friend())
        }
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.onAction(FriendsAction.OnRemoveFriend("friendship-1"))
            assertThat(awaitItem().runningActionKey).isEqualTo("remove:friendship-1")

            val failed = awaitItem()
            assertThat(failed.error).isEqualTo(FriendsError.AUTH_REQUIRED)
            assertThat(failed.friends).hasSize(1)
        }
    }

    private fun FakeFriendRepository.createViewModel() =
        FriendsViewModel(
            getFriends = GetFriendsUseCase(this),
            getFriendRequests = GetFriendRequestsUseCase(this),
            sendFriendRequest = SendFriendRequestUseCase(this),
            respondFriendRequest = RespondFriendRequestUseCase(this),
            removeFriend = RemoveFriendUseCase(this)
        )

    private class FakeFriendRepository(
        private val getFriendsResult: Result<List<Friend>>? = null,
        private val sendResult: Result<Unit> = Result.success(Unit),
        private val acceptResult: Result<Unit> = Result.success(Unit),
        private val removeResult: Result<Unit> = Result.success(Unit)
    ) : FriendRepository {
        var friends = emptyList<Friend>()
        var incoming = emptyList<FriendRequest>()
        var outgoing = emptyList<FriendRequest>()
        var lastSentNickname: String? = null
        var acceptedRequestId: String? = null

        override suspend fun getFriends(): Result<List<Friend>> =
            getFriendsResult ?: Result.success(friends)

        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> =
            Result.success(incoming)

        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> =
            Result.success(outgoing)

        override suspend fun sendRequest(nickname: String): Result<Unit> {
            lastSentNickname = nickname
            return sendResult
        }

        override suspend fun acceptRequest(requestId: String): Result<Unit> {
            acceptedRequestId = requestId
            return acceptResult
        }

        override suspend fun declineRequest(requestId: String): Result<Unit> = Result.success(Unit)

        override suspend fun removeFriend(friendshipId: String): Result<Unit> = removeResult
    }
}

private fun friend() = Friend(
    friendshipId = "friendship-1",
    userId = "friend-1",
    nickname = "monday",
    status = FriendshipStatus.ACTIVE,
    createdAt = "2026-05-09T00:00:00Z",
    removedAt = null
)

private fun request(
    id: String,
    requesterId: String = "friend-1",
    receiverId: String = "me"
) = FriendRequest(
    id = id,
    requester = FriendUser(
        id = requesterId,
        nickname = if (requesterId == "me") "tester" else "monday"
    ),
    receiver = FriendUser(
        id = receiverId,
        nickname = if (receiverId == "me") "tester" else "monday"
    ),
    status = FriendRequestStatus.PENDING,
    createdAt = "2026-05-09T00:00:00Z",
    respondedAt = null
)
