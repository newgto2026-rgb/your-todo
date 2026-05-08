package com.neo.yourtodo.feature.auth.impl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.ui.YourTodoScreenBackground
import com.neo.yourtodo.core.ui.YourTodoWordmark
import com.neo.yourtodo.feature.auth.impl.R

private const val NicknameMaxLength = 12
private val NicknameAllowedPattern = Regex("^[가-힣a-zA-Z0-9_]+$")

@Composable
fun NicknameOnboardingRequiredScreen(
    nicknameSaveInProgress: Boolean,
    snackbarHostState: SnackbarHostState,
    onNicknameSubmit: (String) -> Unit,
    onRetrySignIn: () -> Unit
) {
    var nickname by rememberSaveable { mutableStateOf("") }
    val normalizedNickname = nickname.trim()
    val validation = rememberNicknameValidation(normalizedNickname)

    YourTodoScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(Modifier.height(10.dp))
                YourTodoWordmark(
                    contentDescription = stringResource(R.string.auth_wordmark_content_description),
                    modifier = Modifier
                        .height(42.dp)
                        .width(162.dp)
                )
                Spacer(Modifier.height(36.dp))
                Text(
                    text = stringResource(R.string.auth_onboarding_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF1F2530)
                )
                Text(
                    text = stringResource(R.string.auth_onboarding_description),
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF647286)
                )
                NicknameInputCard(
                    nickname = nickname,
                    validation = validation,
                    onNicknameChange = { input ->
                        if (input.length <= NicknameMaxLength) {
                            nickname = input
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp)
                )
                Button(
                    onClick = { onNicknameSubmit(normalizedNickname) },
                    enabled = validation == NicknameValidation.Valid && !nicknameSaveInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .height(58.dp)
                        .testTag("auth_nickname_next_button"),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF676CB4),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE2E7F2),
                        disabledContentColor = Color(0xFF8A94A3)
                    )
                ) {
                    if (nicknameSaveInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(22.dp)
                                .testTag("auth_nickname_save_progress"),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.auth_onboarding_next),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                NicknameNote(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                )
                OutlinedButton(
                    onClick = onRetrySignIn,
                    enabled = !nicknameSaveInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                        .height(56.dp)
                        .testTag("auth_retry_sign_in_button"),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFFD4DEEC)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.72f),
                        contentColor = Color(0xFF51607A)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.auth_retry_sign_in),
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun NicknameInputCard(
    nickname: String,
    validation: NicknameValidation,
    onNicknameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color(0xFFDCE6F4))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_nickname_label),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF51607A)
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                placeholder = { Text(stringResource(R.string.auth_nickname_placeholder)) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                trailingIcon = {
                    if (validation == NicknameValidation.Valid) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEDF8F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF34A853),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .testTag("auth_nickname_input"),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            Text(
                text = validation.message(),
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = validation.messageColor()
            )
            Row(
                modifier = Modifier.padding(top = 16.dp)
            ) {
                NicknameChip(text = stringResource(R.string.auth_onboarding_search_chip))
                NicknameChip(
                    text = stringResource(R.string.auth_onboarding_share_chip),
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun NicknameChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFEEF4FF),
        border = BorderStroke(1.dp, Color(0xFFD9E5F6))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF4A6697)
        )
    }
}

@Composable
private fun NicknameNote(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFF8F4FF).copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color(0xFFE7DDF8))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF74659E),
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = stringResource(R.string.auth_nickname_note_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF4C4F5D)
                )
                Text(
                    text = stringResource(R.string.auth_nickname_note_description),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6E7480)
                )
            }
        }
    }
}

@Composable
private fun rememberNicknameValidation(nickname: String): NicknameValidation =
    when {
        nickname.isBlank() -> NicknameValidation.Empty
        nickname.length < 2 -> NicknameValidation.TooShort
        !NicknameAllowedPattern.matches(nickname) -> NicknameValidation.InvalidCharacters
        else -> NicknameValidation.Valid
    }

private enum class NicknameValidation {
    Empty,
    TooShort,
    InvalidCharacters,
    Valid
}

@Composable
private fun NicknameValidation.message(): String =
    when (this) {
        NicknameValidation.Empty -> stringResource(R.string.auth_nickname_helper)
        NicknameValidation.TooShort -> stringResource(R.string.auth_nickname_error_too_short)
        NicknameValidation.InvalidCharacters -> stringResource(R.string.auth_nickname_error_invalid_characters)
        NicknameValidation.Valid -> stringResource(R.string.auth_nickname_ready)
    }

private fun NicknameValidation.messageColor(): Color =
    when (this) {
        NicknameValidation.Valid -> Color(0xFF5E9A73)
        NicknameValidation.Empty -> Color(0xFF8A94A3)
        else -> Color(0xFFD45D5D)
    }
