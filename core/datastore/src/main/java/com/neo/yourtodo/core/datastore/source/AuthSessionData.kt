package com.neo.yourtodo.core.datastore.source

data class AuthSessionData(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val nickname: String?,
    val email: String,
    val onboardingRequired: Boolean
)
