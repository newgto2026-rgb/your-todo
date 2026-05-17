package com.neo.yourtodo.feature.friends.impl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.ui.YourTodoAppHeader
import com.neo.yourtodo.core.ui.YourTodoScreenBackground
import com.neo.yourtodo.core.ui.navigation.WorkspaceSyncUiState
import com.neo.yourtodo.feature.friends.api.FriendsIncomingAssignmentRoute
import com.neo.yourtodo.feature.friends.impl.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun FriendsRouteScreen(
    workspaceSyncState: StateFlow<WorkspaceSyncUiState> = MutableStateFlow(WorkspaceSyncUiState()),
    launchRouteState: StateFlow<NavKey?> = MutableStateFlow(null),
    onWorkspaceSyncClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    initialFriendsRouteKey: Any = Unit,
    initialIncomingAssignmentFriendUserId: String? = null,
    initialIncomingAssignmentFriendNickname: String? = null,
    initialIncomingAssignmentBundleId: String? = null,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncUiState by workspaceSyncState.collectAsStateWithLifecycle()
    val launchRoute by launchRouteState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(error.messageRes))
        viewModel.onAction(FriendsAction.OnErrorShown)
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { sideEffect ->
            when (sideEffect) {
                is FriendsSideEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(context.getString(sideEffect.messageRes))
            }
        }
    }
    LaunchedEffect(initialFriendsRouteKey, initialIncomingAssignmentFriendUserId, initialIncomingAssignmentBundleId) {
        if (initialFriendsRouteKey != Unit) {
            viewModel.onAction(
                FriendsAction.OnOpenIncomingAssignment(
                    friendUserId = initialIncomingAssignmentFriendUserId,
                    friendNickname = initialIncomingAssignmentFriendNickname,
                    bundleId = initialIncomingAssignmentBundleId
                )
            )
        }
    }
    LaunchedEffect(launchRoute) {
        val route = launchRoute as? FriendsIncomingAssignmentRoute ?: return@LaunchedEffect
        viewModel.onAction(
            FriendsAction.OnOpenIncomingAssignment(
                friendUserId = route.friendUserId,
                friendNickname = route.friendNickname,
                bundleId = route.bundleId
            )
        )
    }

    FriendsScreen(
        uiState = uiState,
        isSyncing = syncUiState.isSyncing,
        snackbarHostState = snackbarHostState,
        onSyncClick = onWorkspaceSyncClick,
        onProfileClick = onProfileClick,
        onAction = viewModel::onAction
    )
}

