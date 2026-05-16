package com.neo.yourtodo.feature.auth.impl

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.AuthRepository
import com.neo.yourtodo.core.domain.usecase.CompleteNicknameOnboardingUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.SignInWithGoogleUseCase
import com.neo.yourtodo.core.domain.usecase.SignOutUseCase
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.auth.AuthUser
import com.neo.yourtodo.core.testing.rule.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AuthGateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiStateMovesFromSignedOutToOnboardingRequiredWhenSessionNeedsNickname() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().destination).isEqualTo(AuthGateDestination.LOADING)
            assertThat(awaitItem().destination).isEqualTo(AuthGateDestination.SIGNED_OUT)

            repository.session.value = authSession(nickname = null, onboardingRequired = true)

            assertThat(awaitItem().destination).isEqualTo(AuthGateDestination.ONBOARDING_REQUIRED)
        }
    }

    @Test
    fun uiStateMovesToSignedInWhenSessionHasNickname() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            repository.session.value = authSession(nickname = "neo", onboardingRequired = false)

            assertThat(awaitItem().destination).isEqualTo(AuthGateDestination.SIGNED_IN)
        }
    }

    @Test
    fun failedServerSignInShowsError() = runTest {
        val repository = FakeAuthRepository(signInResult = Result.failure(IllegalStateException()))
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            skipItems(2)

            viewModel.signInWithGoogleIdToken("google-token")

            assertThat(awaitItem().error).isEqualTo(AuthGateError.SERVER_SIGN_IN_FAILED)
        }
    }

    @Test
    fun completeNicknameOnboardingMovesToSignedInWhenServerSavesNickname() = runTest {
        val repository = FakeAuthRepository()
        repository.session.value = authSession(nickname = null, onboardingRequired = true)
        val viewModel = repository.createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().destination).isEqualTo(AuthGateDestination.LOADING)
            assertThat(awaitItem().destination).isEqualTo(AuthGateDestination.ONBOARDING_REQUIRED)

            viewModel.completeNicknameOnboarding("태윤")

            val signedIn = awaitItem()
            assertThat(signedIn.destination).isEqualTo(AuthGateDestination.SIGNED_IN)
            assertThat(signedIn.nicknameSaveInProgress).isFalse()
            assertThat(repository.lastNickname).isEqualTo("태윤")
        }
    }

    @Test
    fun signInIgnoresDuplicateRequestsWhileInProgress() = runTest {
        val signInGate = CompletableDeferred<Result<AuthSession>>()
        val repository = FakeAuthRepository(signInGate = signInGate)
        val viewModel = repository.createViewModel()

        viewModel.uiState.first { it.destination == AuthGateDestination.SIGNED_OUT }

        viewModel.signInWithGoogleIdToken("first-token")
        viewModel.uiState.first { it.signInInProgress }

        viewModel.signInWithGoogleIdToken("second-token")

        assertThat(repository.signInCalls).isEqualTo(1)
        assertThat(repository.lastGoogleIdToken).isEqualTo("first-token")

        signInGate.complete(Result.success(authSession(nickname = "neo", onboardingRequired = false)))

        val signedIn = viewModel.uiState.first {
            it.destination == AuthGateDestination.SIGNED_IN && !it.signInInProgress
        }
        assertThat(signedIn.error).isNull()
        assertThat(repository.signInCalls).isEqualTo(1)
    }

    private fun FakeAuthRepository.createViewModel(): AuthGateViewModel =
        AuthGateViewModel(
            observeAuthSession = ObserveAuthSessionUseCase(this),
            signInWithGoogle = SignInWithGoogleUseCase(this),
            completeNicknameOnboardingUseCase = CompleteNicknameOnboardingUseCase(this),
            signOut = SignOutUseCase(this)
        )

    private class FakeAuthRepository(
        private val signInResult: Result<AuthSession> = Result.success(
            authSession(nickname = null, onboardingRequired = true)
        ),
        private val signInGate: CompletableDeferred<Result<AuthSession>>? = null
    ) : AuthRepository {
        val session = MutableStateFlow<AuthSession?>(null)
        var lastNickname: String? = null
        var lastGoogleIdToken: String? = null
        var signInCalls: Int = 0

        override val authSession: Flow<AuthSession?> = session

        override suspend fun signInWithGoogle(idToken: String): Result<AuthSession> {
            signInCalls += 1
            lastGoogleIdToken = idToken
            return (signInGate?.await() ?: signInResult)
                .onSuccess { session.value = it }
        }

        override suspend fun completeNicknameOnboarding(nickname: String): Result<AuthSession> {
            lastNickname = nickname
            return session.value
                ?.copy(
                    user = session.value!!.user.copy(
                        nickname = nickname,
                        onboardingRequired = false
                    )
                )
                ?.also { session.value = it }
                ?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException())
        }

        override suspend fun signOut() {
            session.value = null
        }
    }
}

private fun authSession(
    nickname: String?,
    onboardingRequired: Boolean
) = AuthSession(
    accessToken = "access-token",
    refreshToken = "refresh-token",
    user = AuthUser(
        id = "user-id",
        nickname = nickname,
        email = "neo@example.com",
        onboardingRequired = onboardingRequired
    )
)
