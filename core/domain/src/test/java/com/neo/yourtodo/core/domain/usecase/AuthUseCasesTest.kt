package com.neo.yourtodo.core.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.neo.yourtodo.core.domain.repository.AuthRepository
import com.neo.yourtodo.core.model.auth.AuthSession
import com.neo.yourtodo.core.model.auth.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthUseCasesTest {

    @Test
    fun observeAuthSessionReturnsRepositorySessionFlow() = runTest {
        val repository = FakeAuthRepository()
        val useCase = ObserveAuthSessionUseCase(repository)
        val session = testSession(nickname = "taeyunlive")

        useCase().test {
            assertThat(awaitItem()).isNull()

            repository.authSession.value = session

            assertThat(awaitItem()).isEqualTo(session)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun signInWithGoogleDelegatesToRepository() = runTest {
        val expectedSession = testSession(nickname = null)
        val repository = FakeAuthRepository(
            signInResult = Result.success(expectedSession)
        )
        val useCase = SignInWithGoogleUseCase(repository)

        val result = useCase("google-id-token")

        assertThat(repository.signInTokens).containsExactly("google-id-token")
        assertThat(result.getOrNull()).isEqualTo(expectedSession)
    }

    @Test
    fun completeNicknameOnboardingDelegatesToRepository() = runTest {
        val expectedSession = testSession(nickname = "taeyunlive")
        val repository = FakeAuthRepository(
            nicknameResult = Result.success(expectedSession)
        )
        val useCase = CompleteNicknameOnboardingUseCase(repository)

        val result = useCase("taeyunlive")

        assertThat(repository.nicknameRequests).containsExactly("taeyunlive")
        assertThat(result.getOrNull()).isEqualTo(expectedSession)
    }

    @Test
    fun signOutDelegatesToRepository() = runTest {
        val repository = FakeAuthRepository()
        val useCase = SignOutUseCase(repository)

        useCase()

        assertThat(repository.signOutCount).isEqualTo(1)
    }

    private fun testSession(nickname: String?): AuthSession =
        AuthSession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            user = AuthUser(
                id = "user-id",
                nickname = nickname,
                email = "user@example.com",
                onboardingRequired = nickname.isNullOrBlank()
            )
        )

    private class FakeAuthRepository(
        private val signInResult: Result<AuthSession> =
            Result.failure(UnsupportedOperationException()),
        private val nicknameResult: Result<AuthSession> =
            Result.failure(UnsupportedOperationException())
    ) : AuthRepository {
        override val authSession = MutableStateFlow<AuthSession?>(null)
        val signInTokens = mutableListOf<String>()
        val nicknameRequests = mutableListOf<String>()
        var signOutCount = 0
            private set

        override suspend fun signInWithGoogle(idToken: String): Result<AuthSession> {
            signInTokens += idToken
            return signInResult
        }

        override suspend fun completeNicknameOnboarding(nickname: String): Result<AuthSession> {
            nicknameRequests += nickname
            return nicknameResult
        }

        override suspend fun signOut() {
            signOutCount += 1
        }
    }
}
