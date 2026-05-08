package com.neo.yourtodo.feature.auth.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.usecase.CompleteNicknameOnboardingUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.SignInWithGoogleUseCase
import com.neo.yourtodo.core.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    observeAuthSession: ObserveAuthSessionUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val completeNicknameOnboardingUseCase: CompleteNicknameOnboardingUseCase,
    private val signOut: SignOutUseCase
) : ViewModel() {

    private val signInInProgress = MutableStateFlow(false)
    private val nicknameSaveInProgress = MutableStateFlow(false)
    private val error = MutableStateFlow<AuthGateError?>(null)

    val uiState = combine(
        observeAuthSession(),
        signInInProgress,
        nicknameSaveInProgress,
        error
    ) { session, signInProgress, nicknameProgress, currentError ->
        AuthGateUiState(
            destination = when {
                session == null -> AuthGateDestination.SIGNED_OUT
                session.onboardingRequired -> AuthGateDestination.ONBOARDING_REQUIRED
                else -> AuthGateDestination.SIGNED_IN
            },
            signInInProgress = signInProgress,
            nicknameSaveInProgress = nicknameProgress,
            error = currentError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthGateUiState()
    )

    fun signInWithGoogleIdToken(idToken: String) {
        if (signInInProgress.value) return
        viewModelScope.launch {
            signInInProgress.value = true
            error.value = null
            val result = signInWithGoogle(idToken)
            signInInProgress.value = false
            if (result.isFailure) {
                error.value = AuthGateError.SERVER_SIGN_IN_FAILED
            }
        }
    }

    fun completeNicknameOnboarding(nickname: String) {
        if (nicknameSaveInProgress.value) return
        viewModelScope.launch {
            nicknameSaveInProgress.value = true
            error.value = null
            val result = completeNicknameOnboardingUseCase(nickname)
            nicknameSaveInProgress.value = false
            if (result.isFailure) {
                error.value = AuthGateError.NICKNAME_ONBOARDING_FAILED
            }
        }
    }

    fun showGoogleCredentialError() {
        error.value = AuthGateError.GOOGLE_CREDENTIAL_UNAVAILABLE
    }

    fun dismissError() {
        error.value = null
    }

    fun signOutForRetry() {
        viewModelScope.launch {
            signOut()
        }
    }
}
