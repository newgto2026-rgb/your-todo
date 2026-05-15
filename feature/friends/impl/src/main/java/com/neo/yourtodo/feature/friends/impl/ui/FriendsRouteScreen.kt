package com.neo.yourtodo.feature.friends.impl.ui

import android.app.TimePickerDialog
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentSummary
import com.neo.yourtodo.core.ui.YourTodoAppHeader
import com.neo.yourtodo.core.ui.YourTodoScreenBackground
import com.neo.yourtodo.core.ui.navigation.WorkspaceSyncUiState
import com.neo.yourtodo.feature.friends.api.FriendsIncomingAssignmentRoute
import com.neo.yourtodo.feature.friends.impl.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
                } else {
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
                    if (uiState.friends.isEmpty()) {
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
                                onClick = { onAction(FriendsAction.OnFriendClick(friend)) },
                                onSendTodo = { onAction(FriendsAction.OnOpenAssignmentEditor(friend)) },
                                onAutoAcceptChanged = { enabled ->
                                    onAction(FriendsAction.OnSetDirectAssignmentOptIn(friend, enabled))
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
    onClick: () -> Unit,
    onSendTodo: () -> Unit,
    onAutoAcceptChanged: (Boolean) -> Unit,
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

    FriendSurface(
        testTag = "friends_friend_${friend.userId}",
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FriendIdentity(
                initial = friend.initial,
                nickname = friend.nickname,
                subtitle = stringResource(
                    if (friend.isAutoAcceptEnabledForMe()) {
                        R.string.friends_auto_accept_enabled_subtitle
                    } else {
                        R.string.friends_auto_accept_disabled_subtitle
                    }
                )
            )
            AutoAcceptSwitchRow(
                checked = friend.isAutoAcceptEnabledForMe(),
                enabled = !removing && !togglingAutoAccept,
                onCheckedChange = onAutoAcceptChanged,
                testTag = "friends_auto_accept_${friend.userId}"
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            TextButton(
                onClick = onSendTodo,
                enabled = !removing,
                modifier = Modifier.testTag("friends_send_todo_${friend.userId}")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(
                    text = stringResource(R.string.friends_assignment_open_editor),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            IconButton(
                onClick = { showRemoveDialog = true },
                enabled = !removing,
                modifier = Modifier.testTag("friends_remove_${friend.friendshipId}")
            ) {
                if (removing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.friends_remove_confirm))
                }
            }
        }
    }
}

@Composable
private fun AutoAcceptSwitchRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
        Text(
            text = stringResource(R.string.friends_auto_accept_label),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) Color(0xFF4E5D73) else Color(0xFF9AA2AE)
        )
    }
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

