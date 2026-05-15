package com.neo.yourtodo.app

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.R
import com.neo.yourtodo.core.domain.repository.AssignmentDirection
import com.neo.yourtodo.core.domain.repository.AssignmentFeedStatus
import com.neo.yourtodo.core.domain.repository.AssignmentRepository
import com.neo.yourtodo.core.domain.repository.AuthRepository
import com.neo.yourtodo.core.domain.repository.FriendRepository
import com.neo.yourtodo.core.domain.repository.PushTokenRepository
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.ManageDirectAssignmentConsentUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.SignOutUseCase
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignmentBundle
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDecision
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.FriendAssignmentSummary
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.auth.AuthUser
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentState
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentSummary
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.friends.FriendshipStatus
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AppProfileMenuViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiStateDisplaysCurrentSessionProfile() = runTest {
        val repository = FakeAuthRepository(
            initialSession = authSession(nickname = "taeyun", email = "taeyun@example.com")
        )
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(1)
            val signedIn = awaitItem()

            assertThat(signedIn.nickname).isEqualTo("taeyun")
            assertThat(signedIn.email).isEqualTo("taeyun@example.com")
            assertThat(signedIn.canCopyNickname).isTrue()
            assertThat(signedIn.isSignedIn).isTrue()
        }
    }

    @Test
    fun refreshDirectAssignmentPermissionsShowsIncomingPendingAndActiveFriends() = runTest {
        val repository = FakeAuthRepository(initialSession = authSession())
        val friendRepository = FakeFriendRepository(
            friends = listOf(
                friend(
                    userId = "active",
                    nickname = "active friend",
                    grantedByMe = DirectAssignmentConsentState.ACTIVE
                ),
                friend(
                    userId = "pending",
                    nickname = "pending friend",
                    grantedByMe = DirectAssignmentConsentState.PENDING
                ),
                friend(
                    userId = "outgoing-only",
                    nickname = "outgoing",
                    grantedToMe = DirectAssignmentConsentState.ACTIVE
                )
            )
        )
        val viewModel = repository.createViewModel(friendRepository = friendRepository)

        viewModel.uiState.test {
            skipItems(1)
            assertThat(awaitItem().isSignedIn).isTrue()

            viewModel.refreshDirectAssignmentPermissions()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            val firstRefresh = awaitItem()
            val refreshed = firstRefresh.takeIf { it.directAssignmentPermissions.isNotEmpty() } ?: awaitItem()
            assertThat(refreshed.directAssignmentPermissions.map { it.friendUserId })
                .containsExactly("pending", "active")
                .inOrder()
        }
    }

    @Test
    fun directAssignmentPermissionActionsDelegateAndEmitSuccess() = runTest {
        val repository = FakeAuthRepository(initialSession = authSession())
        val assignmentRepository = FakeAssignmentRepository()
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.sideEffect.test {
            viewModel.acceptDirectAssignment("friend-1")
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertThat(assignmentRepository.acceptedFriendIds).containsExactly("friend-1")
            assertThat(awaitItem())
                .isEqualTo(
                    AppProfileMenuSideEffect.DirectAssignmentPermissionUpdated(
                        R.string.profile_menu_direct_assignment_allowed
                    )
                )
        }
    }

    @Test
    fun revokeDirectAssignmentPermissionDelegatesAndEmitsSuccess() = runTest {
        val repository = FakeAuthRepository(initialSession = authSession())
        val assignmentRepository = FakeAssignmentRepository()
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.sideEffect.test {
            viewModel.revokeDirectAssignment("friend-1")
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertThat(assignmentRepository.revokedFriendIds).containsExactly("friend-1")
            assertThat(awaitItem())
                .isEqualTo(
                    AppProfileMenuSideEffect.DirectAssignmentPermissionUpdated(
                        R.string.profile_menu_direct_assignment_revoked
                    )
                )
        }
    }

    @Test
    fun rejectDirectAssignmentPermissionDelegatesAndEmitsSuccess() = runTest {
        val repository = FakeAuthRepository(initialSession = authSession())
        val assignmentRepository = FakeAssignmentRepository()
        val viewModel = repository.createViewModel(assignmentRepository = assignmentRepository)

        viewModel.sideEffect.test {
            viewModel.rejectDirectAssignment("friend-1")
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertThat(assignmentRepository.rejectedFriendIds).containsExactly("friend-1")
            assertThat(awaitItem())
                .isEqualTo(
                    AppProfileMenuSideEffect.DirectAssignmentPermissionUpdated(
                        R.string.profile_menu_direct_assignment_rejected
                    )
                )
        }
    }

    @Test
    fun signedOutStateDoesNotExposeDirectAssignmentPermissions() = runTest {
        val repository = FakeAuthRepository(initialSession = null)
        val friendRepository = FakeFriendRepository(
            friends = listOf(
                friend(
                    userId = "active",
                    nickname = "active friend",
                    grantedByMe = DirectAssignmentConsentState.ACTIVE
                )
            )
        )
        val viewModel = repository.createViewModel(friendRepository = friendRepository)

        viewModel.refreshDirectAssignmentPermissions()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSignedIn).isFalse()
        assertThat(viewModel.uiState.value.directAssignmentPermissions).isEmpty()
    }

    @Test
    fun uiStateDisablesCopyWhenNicknameIsBlank() = runTest {
        val repository = FakeAuthRepository(
            initialSession = authSession(nickname = " ", email = "taeyun@example.com")
        )
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(1)
            val signedIn = awaitItem()

            assertThat(signedIn.nickname).isNull()
            assertThat(signedIn.canCopyNickname).isFalse()
            assertThat(signedIn.isSignedIn).isTrue()
        }
    }

    @Test
    fun signOutEmitsSignedOutAndClearsSession() = runTest {
        val repository = FakeAuthRepository(initialSession = authSession())
        val pushTokenRepository = FakePushTokenRepository()
        val viewModel = repository.createViewModel(pushTokenRepository)

        viewModel.sideEffect.test {
            viewModel.signOut()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(AppProfileMenuSideEffect.SignedOut)
            assertThat(repository.signOutCount).isEqualTo(1)
            assertThat(pushTokenRepository.deleteRegisteredTokenCount).isEqualTo(1)
            assertThat(repository.currentSession).isNull()
            assertThat(viewModel.uiState.value.isSigningOut).isFalse()
        }
    }

    @Test
    fun failedSignOutEmitsFailureAndKeepsSession() = runTest {
        val repository = FakeAuthRepository(initialSession = authSession())
        val viewModel = repository.createViewModel(
            pushTokenRepository = FakePushTokenRepository(failDelete = true)
        )

        viewModel.sideEffect.test {
            viewModel.signOut()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(AppProfileMenuSideEffect.LogoutFailed)
            assertThat(repository.signOutCount).isEqualTo(0)
            assertThat(repository.currentSession).isNotNull()
            assertThat(viewModel.uiState.value.isSigningOut).isFalse()
        }
    }

    private fun FakeAuthRepository.createViewModel(
        pushTokenRepository: PushTokenRepository = FakePushTokenRepository(),
        friendRepository: FriendRepository = FakeFriendRepository(),
        assignmentRepository: AssignmentRepository = FakeAssignmentRepository()
    ): AppProfileMenuViewModel =
        AppProfileMenuViewModel(
            observeAuthSession = ObserveAuthSessionUseCase(this),
            signOutUseCase = SignOutUseCase(this, pushTokenRepository),
            getFriendsUseCase = GetFriendsUseCase(friendRepository),
            manageDirectAssignmentConsent = ManageDirectAssignmentConsentUseCase(assignmentRepository)
        )

    private class FakeAuthRepository(
        initialSession: AuthSession?
    ) : AuthRepository {
        private val session = MutableStateFlow(initialSession)
        var signOutCount = 0
        val currentSession: AuthSession?
            get() = session.value

        override val authSession: Flow<AuthSession?> = session

        override suspend fun signInWithGoogle(idToken: String): Result<AuthSession> =
            Result.failure(UnsupportedOperationException())

        override suspend fun completeNicknameOnboarding(nickname: String): Result<AuthSession> =
            Result.failure(UnsupportedOperationException())

        override suspend fun signOut() {
            signOutCount += 1
            session.value = null
        }
    }

    private class FakePushTokenRepository(
        private val failDelete: Boolean = false
    ) : PushTokenRepository {
        var deleteRegisteredTokenCount = 0

        override suspend fun saveCurrentToken(token: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun registerCurrentToken(): Result<Unit> =
            Result.success(Unit)

        override suspend fun deleteRegisteredToken(): Result<Unit> {
            deleteRegisteredTokenCount += 1
            if (failDelete) error("push token delete failed")
            return Result.success(Unit)
        }
    }

    private class FakeFriendRepository(
        var friends: List<Friend> = emptyList()
    ) : FriendRepository {
        override suspend fun getFriends(): Result<List<Friend>> = Result.success(friends)

        override suspend fun getIncomingRequests(): Result<List<FriendRequest>> =
            Result.success(emptyList())

        override suspend fun getOutgoingRequests(): Result<List<FriendRequest>> =
            Result.success(emptyList())

        override suspend fun sendRequest(nickname: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun acceptRequest(requestId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun declineRequest(requestId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun removeFriend(friendshipId: String): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeAssignmentRepository : AssignmentRepository {
        val acceptedFriendIds = mutableListOf<String>()
        val rejectedFriendIds = mutableListOf<String>()
        val revokedFriendIds = mutableListOf<String>()

        override suspend fun createBundle(
            receiverUserId: String,
            items: List<AssignmentDraftItem>,
            assignmentMode: com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
        ): Result<AssignmentBundle> = Result.failure(UnsupportedOperationException())

        override suspend fun acceptDirectAssignmentConsent(
            friendUserId: String
        ): Result<DirectAssignmentConsentSummary> {
            acceptedFriendIds += friendUserId
            return Result.success(DirectAssignmentConsentSummary(grantedByMe = DirectAssignmentConsentState.ACTIVE))
        }

        override suspend fun rejectDirectAssignmentConsent(
            friendUserId: String
        ): Result<DirectAssignmentConsentSummary> {
            rejectedFriendIds += friendUserId
            return Result.success(DirectAssignmentConsentSummary(grantedByMe = DirectAssignmentConsentState.REVOKED))
        }

        override suspend fun revokeDirectAssignmentConsent(
            friendUserId: String
        ): Result<DirectAssignmentConsentSummary> {
            revokedFriendIds += friendUserId
            return Result.success(DirectAssignmentConsentSummary(grantedByMe = DirectAssignmentConsentState.REVOKED))
        }

        override suspend fun getFriendSummary(friendUserId: String): Result<FriendAssignmentSummary> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Result<List<AssignedTodo>> = Result.failure(UnsupportedOperationException())

        override suspend fun getReceivedAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getSentAssignedTodos(status: AssignmentFeedStatus): Result<List<AssignedTodo>> =
            Result.failure(UnsupportedOperationException())

        override fun observeReceivedAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            MutableStateFlow(emptyList())

        override fun observeSentAssignedTodos(status: AssignmentFeedStatus): Flow<List<AssignedTodo>> =
            MutableStateFlow(emptyList())

        override fun observeFriendAssignedTodos(
            friendUserId: String,
            direction: AssignmentDirection,
            status: AssignmentFeedStatus
        ): Flow<List<AssignedTodo>> = MutableStateFlow(emptyList())

        override suspend fun decideBundleItems(
            bundleId: String,
            decisions: Map<String, AssignmentDecision>
        ): Result<AssignmentBundle> = Result.failure(UnsupportedOperationException())

        override suspend fun completeAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun reopenAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun deleteReceivedAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun hideReceivedAssignedTodoFromTaskSurface(assignedTodoId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun cancelAssignedTodo(assignedTodoId: String): Result<AssignedTodo> =
            Result.failure(UnsupportedOperationException())

        override suspend fun upsertAssignedTodoReminder(
            assignedTodoId: String,
            reminderAt: String,
            enabled: Boolean
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun deleteAssignedTodoReminder(assignedTodoId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }
}

private fun authSession(
    nickname: String = "tester",
    email: String = "tester@example.com"
) = AuthSession(
    accessToken = "access-token",
    refreshToken = "refresh-token",
    user = AuthUser(
        id = "user-id",
        nickname = nickname,
        email = email,
        onboardingRequired = false
    )
)

private fun friend(
    userId: String,
    nickname: String,
    grantedByMe: DirectAssignmentConsentState = DirectAssignmentConsentState.NONE,
    grantedToMe: DirectAssignmentConsentState = DirectAssignmentConsentState.NONE
) = Friend(
    friendshipId = "friendship-$userId",
    userId = userId,
    nickname = nickname,
    status = FriendshipStatus.ACTIVE,
    createdAt = "2026-05-15T00:00:00Z",
    removedAt = null,
    directAssignment = DirectAssignmentConsentSummary(
        grantedByMe = grantedByMe,
        grantedToMe = grantedToMe
    )
)
