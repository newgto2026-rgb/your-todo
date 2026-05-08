package com.neo.yourtodo.feature.auth.impl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.ui.YourTodoScreenBackground
import com.neo.yourtodo.core.ui.YourTodoWordmark
import com.neo.yourtodo.feature.auth.impl.R

@Composable
fun SignInScreen(
    signInInProgress: Boolean,
    snackbarHostState: SnackbarHostState,
    onGoogleSignInClick: () -> Unit
) {
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
                    text = stringResource(R.string.auth_title),
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF1F2530)
                )
                Text(
                    text = stringResource(R.string.auth_description),
                    modifier = Modifier.padding(top = 10.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF647286)
                )
                AuthValueCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                )
                GoogleSignInButton(
                    signInInProgress = signInInProgress,
                    onGoogleSignInClick = onGoogleSignInClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                        .testTag("auth_google_sign_in_button")
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun AuthValueCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, Color(0xFFDCE6F4))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEEF4FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF676CB4),
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.auth_value_title),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF3C5B8D)
                )
                Text(
                    text = stringResource(R.string.auth_value_description),
                    modifier = Modifier.padding(top = 10.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6E7480)
                )
                Row(
                    modifier = Modifier.padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuthChip(
                        text = stringResource(R.string.auth_sync_chip),
                        containerColor = Color(0xFFEEF4FF),
                        borderColor = Color(0xFFD9E5F6),
                        contentColor = Color(0xFF4A6697)
                    )
                    AuthChip(
                        text = stringResource(R.string.auth_share_chip),
                        containerColor = Color(0xFFF8F4FF),
                        borderColor = Color(0xFFE7DDF8),
                        contentColor = Color(0xFF74659E)
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthChip(
    text: String,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = contentColor
        )
    }
}

@Composable
private fun GoogleSignInButton(
    signInInProgress: Boolean,
    onGoogleSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onGoogleSignInClick,
        enabled = !signInInProgress,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Color(0xFFD4DEEC)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF23262F),
            disabledContainerColor = Color.White.copy(alpha = 0.72f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        if (signInInProgress) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(22.dp)
                    .testTag("auth_google_sign_in_progress"),
                color = Color(0xFF676CB4),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.auth_google_sign_in),
                modifier = Modifier.padding(start = 14.dp),
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
