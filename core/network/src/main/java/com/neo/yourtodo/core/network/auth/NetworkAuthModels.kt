package com.neo.yourtodo.core.network.auth

import kotlinx.serialization.Serializable

@Serializable
data class GoogleSignInRequest(
    val idToken: String
)

@Serializable
data class NicknameOnboardingRequest(
    val nickname: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class NetworkAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: NetworkAuthUser
)

@Serializable
data class NetworkAuthUserResponse(
    val user: NetworkAuthUser
)

@Serializable
data class NetworkAuthUser(
    val id: String,
    val nickname: String? = null,
    val email: String,
    val onboardingRequired: Boolean
)
