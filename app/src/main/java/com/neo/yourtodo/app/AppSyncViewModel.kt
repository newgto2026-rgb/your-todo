package com.neo.yourtodo.app

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neo.yourtodo.R
import com.neo.yourtodo.core.domain.usecase.RefreshPersonVisibilityUseCase
import com.neo.yourtodo.core.domain.usecase.RefreshWorkspaceUseCase
import com.neo.yourtodo.core.ui.navigation.WorkspaceSyncUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AppSyncViewModel @Inject constructor(
    private val refreshWorkspaceUseCase: RefreshWorkspaceUseCase,
    private val refreshPersonVisibilityUseCase: RefreshPersonVisibilityUseCase
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(WorkspaceSyncUiState())
    val uiState = mutableUiState.asStateFlow()

    private val mutableSideEffect = MutableSharedFlow<AppSyncSideEffect>()
    val sideEffect = mutableSideEffect.asSharedFlow()

    fun syncWorkspace(notifyUser: Boolean = true) {
        if (uiState.value.isSyncing) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSyncing = true) }
            val workspaceResult = refreshWorkspaceUseCase()
            val personVisibilityResult = refreshPersonVisibilityUseCase()
            mutableUiState.update { it.copy(isSyncing = false) }
            if (!notifyUser) return@launch
            val isFullySynced = workspaceResult.getOrNull()?.isFullySynced == true &&
                personVisibilityResult.isSuccess
            mutableSideEffect.emit(
                AppSyncSideEffect.ShowSnackbar(
                    if (isFullySynced) {
                        R.string.app_sync_success
                    } else {
                        R.string.app_sync_failed
                    }
                )
            )
        }
    }
}

sealed interface AppSyncSideEffect {
    data class ShowSnackbar(@StringRes val messageRes: Int) : AppSyncSideEffect
}
