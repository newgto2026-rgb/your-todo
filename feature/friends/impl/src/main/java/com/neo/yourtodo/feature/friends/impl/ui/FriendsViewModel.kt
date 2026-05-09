package com.neo.yourtodo.feature.friends.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.core.domain.error.AuthRequiredException
import com.neo.yourtodo.core.domain.usecase.GetFriendRequestsUseCase
import com.neo.yourtodo.core.domain.usecase.GetFriendsUseCase
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.RemoveFriendUseCase
import com.neo.yourtodo.core.domain.usecase.RespondFriendRequestUseCase
import com.neo.yourtodo.core.domain.usecase.SendFriendRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val getFriends: GetFriendsUseCase,
    private val getFriendRequests: GetFriendRequestsUseCase,
    private val sendFriendRequest: SendFriendRequestUseCase,
    private val respondFriendRequest: RespondFriendRequestUseCase,
    private val removeFriend: RemoveFriendUseCase,
    observeAuthSession: ObserveAuthSessionUseCase
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = mutableUiState

    private val mutableSideEffect = MutableSharedFlow<FriendsSideEffect>()
    val sideEffect: SharedFlow<FriendsSideEffect> = mutableSideEffect

    init {
        viewModelScope.launch {
            observeAuthSession().collect { session ->
                mutableUiState.update { it.copy(profileInitial = session?.user?.nickname) }
            }
        }
        refresh(initial = true)
    }

    fun onAction(action: FriendsAction) {
        when (action) {
            FriendsAction.OnRefresh -> refresh(initial = false)
            FriendsAction.OnToggleAddFriend -> mutableUiState.update {
                it.copy(addFriendExpanded = !it.addFriendExpanded, error = null)
            }
            FriendsAction.OnCloseAddFriend -> mutableUiState.update {
                it.copy(addFriendExpanded = false, nicknameInput = "", error = null)
            }
            is FriendsAction.OnNicknameChanged -> mutableUiState.update {
                it.copy(nicknameInput = action.value.take(MaxNicknameLength), error = null)
            }
            FriendsAction.OnSendRequest -> sendRequest()
            is FriendsAction.OnAcceptRequest -> runMutation(
                key = "accept:${action.requestId}",
                successMessage = FriendsMessage.REQUEST_ACCEPTED
            ) { respondFriendRequest.accept(action.requestId) }
            is FriendsAction.OnDeclineRequest -> runMutation(
                key = "decline:${action.requestId}",
                successMessage = FriendsMessage.REQUEST_DECLINED
            ) { respondFriendRequest.decline(action.requestId) }
            is FriendsAction.OnRemoveFriend -> runMutation(
                key = "remove:${action.friendshipId}",
                successMessage = FriendsMessage.FRIEND_REMOVED
            ) { removeFriend(action.friendshipId) }
            FriendsAction.OnErrorShown -> mutableUiState.update { it.copy(error = null) }
        }
    }

    private fun sendRequest() {
        val nickname = uiState.value.nicknameInput.trim()
        if (nickname.isBlank() || uiState.value.runningActionKey != null) return
        runMutation(
            key = "send",
            successMessage = FriendsMessage.REQUEST_SENT,
            onSuccess = {
                mutableUiState.update {
                    it.copy(
                        nicknameInput = "",
                        addFriendExpanded = false
                    )
                }
            }
        ) {
            sendFriendRequest(nickname)
        }
    }

    private fun refresh(initial: Boolean) {
        if (uiState.value.isRefreshing) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLoading = initial,
                    isRefreshing = !initial,
                    error = null
                )
            }

            val friends = getFriends()
            val incoming = getFriendRequests.incoming()
            val outgoing = getFriendRequests.outgoing()

            if (friends.isSuccess && incoming.isSuccess && outgoing.isSuccess) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        friends = friends.getOrThrow(),
                        incomingRequests = incoming.getOrThrow(),
                        outgoingRequests = outgoing.getOrThrow(),
                        error = null
                    )
                }
            } else {
                val failure = listOf(friends, incoming, outgoing)
                    .firstOrNull { it.isFailure }
                    ?.exceptionOrNull()
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = failure.toUiError()
                    )
                }
            }
        }
    }

    private fun runMutation(
        key: String,
        successMessage: FriendsMessage,
        onSuccess: () -> Unit = {},
        block: suspend () -> Result<Unit>
    ) {
        if (uiState.value.runningActionKey != null) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(runningActionKey = key, error = null) }
            val result = block()
            if (result.isSuccess) {
                onSuccess()
                refreshAfterMutation()
                mutableSideEffect.emit(FriendsSideEffect.ShowSnackbar(successMessage.messageRes))
            } else {
                mutableUiState.update {
                    it.copy(
                        runningActionKey = null,
                        error = result.exceptionOrNull().toUiError()
                    )
                }
            }
        }
    }

    private suspend fun refreshAfterMutation() {
        val friends = getFriends()
        val incoming = getFriendRequests.incoming()
        val outgoing = getFriendRequests.outgoing()
        mutableUiState.update {
            if (friends.isSuccess && incoming.isSuccess && outgoing.isSuccess) {
                it.copy(
                    friends = friends.getOrThrow(),
                    incomingRequests = incoming.getOrThrow(),
                    outgoingRequests = outgoing.getOrThrow(),
                    runningActionKey = null,
                    error = null
                )
            } else {
                val failure = listOf(friends, incoming, outgoing)
                    .firstOrNull { result -> result.isFailure }
                    ?.exceptionOrNull()
                it.copy(
                    runningActionKey = null,
                    error = failure.toUiError()
                )
            }
        }
    }

    private fun Throwable?.toUiError(): FriendsError =
        if (this is AuthRequiredException) FriendsError.AUTH_REQUIRED else FriendsError.NETWORK

    private companion object {
        const val MaxNicknameLength = 12
    }
}
