package com.neo.yourtodo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.neo.yourtodo.R
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.ManageDirectAssignmentConsentUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.SignOutUseCase
import com.neo.yourtodo.core.model.friends.DirectAssignmentConsentState
import com.neo.yourtodo.core.model.friends.Friend
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
    private val signOutUseCase: SignOutUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val manageDirectAssignmentConsent: ManageDirectAssignmentConsentUseCase
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
            isSigningOut = operation.isSigningOut,
            isLoadingDirectAssignmentPermissions = operation.isLoadingDirectAssignmentPermissions,
            directAssignmentPermissions = operation.directAssignmentPermissions,
            runningDirectAssignmentActionKey = operation.runningDirectAssignmentActionKey
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

    fun refreshDirectAssignmentPermissions() {
        if (!uiState.value.isSignedIn || operationState.value.isLoadingDirectAssignmentPermissions) return
        viewModelScope.launch {
            operationState.update { it.copy(isLoadingDirectAssignmentPermissions = true) }
            getFriendsUseCase()
                .onSuccess { friends ->
                    operationState.update {
                        it.copy(
                            directAssignmentPermissions = friends.directAssignmentPermissionUiModels(),
                            isLoadingDirectAssignmentPermissions = false
                        )
                    }
                }
                .onFailure {
                    operationState.update { it.copy(isLoadingDirectAssignmentPermissions = false) }
                    _sideEffect.emit(AppProfileMenuSideEffect.DirectAssignmentPermissionFailed)
                }
        }
    }

    fun acceptDirectAssignment(friendUserId: String) {
        runDirectAssignmentPermissionAction(
            actionKey = "accept:$friendUserId",
            successMessageRes = R.string.profile_menu_direct_assignment_allowed
        ) {
            manageDirectAssignmentConsent.accept(friendUserId)
        }
    }

    fun rejectDirectAssignment(friendUserId: String) {
        runDirectAssignmentPermissionAction(
            actionKey = "reject:$friendUserId",
            successMessageRes = R.string.profile_menu_direct_assignment_rejected
        ) {
            manageDirectAssignmentConsent.reject(friendUserId)
        }
    }

    fun revokeDirectAssignment(friendUserId: String) {
        runDirectAssignmentPermissionAction(
            actionKey = "revoke:$friendUserId",
            successMessageRes = R.string.profile_menu_direct_assignment_revoked
        ) {
            manageDirectAssignmentConsent.revoke(friendUserId)
        }
    }

    private fun runDirectAssignmentPermissionAction(
        actionKey: String,
        @StringRes successMessageRes: Int,
        block: suspend () -> Result<*>
    ) {
        if (operationState.value.runningDirectAssignmentActionKey != null) return
        viewModelScope.launch {
            operationState.update { it.copy(runningDirectAssignmentActionKey = actionKey) }
            block()
                .onSuccess {
                    _sideEffect.emit(AppProfileMenuSideEffect.DirectAssignmentPermissionUpdated(successMessageRes))
                    refreshDirectAssignmentPermissions()
                }
                .onFailure {
                    _sideEffect.emit(AppProfileMenuSideEffect.DirectAssignmentPermissionFailed)
                }
            operationState.update { it.copy(runningDirectAssignmentActionKey = null) }
        }
    }
}

data class AppProfileMenuUiState(
    val nickname: String? = null,
    val email: String? = null,
    val isSignedIn: Boolean = false,
    val isSigningOut: Boolean = false,
    val isLoadingDirectAssignmentPermissions: Boolean = false,
    val directAssignmentPermissions: List<ProfileDirectAssignmentPermissionUiModel> = emptyList(),
    val runningDirectAssignmentActionKey: String? = null
) {
    val canCopyNickname: Boolean
        get() = !nickname.isNullOrBlank()
}

data class ProfileDirectAssignmentPermissionUiModel(
    val friendUserId: String,
    val nickname: String,
    val state: DirectAssignmentConsentState
)

private data class AppProfileMenuOperationState(
    val isSigningOut: Boolean = false,
    val isLoadingDirectAssignmentPermissions: Boolean = false,
    val directAssignmentPermissions: List<ProfileDirectAssignmentPermissionUiModel> = emptyList(),
    val runningDirectAssignmentActionKey: String? = null
)

sealed interface AppProfileMenuSideEffect {
    data object SignedOut : AppProfileMenuSideEffect
    data object LogoutFailed : AppProfileMenuSideEffect
    data class DirectAssignmentPermissionUpdated(@StringRes val messageRes: Int) : AppProfileMenuSideEffect
    data object DirectAssignmentPermissionFailed : AppProfileMenuSideEffect
}

internal fun List<Friend>.directAssignmentPermissionUiModels(): List<ProfileDirectAssignmentPermissionUiModel> =
    asSequence()
        .mapNotNull { friend ->
            when (friend.directAssignment.grantedByMe) {
                DirectAssignmentConsentState.PENDING,
                DirectAssignmentConsentState.ACTIVE -> ProfileDirectAssignmentPermissionUiModel(
                    friendUserId = friend.userId,
                    nickname = friend.nickname,
                    state = friend.directAssignment.grantedByMe
                )

                DirectAssignmentConsentState.NONE,
                DirectAssignmentConsentState.REVOKED,
                DirectAssignmentConsentState.EXPIRED -> null
            }
        }
        .sortedWith(
            compareBy<ProfileDirectAssignmentPermissionUiModel> {
                when (it.state) {
                    DirectAssignmentConsentState.PENDING -> 0
                    DirectAssignmentConsentState.ACTIVE -> 1
                    DirectAssignmentConsentState.NONE,
                    DirectAssignmentConsentState.REVOKED,
                    DirectAssignmentConsentState.EXPIRED -> 2
                }
            }.thenBy { it.nickname.lowercase() }
        )
        .toList()