@Composable
private fun FriendsScreen(
    uiState: FriendsUiState,
    isSyncing: Boolean,
    snackbarHostState: SnackbarHostState,
    onSyncClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAction: (FriendsAction) -> Unit
) {
    YourTodoScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0.dp)
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("friends_screen")
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(Modifier.height(10.dp))
                    YourTodoAppHeader(
                        wordmarkContentDescription = stringResource(R.string.friends_app_header_title),
                        profileContentDescription = stringResource(R.string.friends_header_profile_icon),
                        syncContentDescription = stringResource(R.string.friends_refresh),
                        profileInitial = uiState.profileInitial,
                        isSyncing = isSyncing,
                        onSyncClick = onSyncClick,
                        onProfileClick = onProfileClick,
                        syncTestTag = "friends_refresh"
                    )
                }
                item {
                    FriendsHeader(
                        onToggleAdd = { onAction(FriendsAction.OnToggleAddFriend) }
                    )
                }
                if (uiState.addFriendExpanded) {
                    item {
                        FriendAddPanel(
                            nickname = uiState.nicknameInput,
                            canSend = uiState.canSendRequest,
                            isSending = uiState.runningActionKey == "send",
                            onNicknameChanged = { onAction(FriendsAction.OnNicknameChanged(it)) },
                            onSend = { onAction(FriendsAction.OnSendRequest) },
                            onClose = { onAction(FriendsAction.OnCloseAddFriend) }
                        )
                    }
                }
                if (uiState.isLoading) {
                    item {
                        LoadingBlock()
                    }
                } else if (uiState.showFriendsUnavailable) {
                    item {
                        FriendsUnavailableBlock(
                            error = checkNotNull(uiState.friendsSnapshotError),
                            onRetry = { onAction(FriendsAction.OnRefresh) }
                        )
                    }
                } else {
                    uiState.staleSnapshotError?.let { error ->
                        item {
                            FriendsStaleSnapshotBanner(
                                error = error,
                                onRetry = { onAction(FriendsAction.OnRefresh) }
                            )
                        }
                    }
                    if (uiState.incomingRequests.isNotEmpty()) {
                        item {
                            SectionTitle(text = stringResource(R.string.friends_incoming_title))
                        }
                        items(uiState.incomingRequests, key = { it.id }) { request ->
                            IncomingRequestRow(
                                request = request,
                                runningActionKey = uiState.runningActionKey,
                                onAccept = { onAction(FriendsAction.OnAcceptRequest(request.id)) },
                                onDecline = { onAction(FriendsAction.OnDeclineRequest(request.id)) }
                            )
                        }
                    }
                    item {
                        SectionTitle(text = stringResource(R.string.friends_list_title))
                    }
                    if (uiState.showEmptyFriends) {
                        item {
                            EmptyFriendsBlock(
                                onAddClick = { onAction(FriendsAction.OnToggleAddFriend) }
                            )
                        }
                    } else {
                        items(uiState.friends, key = { it.friendshipId }) { friend ->
                            FriendRow(
                                friend = friend,
                                removing = uiState.runningActionKey == "remove:${friend.friendshipId}",
                                togglingAutoAccept =
                                    uiState.runningActionKey == "direct_assignment_opt_in:${friend.userId}",
                                togglingMyTodoVisibility =
                                    uiState.runningActionKey == "todo_visibility:${friend.userId}",
                                observedTodos = uiState.observedTodos(friend.userId),
                                observedTodosExpanded = uiState.isObservedTodosExpanded(friend.userId),
                                myTodosVisibleToFriend = uiState.isMyTodosVisibleTo(friend.userId),
                                onClick = { onAction(FriendsAction.OnFriendClick(friend)) },
                                onSendTodo = { onAction(FriendsAction.OnOpenAssignmentEditor(friend)) },
                                onAutoAcceptChanged = { enabled ->
                                    onAction(FriendsAction.OnSetDirectAssignmentOptIn(friend, enabled))
                                },
                                onMyTodoVisibilityChanged = { enabled ->
                                    onAction(FriendsAction.OnSetMyTodoVisibility(friend, enabled))
                                },
                                onToggleObservedTodos = {
                                    onAction(FriendsAction.OnToggleObservedTodos(friend.userId))
                                },
                                onRemove = { onAction(FriendsAction.OnRemoveFriend(friend.friendshipId)) }
                            )
                        }
                    }
                    if (uiState.outgoingRequests.isNotEmpty()) {
                        item {
                            SectionTitle(text = stringResource(R.string.friends_outgoing_title))
                        }
                        items(uiState.outgoingRequests, key = { it.id }) { request ->
                            OutgoingRequestRow(request)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }

    uiState.selectedFriend?.let { friend ->
        FriendAssignmentMonitorDialog(
            uiState = uiState,
            friend = friend,
            onAction = onAction
        )
    }
    uiState.assignmentTargetFriend?.let { friend ->
        FriendAssignmentEditorSheet(
            uiState = uiState,
            friend = friend,
            onAction = onAction
        )
    }
}

@Composable
private fun FriendsHeader(
    onToggleAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.friends_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1F2530)
            )
            Text(
                text = stringResource(R.string.friends_subtitle),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF647286)
            )
        }
        IconButton(
            onClick = onToggleAdd,
            modifier = Modifier.testTag("friends_add_toggle")
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.friends_add))
        }
    }
}

@Composable
private fun FriendAddPanel(
    nickname: String,
    canSend: Boolean,
    isSending: Boolean,
    onNicknameChanged: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color(0xFFDCE6F4)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.friends_add_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF303440)
                )
                IconButton(
                    onClick = onClose,
                    enabled = !isSending,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("friends_add_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.friends_add_close),
                        tint = Color(0xFF647286)
                    )
                }
            }
            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friends_nickname_input"),
                placeholder = { Text(stringResource(R.string.friends_nickname_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() })
            )
            Button(
                onClick = onSend,
                enabled = canSend && !isSending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("friends_send_request"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF676CB4),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE2E7F2),
                    disabledContentColor = Color(0xFF8A94A3)
                )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.friends_send_request))
                }
            }
        }
    }
}

