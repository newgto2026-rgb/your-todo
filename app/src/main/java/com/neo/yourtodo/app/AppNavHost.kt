package com.neo.yourtodo.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.neo.yourtodo.R
import com.neo.yourtodo.app.push.PushTokenRegistrationViewModel
import com.neo.yourtodo.app.navigation.ImmediateNavDisplay
import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import com.neo.yourtodo.core.ui.navigation.AppRouteActions
import com.neo.yourtodo.core.ui.navigation.WorkspaceSyncUiState
import com.neo.yourtodo.feature.calendar.api.CalendarDateRoute
import com.neo.yourtodo.feature.friends.api.FriendsIncomingAssignmentRoute
import com.neo.yourtodo.feature.todo.api.TodoEditorRoute
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppNavHost(
    entries: Set<@JvmSuppressWildcards AppFeatureEntry>,
    launchNavigationRequest: AppLaunchNavigationRequest? = null,
    syncViewModel: AppSyncViewModel = hiltViewModel(),
    profileMenuViewModel: AppProfileMenuViewModel = hiltViewModel(),
    pushTokenRegistrationViewModel: PushTokenRegistrationViewModel = hiltViewModel()
) {
    pushTokenRegistrationViewModel.keepActive()
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val backPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val profileMenuUiState by profileMenuViewModel.uiState.collectAsStateWithLifecycle()
    var isProfileMenuOpen by rememberSaveable { mutableStateOf(false) }
    var hasObservedSignedInProfile by rememberSaveable { mutableStateOf(false) }
    val profileNicknameCopiedMessage = stringResource(R.string.profile_menu_copy_success)
    val profileLogoutFailedMessage = stringResource(R.string.profile_menu_logout_failed)
    val profileDirectAssignmentFailedMessage = stringResource(R.string.profile_menu_direct_assignment_failed)
    val profileLinkUnavailableMessage = stringResource(R.string.profile_menu_link_unavailable)
    val privacyPolicyUrl = stringResource(R.string.profile_menu_privacy_policy_url)
    val termsUrl = stringResource(R.string.profile_menu_terms_url)
    val appVersion = remember(context) { context.appVersionName() }
    val navigationGraph = remember(entries) { buildAppNavigationGraph(entries) }
    val orderedTopLevelRoutes = navigationGraph.topLevelRoutes
    val initialLaunchNavigationRequest = remember { launchNavigationRequest }
    val initialStartRoute = remember(navigationGraph.startRoute, initialLaunchNavigationRequest) {
        initialLaunchNavigationRequest?.topLevelRoute ?: navigationGraph.startRoute
    }
    val launchContentRouteState = remember {
        MutableStateFlow(initialLaunchNavigationRequest?.contentRoute)
    }
    val navigationState = rememberAppNavigationState(
        startRoute = initialStartRoute,
        topLevelRoutes = orderedTopLevelRoutes,
        initialTopLevelContentRoute = initialLaunchNavigationRequest?.stackedContentRouteOrNull()
    )
    val navigator = remember(navigationState, navigationGraph.transientRouteTypes) {
        AppNavigator(
            state = navigationState,
            transientRouteTypes = navigationGraph.transientRouteTypes
        )
    }
    val routeActions = remember(navigator, syncViewModel) {
        object : AppRouteActions {
            override val workspaceSyncState: StateFlow<WorkspaceSyncUiState> = syncViewModel.uiState
            override val topLevelLaunchRouteState: StateFlow<NavKey?> = launchContentRouteState

            override fun openTodoEdit(todoId: Long) {
                navigator.navigate(TodoEditorRoute(todoId = todoId, editOnly = true))
            }

            override fun openAssignedTodoEdit(assignedTodoId: String) {
                navigator.navigate(TodoEditorRoute(assignedTodoId = assignedTodoId, editOnly = true))
            }

            override fun openTodoAdd(dueDate: String) {
                navigator.navigate(TodoEditorRoute(dueDate = dueDate, editOnly = true))
            }

            override fun requestWorkspaceSync() {
                syncViewModel.syncWorkspace()
            }

            override fun openProfileMenu() {
                isProfileMenuOpen = true
                profileMenuViewModel.refreshDirectAssignmentPermissions()
            }

            override fun closeCurrentEntry() {
                navigator.closeCurrentEntry()
            }

            override fun setBackBlocked(blocked: Boolean) {
                navigator.setBackBlocked(blocked)
            }
        }
    }
    val currentTopLevelRoute = navigationState.topLevelRoute
    val currentTab = AppTabDestination.fromRoute(currentTopLevelRoute)
    val appEntryProvider = remember(navigationGraph.featureEntries, routeActions) {
        entryProvider {
            navigationGraph.featureEntries.forEach { entry ->
                entry.register(this, routeActions)
            }
        }
    }
    val navEntries = navigationState.toEntries(
        entryProvider = appEntryProvider
    )
    fun openProfileMenuFromLaunchRequest() {
        isProfileMenuOpen = true
        profileMenuViewModel.refreshDirectAssignmentPermissions()
    }
    LaunchedEffect(launchNavigationRequest?.id) {
        val request = launchNavigationRequest ?: return@LaunchedEffect
        if (request.syncOnOpen) {
            syncViewModel.syncWorkspace(notifyUser = false)
        }
        if (request.id == initialLaunchNavigationRequest?.id) return@LaunchedEffect
        navigator.navigate(request.topLevelRoute)
        launchContentRouteState.value = request.contentRoute
        request.stackedContentRouteOrNull()?.let { route ->
            navigator.replaceTopLevelContent(route)
        }
        if (request.openProfileMenuOnLaunch) {
            openProfileMenuFromLaunchRequest()
        }
    }
    LaunchedEffect(initialLaunchNavigationRequest?.id) {
        val request = initialLaunchNavigationRequest ?: return@LaunchedEffect
        if (request.syncOnOpen) {
            syncViewModel.syncWorkspace(notifyUser = false)
        }
        launchContentRouteState.value = request.contentRoute
        if (request.openProfileMenuOnLaunch) {
            openProfileMenuFromLaunchRequest()
        }
    }
    LaunchedEffect(Unit) {
        syncViewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                is AppSyncSideEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(resources.getString(sideEffect.messageRes))
            }
        }
    }
    LaunchedEffect(Unit) {
        profileMenuViewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                AppProfileMenuSideEffect.SignedOut -> {
                    isProfileMenuOpen = false
                }

                AppProfileMenuSideEffect.LogoutFailed ->
                    snackbarHostState.showSnackbar(profileLogoutFailedMessage)

                AppProfileMenuSideEffect.DirectAssignmentPermissionFailed ->
                    snackbarHostState.showSnackbar(profileDirectAssignmentFailedMessage)

                is AppProfileMenuSideEffect.DirectAssignmentPermissionUpdated ->
                    snackbarHostState.showSnackbar(resources.getString(sideEffect.messageRes))
            }
        }
    }
    LaunchedEffect(isProfileMenuOpen, profileMenuUiState.isSignedIn) {
        if (isProfileMenuOpen && profileMenuUiState.isSignedIn) {
            profileMenuViewModel.refreshDirectAssignmentPermissions()
        }
    }
    LaunchedEffect(profileMenuUiState.isSignedIn) {
        if (profileMenuUiState.isSignedIn) {
            hasObservedSignedInProfile = true
        } else if (hasObservedSignedInProfile) {
            isProfileMenuOpen = false
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            AppBottomBar(
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    navigator.navigate(tab.route)
                }
            )
        }
    ) { innerPadding ->
        val activeRoute = navigationState.currentStack.last()
        val activeContentKey = navEntries
            .lastOrNull { entry ->
                entry.contentKey == activeRoute ||
                    entry.contentKey.toString() == activeRoute.toString()
            }
            ?.contentKey
            ?: activeRoute
        ImmediateNavDisplay(
            entries = navEntries,
            activeContentKey = activeContentKey,
            onBack = {
                if (!navigator.goBack()) {
                    (context as? android.app.Activity)?.finish()
                        ?: backPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
        AppProfileMenuDrawer(
            isOpen = isProfileMenuOpen,
            uiState = profileMenuUiState,
            appVersion = appVersion,
            onDismiss = { isProfileMenuOpen = false },
            onCopyNickname = { nickname ->
                context.copyPlainText(
                    label = resources.getString(R.string.profile_menu_copy_nickname),
                    text = nickname
                )
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(profileNicknameCopiedMessage)
                }
            },
            onOpenNotificationSettings = {
                context.openNotificationSettings()
            },
            onOpenAppSettings = {
                context.openAppSettings()
            },
            onOpenPrivacyPolicy = {
                context.openExternalUrlOrNotify(
                    url = privacyPolicyUrl,
                    onUnavailable = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(profileLinkUnavailableMessage)
                        }
                    }
                )
            },
            onOpenTerms = {
                context.openExternalUrlOrNotify(
                    url = termsUrl,
                    onUnavailable = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(profileLinkUnavailableMessage)
                        }
                    }
                )
            },
            onAcceptDirectAssignment = profileMenuViewModel::acceptDirectAssignment,
            onRejectDirectAssignment = profileMenuViewModel::rejectDirectAssignment,
            onRevokeDirectAssignment = profileMenuViewModel::revokeDirectAssignment,
            onLogoutConfirm = profileMenuViewModel::signOut
        )
    }
}

