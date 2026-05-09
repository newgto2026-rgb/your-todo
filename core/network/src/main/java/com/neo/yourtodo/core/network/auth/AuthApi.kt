package com.neo.yourtodo.core.network.auth

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

internal interface AuthApi {
    @POST("api/auth/google")
    suspend fun signInWithGoogle(
        @Body request: GoogleSignInRequest
    ): NetworkAuthSession

    @POST("api/auth/refresh")
    suspend fun refreshSession(
        @Body request: RefreshTokenRequest
    ): NetworkAuthSession

    @POST("api/users/me/onboarding")
    suspend fun completeNicknameOnboarding(
        @Header("Authorization") authorization: String,
        @Body request: NicknameOnboardingRequest
    ): NetworkAuthUserResponse
}