@Composable
private fun FriendAssignmentMonitorDialog(
    uiState: FriendsUiState,
    friend: Friend,
    onAction: (FriendsAction) -> Unit
) {
    val detail = uiState.assignmentDetail

    AlertDialog(
        onDismissRequest = { onAction(FriendsAction.OnCloseFriendDetail) },
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .testTag("friends_assignment_monitor_dialog"),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color(0xFFF7F8FC),
        shape = RoundedCornerShape(28.dp),
        title = {
            FriendIdentity(
                initial = friend.initial,
                nickname = stringResource(R.string.friends_assignment_dialog_title, friend.nickname),
                subtitle = stringResource(R.string.friends_assignment_monitor_subtitle)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (uiState.friendDetailLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    AssignmentDetailLoadingBlock()
                } else {
                    AssignmentSummaryBlock(
                        sent = uiState.friendAssignmentSummary?.sent,
                        received = uiState.friendAssignmentSummary?.received
                    )
                    AssignmentHistoryToggle(
                        showHistory = detail.showHistory,
                        onToggle = { onAction(FriendsAction.OnToggleAssignmentHistory) }
                    )
                    PendingAssignmentDecisionBlock(
                        items = detail.pendingReceivedItems,
                        selectedCount = detail.pendingSelectedCount,
                        totalCount = detail.pendingTotalCount,
                        allSelected = detail.isAllPendingSelected,
                        hasSelection = detail.hasPendingSelection,
                        running = detail.isDecisionRunning,
                        onToggle = { onAction(FriendsAction.OnTogglePendingAssignment(it)) },
                        onToggleAll = { onAction(FriendsAction.OnToggleAllPendingAssignments) },
                        onAcceptSelected = { onAction(FriendsAction.OnAcceptSelectedAssignments) },
                        onRejectSelected = { onAction(FriendsAction.OnRejectSelectedAssignments) }
                    )
                    if (detail.showHistory) {
                        AssignmentPreviewList(
                            title = stringResource(R.string.friends_assignment_sent_history_title),
                            caption = stringResource(R.string.friends_assignment_sent_history_caption),
                            items = detail.sentHistoryItems,
                            expanded = FriendAssignmentSection.SENT_HISTORY in uiState.expandedAssignmentSections,
                            onToggleExpanded = {
                                onAction(FriendsAction.OnToggleAssignmentSection(FriendAssignmentSection.SENT_HISTORY))
                            },
                            expandTestTag = "friends_assignment_expand_sent_history",
                            accentColor = Color(0xFF6771C7),
                            containerColor = Color(0xFFF0F3FF)
                        )
                        AssignmentPreviewList(
                            title = stringResource(R.string.friends_assignment_received_history_title),
                            caption = stringResource(R.string.friends_assignment_received_history_caption),
                            items = detail.receivedHistoryItems,
                            expanded = FriendAssignmentSection.RECEIVED_HISTORY in uiState.expandedAssignmentSections,
                            onToggleExpanded = {
                                onAction(FriendsAction.OnToggleAssignmentSection(FriendAssignmentSection.RECEIVED_HISTORY))
                            },
                            expandTestTag = "friends_assignment_expand_received_history",
                            accentColor = Color(0xFF4B9A82),
                            containerColor = Color(0xFFEFF8F4)
                        )
                    } else {
                        AssignmentPreviewList(
                            title = stringResource(R.string.friends_assignment_sent_title),
                            caption = stringResource(R.string.friends_assignment_sent_caption),
                            items = detail.sentItems,
                            expanded = FriendAssignmentSection.SENT in uiState.expandedAssignmentSections,
                            onToggleExpanded = {
                                onAction(FriendsAction.OnToggleAssignmentSection(FriendAssignmentSection.SENT))
                            },
                            expandTestTag = "friends_assignment_expand_sent",
                            accentColor = Color(0xFF6771C7),
                            containerColor = Color(0xFFF0F3FF)
                        )
                        AssignmentPreviewList(
                            title = stringResource(R.string.friends_assignment_received_title),
                            caption = stringResource(R.string.friends_assignment_received_caption),
                            items = detail.activeReceivedItems,
                            expanded = FriendAssignmentSection.RECEIVED in uiState.expandedAssignmentSections,
                            onToggleExpanded = {
                                onAction(FriendsAction.OnToggleAssignmentSection(FriendAssignmentSection.RECEIVED))
                            },
                            expandTestTag = "friends_assignment_expand_received",
                            accentColor = Color(0xFF4B9A82),
                            containerColor = Color(0xFFEFF8F4)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(FriendsAction.OnCloseFriendDetail) },
                modifier = Modifier.testTag("friends_assignment_dialog_close")
            ) {
                Text(stringResource(R.string.friends_dialog_close))
            }
        }
    )
}

@Composable
private fun AssignmentDetailLoadingBlock() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .testTag("friends_assignment_loading"),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color(0xFFE1E7F0))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = Color(0xFF6771C7)
            )
            Text(
                text = stringResource(R.string.friends_assignment_loading),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF647286)
            )
        }
    }
}

