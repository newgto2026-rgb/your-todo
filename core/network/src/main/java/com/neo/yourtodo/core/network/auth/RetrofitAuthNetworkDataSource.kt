package com.neo.yourtodo.core.network.auth

import javax.inject.Inject

internal class RetrofitAuthNetworkDataSource @Inject constructor(
    private val authApi: AuthApi
) : AuthNetworkDataSource {
    override suspend fun signInWithGoogle(idToken: String): NetworkAuthSession =
        authApi.signInWithGoogle(GoogleSignInRequest(idToken = idToken))

    override suspend fun refreshSession(refreshToken: String): NetworkAuthSession =
        authApi.refreshSession(RefreshTokenRequest(refreshToken = refreshToken))

    override suspend fun completeNicknameOnboarding(
        accessToken: String,
        nickname: String
    ): NetworkAuthUserResponse =
        authApi.completeNicknameOnboarding(
            authorization = "Bearer $accessToken",
            request = NicknameOnboardingRequest(nickname = nickname)
        )
}
