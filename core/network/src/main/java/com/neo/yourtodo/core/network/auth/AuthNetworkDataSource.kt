package com.neo.yourtodo.core.network.auth

interface AuthNetworkDataSource {
    suspend fun signInWithGoogle(idToken: String): NetworkAuthSession
    suspend fun refreshSession(refreshToken: String): NetworkAuthSession
    suspend fun completeNicknameOnboarding(
        accessToken: String,
        nickname: String
    ): NetworkAuthUserResponse
}
