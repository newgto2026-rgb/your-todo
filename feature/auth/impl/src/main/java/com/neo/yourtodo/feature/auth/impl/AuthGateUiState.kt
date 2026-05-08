package com.neo.yourtodo.feature.auth.impl

data class AuthGateUiState(
    val destination: AuthGateDestination = AuthGateDestination.LOADING,
    val signInInProgress: Boolean = false,
    val nicknameSaveInProgress: Boolean = false,
    val error: AuthGateError? = null
)

enum class AuthGateError {
    GOOGLE_CREDENTIAL_UNAVAILABLE,
    SERVER_SIGN_IN_FAILED,
    NICKNAME_ONBOARDING_FAILED
}
