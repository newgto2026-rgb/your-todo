package com.neo.yourtodo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AppProfileMenuViewModel @Inject constructor(
    observeAuthSession: ObserveAuthSessionUseCase,
    private val signOutUseCase: SignOutUseCase
) : ViewModel() {
    private val operationState = MutableStateFlow(AppProfileMenuOperationState())
    private val _sideEffect = MutableSharedFlow<AppProfileMenuSideEffect>()
    val sideEffect = _sideEffect.asSharedFlow()

    val uiState: StateFlow<AppProfileMenuUiState> = combine(
        observeAuthSession(),
        operationState
    ) { session, operation ->
        if (session == null) {
            return@combine AppProfileMenuUiState()
        }
        AppProfileMenuUiState(
            nickname = session.user.nickname?.trim()?.takeIf(String::isNotBlank),
            email = session.user.email?.trim()?.takeIf(String::isNotBlank),
            isSignedIn = true,
            isSigningOut = operation.isSigningOut
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppProfileMenuUiState()
    )

    fun signOut() {
        if (operationState.value.isSigningOut) return
        viewModelScope.launch {
            operationState.update { it.copy(isSigningOut = true) }
            runCatching { signOutUseCase() }
                .onSuccess {
                    _sideEffect.emit(AppProfileMenuSideEffect.SignedOut)
                }
                .onFailure {
                    _sideEffect.emit(AppProfileMenuSideEffect.LogoutFailed)
                }
            operationState.update { it.copy(isSigningOut = false) }
        }
    }

}

data class AppProfileMenuUiState(
    val nickname: String? = null,
    val email: String? = null,
    val isSignedIn: Boolean = false,
    val isSigningOut: Boolean = false
) {
    val canCopyNickname: Boolean
        get() = !nickname.isNullOrBlank()
}

private data class AppProfileMenuOperationState(
    val isSigningOut: Boolean = false
)

sealed interface AppProfileMenuSideEffect {
    data object SignedOut : AppProfileMenuSideEffect
    data object LogoutFailed : AppProfileMenuSideEffect
}
