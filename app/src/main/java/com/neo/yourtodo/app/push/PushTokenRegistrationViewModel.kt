package com.neo.yourtodo.app.push

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.neo.yourtodo.core.domain.usecase.ObserveAuthSessionUseCase
import com.neo.yourtodo.core.domain.usecase.RefreshPushTokenRegistrationUseCase
import com.neo.yourtodo.core.domain.usecase.RegisterPushTokenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class PushTokenRegistrationViewModel @Inject constructor(
    observeAuthSession: ObserveAuthSessionUseCase,
    private val registerPushToken: RegisterPushTokenUseCase,
    private val refreshPushTokenRegistration: RefreshPushTokenRegistrationUseCase
) : ViewModel() {
    init {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                viewModelScope.launch {
                    registerPushToken(token)
                }
            }
        viewModelScope.launch {
            observeAuthSession()
                .map { session -> session?.user?.id }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) {
                        refreshPushTokenRegistration()
                    }
                }
        }
    }

    fun keepActive() = Unit
}