@Composable
private fun IncomingRequestRow(
    request: FriendRequest,
    runningActionKey: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    FriendSurface(testTag = "friends_incoming_${request.id}") {
        FriendIdentity(
            initial = request.requester.initial,
            nickname = request.requester.nickname,
            subtitle = stringResource(R.string.friends_incoming_subtitle),
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onDecline,
            enabled = runningActionKey == null,
            modifier = Modifier.testTag("friends_decline_${request.id}")
        ) {
            Text(stringResource(R.string.friends_decline))
        }
        Button(
            onClick = onAccept,
            enabled = runningActionKey == null,
            modifier = Modifier.testTag("friends_accept_${request.id}"),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF676CB4))
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.friends_accept), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    removing: Boolean,
    togglingAutoAccept: Boolean,
    togglingMyTodoVisibility: Boolean,
    observedTodos: List<ObservedTodoUiModel>,
    observedTodosExpanded: Boolean,
    myTodosVisibleToFriend: Boolean,
    onClick: () -> Unit,
    onSendTodo: () -> Unit,
    onAutoAcceptChanged: (Boolean) -> Unit,
    onMyTodoVisibilityChanged: (Boolean) -> Unit,
    onToggleObservedTodos: () -> Unit,
    onRemove: () -> Unit
) {
    var showRemoveDialog by remember(friend.friendshipId) { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.friends_remove_dialog_title)) },
            text = { Text(stringResource(R.string.friends_remove_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveDialog = false
                        onRemove()
                    },
                    modifier = Modifier.testTag("friends_remove_confirm_${friend.friendshipId}")
                ) {
                    Text(stringResource(R.string.friends_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringResource(R.string.friends_remove_cancel))
                }
            }
        )
    }

    FriendSurface(testTag = "friends_friend_${friend.userId}") {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FriendIdentity(
                    initial = friend.initial,
                    nickname = friend.nickname,
                    subtitle = null,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 2.dp)
                )
                FriendRowActions(
                    friend = friend,
                    removing = removing,
                    onSendTodo = onSendTodo,
                    onRemove = { showRemoveDialog = true }
                )
            }
            FriendQuickSettingsRow(
                autoAcceptChecked = friend.isAutoAcceptEnabledForMe(),
                autoAcceptEnabled = !removing && !togglingAutoAccept,
                myTodoVisibilityChecked = myTodosVisibleToFriend,
                myTodoVisibilityEnabled = !removing && !togglingMyTodoVisibility,
                onAutoAcceptChanged = onAutoAcceptChanged,
                onMyTodoVisibilityChanged = onMyTodoVisibilityChanged,
                autoAcceptTestTag = "friends_auto_accept_${friend.userId}",
                myTodoVisibilityTestTag = "friends_show_my_todos_${friend.userId}"
            )
            if (observedTodos.isNotEmpty()) {
                ObservedTodosToggleRow(
                    nickname = friend.nickname,
                    count = observedTodos.size,
                    expanded = observedTodosExpanded,
                    enabled = !removing,
                    onClick = onToggleObservedTodos,
                    testTag = "friends_observed_todos_toggle_${friend.userId}"
                )
                if (observedTodosExpanded) {
                    ObservedTodoList(
                        friendUserId = friend.userId,
                        todos = observedTodos
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRowActions(
    friend: Friend,
    removing: Boolean,
    onSendTodo: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onSendTodo,
            enabled = !removing,
            modifier = Modifier
                .height(38.dp)
                .testTag("friends_send_todo_${friend.userId}"),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF1F4FE),
                contentColor = Color(0xFF4F56A6),
                disabledContainerColor = Color(0xFFE2E7F2),
                disabledContentColor = Color(0xFF8A94A3)
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.friends_assignment_send_short),
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
        IconButton(
            onClick = onRemove,
            enabled = !removing,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(14.dp))
                .testTag("friends_remove_${friend.friendshipId}")
        ) {
            if (removing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.friends_remove_confirm),
                    tint = Color(0xFF7A8595)
                )
            }
        }
    }
}