@Composable
private fun AssignmentHistoryToggle(
    showHistory: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color(0xFFE1E7F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.friends_assignment_monitor_window_caption),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF647286)
            )
            TextButton(
                onClick = onToggle,
                modifier = Modifier.testTag("friends_assignment_history_toggle")
            ) {
                Text(
                    text = stringResource(
                        if (showHistory) {
                            R.string.friends_assignment_show_monitoring
                        } else {
                            R.string.friends_assignment_show_history
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun AssignmentSummaryBlock(
    sent: AssignmentSummary?,
    received: AssignmentSummary?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssignmentMetric(
            label = stringResource(R.string.friends_assignment_sent_metric),
            count = sent?.totalCount ?: 0,
            doneCount = sent?.doneCount ?: 0,
            modifier = Modifier.weight(1f)
        )
        AssignmentMetric(
            label = stringResource(R.string.friends_assignment_received_metric),
            count = received?.totalCount ?: 0,
            doneCount = received?.doneCount ?: 0,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AssignmentMetric(
    label: String,
    count: Int,
    doneCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF4F7FC)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF647286)
            )
            Text(
                text = stringResource(R.string.friends_assignment_metric_value, doneCount, count),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF303440)
            )
        }
    }
}

@Composable
private fun PendingAssignmentDecisionBlock(
    items: List<AssignmentTodoUiModel>,
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    hasSelection: Boolean,
    running: Boolean,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onAcceptSelected: () -> Unit,
    onRejectSelected: () -> Unit
) {
    if (items.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFF7E8),
        border = BorderStroke(1.dp, Color(0xFFFFDCA8))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.friends_assignment_pending_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF303440)
                    )
                    Text(
                        text = stringResource(R.string.friends_assignment_selected_count, selectedCount, totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8A5F1F)
                    )
                }
                TextButton(
                    onClick = onToggleAll,
                    enabled = !running,
                    modifier = Modifier.testTag("friends_assignment_pending_select_all")
                ) {
                    Text(
                        text = if (allSelected) {
                            stringResource(R.string.friends_assignment_unselect_all)
                        } else {
                            stringResource(R.string.friends_assignment_select_all)
                        }
                    )
                }
            }
            items.forEach { item ->
                PendingAssignmentRow(
                    item = item,
                    enabled = !running,
                    onToggle = { onToggle(item.id) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onRejectSelected,
                    enabled = hasSelection && !running,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("friends_assignment_reject_selected")
                ) {
                    Text(stringResource(R.string.friends_assignment_reject_selected))
                }
                Button(
                    onClick = onAcceptSelected,
                    enabled = hasSelection && !running,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("friends_assignment_accept_selected"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF676CB4))
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(stringResource(R.string.friends_assignment_accept_selected))
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingAssignmentRow(
    item: AssignmentTodoUiModel,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (item.selected) Color(0xFFFFFCF5) else Color.White.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, if (item.selected) Color(0xFFFFC977) else Color(0xFFE2E8F2)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("friends_assignment_pending_${item.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                modifier = Modifier.testTag("friends_assignment_pending_check_${item.id}")
            )
            AssignmentTodoSummary(
                item = item,
                accentColor = Color(0xFFE2A23A),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AssignmentPreviewList(
    title: String,
    caption: String,
    items: List<AssignmentTodoUiModel>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    expandTestTag: String,
    accentColor: Color,
    containerColor: Color
) {
    val visibleItems = if (expanded) {
        items
    } else {
        items.take(AssignmentPreviewCollapsedCount)
    }
    val hiddenCount = items.size - AssignmentPreviewCollapsedCount
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 156.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 30.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                )
                Column(modifier = Modifier.padding(start = 9.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color(0xFF303440)
                    )
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF627083)
                    )
                }
            }
            if (items.isEmpty()) {
                EmptyAssignmentTodoCard(accentColor = accentColor)
            } else {
                visibleItems.forEach { item ->
                    AssignmentTodoCard(
                        item = item,
                        accentColor = accentColor
                    )
                }
                if (items.size > AssignmentPreviewCollapsedCount) {
                    TextButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag(expandTestTag)
                    ) {
                        Text(
                            text = if (expanded) {
                                stringResource(R.string.friends_assignment_collapse)
                            } else {
                                pluralStringResource(
                                    R.plurals.friends_assignment_expand_more,
                                    hiddenCount,
                                    hiddenCount
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAssignmentTodoCard(
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.86f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor.copy(alpha = 0.28f))
            )
            Text(
                text = stringResource(R.string.friends_assignment_empty),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF7A8595)
            )
        }
    }
}

@Composable
private fun AssignmentTodoCard(
    item: AssignmentTodoUiModel,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, Color.White)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 46.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor.copy(alpha = 0.8f))
            )
            AssignmentTodoSummary(
                item = item,
                accentColor = accentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AssignmentTodoSummary(
    item: AssignmentTodoUiModel,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val progressFraction = item.progressPercent.coerceIn(0, 100) / 100f
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF303440),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AssignmentModeChip(labelRes = item.assignmentModeLabelRes, mode = item.assignmentMode)
            AssignmentStatusChip(labelRes = item.statusLabelRes, style = item.statusStyle)
        }
        if (item.showProgress) {
            Text(
                text = stringResource(R.string.friends_assignment_item_summary, item.progressPercent),
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF647286)
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFE2E8F1))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .height(6.dp)
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
private fun AssignmentModeChip(
    @StringRes labelRes: Int,
    mode: AssignmentMode
) {
    val chipColor = when (mode) {
        AssignmentMode.REQUEST -> Color(0xFFF1F4F8)
        AssignmentMode.DIRECT -> Color(0xFFE7F4EE)
    }
    val textColor = when (mode) {
        AssignmentMode.REQUEST -> Color(0xFF5F6975)
        AssignmentMode.DIRECT -> Color(0xFF2F735B)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = chipColor
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
private fun AssignmentStatusChip(
    @StringRes labelRes: Int,
    style: AssignmentTodoStatusStyle
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = style.statusChipColor()
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF303440)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendAssignmentEditorSheet(
    uiState: FriendsUiState,
    friend: Friend,
    onAction: (FriendsAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = { onAction(FriendsAction.OnCloseAssignmentEditor) },
        sheetState = sheetState,
        containerColor = Color(0xFFF6F7FB),
        modifier = Modifier.testTag("friends_assignment_editor_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.friends_assignment_editor_title, friend.nickname),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF323640)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEAECF3),
                    onClick = { onAction(FriendsAction.OnCloseAssignmentEditor) },
                    modifier = Modifier.testTag("friends_assignment_editor_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = Color(0xFF5C6170)
                    )
                }
            }

            AssignmentEditorForm(uiState = uiState, onAction = onAction)

            AssignmentSendModeHint(assignmentMode = uiState.assignmentMode)

            DraftItemsRow(
                items = uiState.assignmentDraftItems,
                onRemove = { onAction(FriendsAction.OnRemoveAssignmentDraft(it)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = { onAction(FriendsAction.OnAddAssignmentDraft) },
                    enabled = uiState.assignmentTitleInput.trim().isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("friends_assignment_add_draft")
                ) {
                    Text(stringResource(R.string.friends_assignment_add_draft))
                }
                Button(
                    onClick = { onAction(FriendsAction.OnSendAssignmentNow) },
                    enabled = uiState.assignmentTitleInput.trim().isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("friends_assignment_send_now"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF676CB4))
                ) {
                    Text(
                        stringResource(
                            if (uiState.assignmentMode == AssignmentMode.DIRECT) {
                                R.string.friends_assignment_direct_send_now
                            } else {
                                R.string.friends_assignment_send_now
                            }
                        )
                    )
                }
            }
            Button(
                onClick = { onAction(FriendsAction.OnSendAssignmentDrafts) },
                enabled = uiState.assignmentDraftItems.isNotEmpty() ||
                    uiState.assignmentTitleInput.trim().isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("friends_assignment_send_batch"),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303440))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(
                        if (uiState.assignmentMode == AssignmentMode.DIRECT) {
                            R.string.friends_assignment_direct_send_batch
                        } else {
                            R.string.friends_assignment_send_batch
                        }
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AssignmentSendModeHint(
    assignmentMode: AssignmentMode
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color(0xFFE1E7F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(
                    if (assignmentMode == AssignmentMode.DIRECT) {
                        R.string.friends_assignment_mode_direct_title
                    } else {
                        R.string.friends_assignment_mode_request_title
                    }
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF7A7F8C)
            )
            Text(
                text = stringResource(
                    if (assignmentMode == AssignmentMode.DIRECT) {
                        R.string.friends_assignment_mode_direct_description
                    } else {
                        R.string.friends_assignment_mode_request_description
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF647286)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentEditorForm(
    uiState: FriendsUiState,
    onAction: (FriendsAction) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dueTimeEnabled = uiState.assignmentDueDateInput.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.friends_assignment_task_label),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF7A7F8C)
        )
        TextField(
            value = uiState.assignmentTitleInput,
            onValueChange = { onAction(FriendsAction.OnAssignmentTitleChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .testTag("friends_assignment_title"),
            placeholder = { Text(stringResource(R.string.friends_assignment_title_placeholder)) },
            singleLine = false,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = editorTextFieldColors()
        )
        Surface(
            onClick = { showDatePicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("friends_assignment_due_date"),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFEBEDF4)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = Color(0xFF5C6170)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = uiState.assignmentDueDateInput.ifBlank {
                        stringResource(R.string.friends_assignment_due_date_placeholder)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (uiState.assignmentDueDateInput.isBlank()) {
                        Color(0xFF8E94A3)
                    } else {
                        Color(0xFF2F3441)
                    }
                )
                if (uiState.assignmentDueDateInput.isNotBlank()) {
                    TextButton(
                        onClick = { onAction(FriendsAction.OnAssignmentDueDateChanged("")) },
                        modifier = Modifier.testTag("friends_assignment_due_date_clear")
                    ) {
                        Text(stringResource(R.string.friends_assignment_due_date_clear))
                    }
                }
            }
        }
        Surface(
            onClick = {
                val minutes = editorDueTimeTextToMinutes(uiState.assignmentDueTimeInput)
                val initialHour = minutes?.div(60) ?: 9
                val initialMinute = minutes?.rem(60) ?: 0
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onAction(FriendsAction.OnAssignmentDueTimeChanged(editorMinutesToDueTimeText(hour * 60 + minute)))
                    },
                    initialHour,
                    initialMinute,
                    true
                ).show()
            },
            enabled = dueTimeEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("friends_assignment_due_time"),
            shape = RoundedCornerShape(14.dp),
            color = if (dueTimeEnabled) Color(0xFFEBEDF4) else Color(0xFFF2F3F6)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = if (dueTimeEnabled) Color(0xFF5C6170) else Color(0xFFA7ACB8)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = when {
                        uiState.assignmentDueTimeInput.isNotBlank() -> uiState.assignmentDueTimeInput
                        dueTimeEnabled -> stringResource(R.string.friends_assignment_due_time_placeholder)
                        else -> stringResource(R.string.friends_assignment_due_time_disabled)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (uiState.assignmentDueTimeInput.isBlank()) {
                        Color(0xFF8E94A3)
                    } else {
                        Color(0xFF2F3441)
                    }
                )
                if (uiState.assignmentDueTimeInput.isNotBlank()) {
                    TextButton(
                        onClick = { onAction(FriendsAction.OnAssignmentDueTimeChanged("")) },
                        modifier = Modifier.testTag("friends_assignment_due_time_clear")
                    ) {
                        Text(stringResource(R.string.friends_assignment_due_date_clear))
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.friends_assignment_priority_label),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF7A7F8C),
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TodoPriority.entries.forEach { priority ->
                AssignmentPriorityChip(
                    priority = priority,
                    selected = uiState.assignmentPriority == priority,
                    onClick = { onAction(FriendsAction.OnAssignmentPriorityChanged(priority)) }
                )
            }
        }
        uiState.assignmentInputErrorMessageRes?.let { messageRes ->
            Text(
                text = stringResource(messageRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = isoDateToUtcMillis(uiState.assignmentDueDateInput)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(
                            FriendsAction.OnAssignmentDueDateChanged(
                                utcMillisToIsoDate(datePickerState.selectedDateMillis)
                            )
                        )
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.friends_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.friends_remove_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun AssignmentPriorityChip(
    priority: TodoPriority,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = when (priority) {
        TodoPriority.LOW -> Color(0xFF6FA58C)
        TodoPriority.MEDIUM -> Color(0xFF6F86C9)
        TodoPriority.HIGH -> Color(0xFFC76B7D)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) color.copy(alpha = 0.2f) else Color(0xFFE8EBF3),
        onClick = onClick,
        modifier = Modifier.testTag("friends_assignment_priority_${priority.name.lowercase()}")
    ) {
        Text(
            text = stringResource(priority.shortLabelRes()),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected) color else Color(0xFF6C7382),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun DraftItemsRow(
    items: List<AssignmentDraftItem>,
    onRemove: (Int) -> Unit
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.friends_assignment_draft_count, items.size),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF647286)
        )
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF303440)
                )
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun TodoPriority.shortLabelRes(): Int = when (this) {
    TodoPriority.LOW -> R.string.friends_assignment_priority_low
    TodoPriority.MEDIUM -> R.string.friends_assignment_priority_medium
    TodoPriority.HIGH -> R.string.friends_assignment_priority_high
}

private fun AssignmentTodoStatusStyle.statusChipColor(): Color = when (this) {
    AssignmentTodoStatusStyle.PENDING -> Color(0xFFFFF1D6)
    AssignmentTodoStatusStyle.ACCEPTED -> Color(0xFFE6F0FF)
    AssignmentTodoStatusStyle.IN_PROGRESS -> Color(0xFFE9F5EC)
    AssignmentTodoStatusStyle.DONE -> Color(0xFFE5F6EF)
    AssignmentTodoStatusStyle.REJECTED -> Color(0xFFFCE7EA)
    AssignmentTodoStatusStyle.CANCELED -> Color(0xFFE9ECF2)
}

@Composable
private fun editorTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0xFFEBEDF4),
    unfocusedContainerColor = Color(0xFFEBEDF4),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)

private fun isoDateToUtcMillis(value: String): Long? =
    runCatching {
        LocalDate.parse(value)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrNull()

private fun utcMillisToIsoDate(value: Long?): String =
    value?.let {
        Instant.ofEpochMilli(it)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .toString()
    }.orEmpty()

private val DueTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun editorDueTimeTextToMinutes(value: String): Int? {
    if (value.isBlank()) return null
    return runCatching {
        val time = LocalTime.parse(value, DueTimeFormatter)
        time.hour * 60 + time.minute
    }.getOrNull()
}

private fun editorMinutesToDueTimeText(minutes: Int): String {
    val normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    return LocalTime.of(normalized / 60, normalized % 60).format(DueTimeFormatter)
}

@Composable
private fun FriendSurface(
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color(0xFFE2E8F2)),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun FriendIdentity(
    initial: String,
    nickname: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF3FA)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF526585)
            )
        }
        Column(
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Text(
                text = nickname,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF303440)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7A8595)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF4E5D73)
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = Color(0x1A51607A)
        )
    }
}

@Composable
private fun EmptyFriendsBlock(onAddClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color(0xFFDCE6F4)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("friends_empty")
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.friends_empty_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF303440)
            )
            Text(
                text = stringResource(R.string.friends_empty_description),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF647286)
            )
            TextButton(
                onClick = onAddClick,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .testTag("friends_empty_add")
            ) {
                Text(stringResource(R.string.friends_add))
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .testTag("friends_loading"),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private const val AssignmentPreviewCollapsedCount = 3
