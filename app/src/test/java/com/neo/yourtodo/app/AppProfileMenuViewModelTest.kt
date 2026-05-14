package com.neo.yourtodo.app

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.AuthRepository
import com.neo.yourtodo.core.domain.repository.PushTokenRepository
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.SignOutUseCase
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.auth.AuthUser
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
        pushTokenRepository: PushTokenRepository = FakePushTokenRepository()
    ): AppProfileMenuViewModel =
        AppProfileMenuViewModel(
            observeAuthSession = ObserveAuthSessionUseCase(this),
            signOutUseCase = SignOutUseCase(this, pushTokenRepository)
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
