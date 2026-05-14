package com.neo.yourtodo.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neo.yourtodo.R

@Composable
fun AppProfileMenuDrawer(
    isOpen: Boolean,
    uiState: AppProfileMenuUiState,
    appVersion: String,
    onDismiss: () -> Unit,
    onCopyNickname: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onLogoutConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val nickname = uiState.nickname ?: stringResource(R.string.profile_menu_nickname_missing)
    val email = uiState.email ?: stringResource(R.string.profile_menu_email_missing)

    if (isOpen) {
        BackHandler(enabled = !showLogoutConfirm) {
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .testTag("profile_menu_scrim")
                    .clickable(enabled = !uiState.isSigningOut) { onDismiss() }
            )
            AnimatedVisibility(
                visible = isOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    color = Color.White.copy(alpha = 0.98f),
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.86f)
                        .widthIn(max = 360.dp)
                        .testTag("profile_menu_drawer")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.profile_menu_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF303440),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismiss,
                                enabled = !uiState.isSigningOut,
                                modifier = Modifier.testTag("profile_menu_close")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.profile_menu_close)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        ProfileSummary(
                            nickname = nickname,
                            email = email,
                            profileInitial = uiState.nickname?.firstOrNull()?.uppercaseChar()?.toString()
                        )

                        Spacer(Modifier.height(18.dp))

                        ProfileMenuAction(
                            icon = Icons.Default.ContentCopy,
                            title = stringResource(R.string.profile_menu_copy_nickname),
                            subtitle = stringResource(R.string.profile_menu_copy_nickname_subtitle),
                            enabled = uiState.canCopyNickname && !uiState.isSigningOut,
                            testTag = "profile_menu_copy_nickname",
                            onClick = {
                                uiState.nickname?.let(onCopyNickname)
                            }
                        )
                        ProfileMenuAction(
                            icon = Icons.Default.Notifications,
                            title = stringResource(R.string.profile_menu_notification_settings),
                            subtitle = stringResource(R.string.profile_menu_notification_settings_subtitle),
                            enabled = !uiState.isSigningOut,
                            testTag = "profile_menu_notification_settings",
                            onClick = onOpenNotificationSettings
                        )
                        ProfileMenuAction(
                            icon = Icons.Default.Settings,
                            title = stringResource(R.string.profile_menu_app_settings),
                            subtitle = stringResource(R.string.profile_menu_app_settings_subtitle),
                            enabled = !uiState.isSigningOut,
                            testTag = "profile_menu_app_settings",
                            onClick = onOpenAppSettings
                        )

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFE6E8EF))
                        Spacer(Modifier.height(12.dp))

                        ProfileMenuAction(
                            icon = Icons.Default.Policy,
                            title = stringResource(R.string.profile_menu_privacy_policy),
                            enabled = !uiState.isSigningOut,
                            testTag = "profile_menu_privacy_policy",
                            onClick = onOpenPrivacyPolicy
                        )
                        ProfileMenuAction(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.profile_menu_terms),
                            enabled = !uiState.isSigningOut,
                            testTag = "profile_menu_terms",
                            onClick = onOpenTerms
                        )
                        ProfileMenuVersionRow(appVersion = appVersion)

                        Spacer(Modifier.weight(1f))
                        HorizontalDivider(color = Color(0xFFE6E8EF))
                        Spacer(Modifier.height(8.dp))

                        ProfileMenuAction(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            title = stringResource(R.string.profile_menu_logout),
                            enabled = !uiState.isSigningOut,
                            destructive = true,
                            testTag = "profile_menu_logout",
                            onClick = { showLogoutConfirm = true }
                        )
                    }
                }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSigningOut) showLogoutConfirm = false
            },
            title = { Text(stringResource(R.string.profile_menu_logout_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.profile_menu_logout_confirm_message))
                    if (uiState.isSigningOut) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("profile_menu_logout_progress"),
                            strokeWidth = 2.dp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onLogoutConfirm,
                    enabled = !uiState.isSigningOut,
                    modifier = Modifier.testTag("profile_menu_logout_confirm")
                ) {
                    Text(
                        text = stringResource(R.string.profile_menu_logout),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirm = false },
                    enabled = !uiState.isSigningOut
                ) {
                    Text(stringResource(R.string.profile_menu_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProfileSummary(
    nickname: String,
    email: String,
    profileInitial: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_menu_summary"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F3A56)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profileInitial ?: "?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = nickname,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF303440),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("profile_menu_nickname")
            )
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF69707D),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("profile_menu_email")
            )
        }
    }
}

@Composable
private fun ProfileMenuAction(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false
) {
    val contentColor = when {
        !enabled -> Color(0xFFADB3BE)
        destructive -> MaterialTheme.colorScheme.error
        else -> Color(0xFF303440)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) Color(0xFF69707D) else Color(0xFFADB3BE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuVersionRow(appVersion: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp)
            .testTag("profile_menu_app_version"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Color(0xFF69707D),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = stringResource(R.string.profile_menu_app_version),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF303440),
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        )
        Text(
            text = stringResource(R.string.profile_menu_app_version_value, appVersion),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF69707D),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