@Composable
private fun FriendQuickSettingsRow(
    autoAcceptChecked: Boolean,
    autoAcceptEnabled: Boolean,
    myTodoVisibilityChecked: Boolean,
    myTodoVisibilityEnabled: Boolean,
    onAutoAcceptChanged: (Boolean) -> Unit,
    onMyTodoVisibilityChanged: (Boolean) -> Unit,
    autoAcceptTestTag: String,
    myTodoVisibilityTestTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FriendQuickSettingChip(
            title = stringResource(R.string.friends_auto_accept_label),
            checked = autoAcceptChecked,
            enabled = autoAcceptEnabled,
            onCheckedChange = onAutoAcceptChanged,
            testTag = autoAcceptTestTag,
            modifier = Modifier.weight(1f)
        )
        FriendQuickSettingChip(
            title = stringResource(R.string.friends_my_todo_visibility_label),
            checked = myTodoVisibilityChecked,
            enabled = myTodoVisibilityEnabled,
            onCheckedChange = onMyTodoVisibilityChanged,
            testTag = myTodoVisibilityTestTag,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FriendQuickSettingChip(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.58f))
            .border(1.dp, if (checked) Color(0xFFD8E2F4) else Color(0xFFE9EEF6), shape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .testTag(testTag)
            .padding(start = 10.dp, top = 7.dp, end = 8.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (enabled) Color(0xFF303440) else Color(0xFF9AA2AE),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        CompactSwitch(
            checked = checked,
            enabled = enabled
        )
    }
}

@Composable
private fun CompactSwitch(
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = when {
        !enabled -> Color(0xFFE4E8EF)
        checked -> Color(0xFF6570B8)
        else -> Color(0xFFE5E0EA)
    }
    val thumbColor = if (enabled) Color.White else Color(0xFFF5F6F8)
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
private fun ObservedTodosToggleRow(
    nickname: String,
    count: Int,
    expanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.66f))
            .border(1.dp, Color(0xFFE2E8F2), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.friends_observed_todos_affordance,
                count,
                nickname,
                count
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = Color(0xFF303440),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = Color(0xFF526585),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ObservedTodoList(
    friendUserId: String,
    todos: List<ObservedTodoUiModel>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("friends_friend_observed_todos_$friendUserId"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        todos.forEach { todo ->
            ObservedTodoRow(todo = todo)
        }
    }
}

@Composable
private fun ObservedTodoRow(todo: ObservedTodoUiModel) {
    val locale = Locale.getDefault()
    val scheduleLabel = todo.scheduleLabel(locale)
    val statusLabel = if (todo.isDone) stringResource(R.string.friends_assignment_status_done) else null
    val metaText = listOfNotNull(scheduleLabel, statusLabel).joinToString(" · ")
    val priorityColor = observedPriorityColor(todo.priority)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.8f))
            .border(1.dp, Color(0xFFE7EDF5), RoundedCornerShape(14.dp))
            .testTag("friends_observed_todo_${todo.id}")
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(
            modifier = Modifier
                .size(width = 4.dp, height = 34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(priorityColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (todo.isDone) Color(0xFF2D3338).copy(alpha = 0.52f) else Color(0xFF2D3338),
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (metaText.isNotBlank()) {
                Text(
                    text = metaText,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5A6065),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun ObservedTodoUiModel.scheduleLabel(locale: Locale): String? {
    val dateLabel = dueDate?.formatObservedDate(locale)
    val timeLabel = dueTimeMinutes?.let { minutes ->
        "%02d:%02d".format(minutes / 60, minutes % 60)
    }
    return listOfNotNull(dateLabel, timeLabel)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")
}

private fun LocalDate.formatObservedDate(locale: Locale): String {
    val pattern = if (locale.language == Locale.KOREAN.language) {
        "M월 d일"
    } else {
        "MMM d"
    }
    return format(DateTimeFormatter.ofPattern(pattern, locale))
}

private fun observedPriorityColor(priority: TodoPriority): Color = when (priority) {
    TodoPriority.LOW -> Color(0xFF6FA58C)
    TodoPriority.MEDIUM -> Color(0xFF6F86C9)
    TodoPriority.HIGH -> Color(0xFFC76B7D)
}

@Composable
private fun OutgoingRequestRow(request: FriendRequest) {
    FriendSurface(testTag = "friends_outgoing_${request.id}") {
        FriendIdentity(
            initial = request.receiver.initial,
            nickname = request.receiver.nickname,
            subtitle = stringResource(R.string.friends_outgoing_pending),
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF8A94A3))
    }
}
