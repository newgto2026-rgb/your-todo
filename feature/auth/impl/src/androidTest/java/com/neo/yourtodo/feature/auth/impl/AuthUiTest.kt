package com.neo.yourtodo.feature.auth.impl

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.neo.yourtodo.feature.auth.impl.R
import com.neo.yourtodo.feature.auth.impl.ui.components.NicknameOnboardingRequiredScreen
import com.neo.yourtodo.feature.auth.impl.ui.components.SignInScreen
import com.neo.yourtodo.core.designsystem.theme.YourTodoTheme
import org.junit.Rule
import org.junit.Test

class AuthUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signInScreenShowsGoogleButton() {
        composeRule.setContent {
            YourTodoTheme {
                SignInScreen(
                    signInInProgress = false,
                    snackbarHostState = SnackbarHostState(),
                    onGoogleSignInClick = {}
                )
            }
        }

        composeRule.onNodeWithText(stringResource(R.string.auth_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("auth_google_sign_in_button")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun signInScreenDisablesGoogleButtonWhileLoading() {
        composeRule.setContent {
            YourTodoTheme {
                SignInScreen(
                    signInInProgress = true,
                    snackbarHostState = SnackbarHostState(),
                    onGoogleSignInClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("auth_google_sign_in_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("auth_google_sign_in_progress").assertIsDisplayed()
    }

    @Test
    fun onboardingRequiredScreenEnablesNextStepForValidNickname() {
        composeRule.setContent {
            YourTodoTheme {
                NicknameOnboardingRequiredScreen(
                    nicknameSaveInProgress = false,
                    snackbarHostState = SnackbarHostState(),
                    onNicknameSubmit = {},
                    onRetrySignIn = {}
                )
            }
        }

        composeRule.onNodeWithText(stringResource(R.string.auth_onboarding_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("auth_nickname_next_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("auth_nickname_input")
            .assertIsDisplayed()
            .performTextInput("taeyun")
        composeRule.onNodeWithTag("auth_nickname_next_button")
            .assertIsEnabled()
        composeRule.onNodeWithTag("auth_retry_sign_in_button")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    private fun stringResource(id: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
}