private fun AppLaunchNavigationRequest.stackedContentRouteOrNull() =
    when (contentRoute) {
        is CalendarDateRoute -> null
        is FriendsIncomingAssignmentRoute -> null
        else -> contentRoute
    }

private fun Context.appVersionName(): String =
    runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    }.getOrDefault("1.0")

private fun Context.copyPlainText(label: String, text: String) {
    val clipboard = getSystemService(android.content.ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        appSettingsIntent()
    }
    runCatching { startActivity(intent.withNewTaskFlag()) }
        .onFailure { openAppSettings() }
}

private fun Context.openAppSettings() {
    runCatching {
        startActivity(appSettingsIntent().withNewTaskFlag())
    }
}

private fun Context.openExternalUrlOrNotify(
    url: String,
    onUnavailable: () -> Unit
) {
    if (url.isBlank()) {
        onUnavailable()
        return
    }
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).withNewTaskFlag())
    }.onFailure {
        onUnavailable()
    }
}

private fun Context.appSettingsIntent(): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )

private fun Intent.withNewTaskFlag(): Intent =
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun AppBottomBar(
    selectedTab: AppTabDestination?,
    onTabSelected: (AppTabDestination) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFFFAF7FF),
        tonalElevation = 0.dp
    ) {
        AppTabDestination.tabs.forEach { tab ->
            val selected = selectedTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    AppTabIcon(
                        tab = tab,
                        selected = selected
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color(0xFFECEFF8),
                    selectedTextColor = Color(0xFF303440),
                    unselectedTextColor = Color(0xFF57515F)
                ),
                modifier = Modifier.testTag("app_tab_${tab.name.lowercase()}")
            )
        }
    }
}
