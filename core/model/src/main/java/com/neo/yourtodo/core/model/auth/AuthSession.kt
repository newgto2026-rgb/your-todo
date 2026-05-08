package com.neo.yourtodo.core.model.auth

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: AuthUser
) {
    val onboardingRequired: Boolean
        get() = user.onboardingRequired || user.nickname.isNullOrBlank()
}

data class AuthUser(
    val id: String,
    val nickname: String?,
    val email: String,
    val onboardingRequired: Boolean
)
