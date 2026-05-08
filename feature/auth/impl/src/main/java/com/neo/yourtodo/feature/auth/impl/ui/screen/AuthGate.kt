package com.neo.yourtodo.feature.auth.impl.ui.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neo.yourtodo.feature.auth.impl.AuthGateDestination
import com.neo.yourtodo.feature.auth.impl.AuthGateError
import com.neo.yourtodo.feature.auth.impl.AuthGateViewModel
import com.neo.yourtodo.feature.auth.impl.GoogleIdTokenReader
import com.neo.yourtodo.feature.auth.impl.R
import com.neo.yourtodo.feature.auth.impl.ui.components.AuthLoadingScreen
import com.neo.yourtodo.feature.auth.impl.ui.components.NicknameOnboardingRequiredScreen
import com.neo.yourtodo.feature.auth.impl.ui.components.SignInScreen
import kotlinx.coroutines.launch

@Composable
fun AuthGate(
    viewModel: AuthGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val googleIdTokenReader = remember(context) { GoogleIdTokenReader(context) }
    val serverClientId = stringResource(R.string.auth_google_web_client_id)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val credentialErrorMessage = stringResource(R.string.auth_error_google_credential)
    val serverErrorMessage = stringResource(R.string.auth_error_server_sign_in)
    val nicknameErrorMessage = stringResource(R.string.auth_error_nickname_onboarding)

    LaunchedEffect(uiState.error) {
        val message = when (uiState.error) {
            AuthGateError.GOOGLE_CREDENTIAL_UNAVAILABLE -> credentialErrorMessage
            AuthGateError.SERVER_SIGN_IN_FAILED -> serverErrorMessage
            AuthGateError.NICKNAME_ONBOARDING_FAILED -> nicknameErrorMessage
            null -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    when (uiState.destination) {
        AuthGateDestination.LOADING -> AuthLoadingScreen()
        AuthGateDestination.SIGNED_OUT -> SignInScreen(
            signInInProgress = uiState.signInInProgress,
            snackbarHostState = snackbarHostState,
            onGoogleSignInClick = {
                coroutineScope.launch {
                    googleIdTokenReader
                        .readIdToken(
                            context = context,
                            serverClientId = serverClientId
                        )
                        .onSuccess(viewModel::signInWithGoogleIdToken)
                        .onFailure { viewModel.showGoogleCredentialError() }
                }
            }
        )
        AuthGateDestination.ONBOARDING_REQUIRED -> NicknameOnboardingRequiredScreen(
            nicknameSaveInProgress = uiState.nicknameSaveInProgress,
            snackbarHostState = snackbarHostState,
            onNicknameSubmit = viewModel::completeNicknameOnboarding,
            onRetrySignIn = viewModel::signOutForRetry
        )
        AuthGateDestination.SIGNED_IN -> content()
    }
}
