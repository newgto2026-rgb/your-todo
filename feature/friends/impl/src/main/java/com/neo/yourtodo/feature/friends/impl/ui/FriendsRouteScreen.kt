package com.neo.yourtodo.feature.friends.impl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.core.model.friends.FriendRequest
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignedTodo
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentSummary
import com.neo.yourtodo.core.ui.YourTodoBrandHeader
import com.neo.yourtodo.core.ui.YourTodoScreenBackground
import com.neo.yourtodo.feature.friends.impl.R

@Composable
fun FriendsRouteScreen(
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    FriendsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@Composable
private fun FriendsScreen(
    uiState: FriendsUiState,
    snackbarHostState: SnackbarHostState,
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
                    YourTodoBrandHeader(
                        wordmarkContentDescription = stringResource(R.string.friends_app_header_title),
                        profileContentDescription = stringResource(R.string.friends_header_profile_icon),
                        profileInitial = uiState.profileInitial
                    )
                }
                item {
                    FriendsHeader(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { onAction(FriendsAction.OnRefresh) },
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
                                onClick = { onAction(FriendsAction.OnFriendClick(friend)) },
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
        FriendAssignmentDialog(
            uiState = uiState,
            friend = friend,
            onAction = onAction
        )
    }
}

@Composable
private fun FriendsHeader(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
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
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.testTag("friends_refresh")
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.friends_refresh))
            }
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
    onClick: () -> Unit,
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
        FriendIdentity(
            initial = friend.initial,
            nickname = friend.nickname,
            subtitle = null,
            modifier = Modifier.weight(1f)
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendAssignmentDialog(
    uiState: FriendsUiState,
    friend: Friend,
    onAction: (FriendsAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onAction(FriendsAction.OnCloseFriendDetail) },
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = Modifier.testTag("friends_assignment_dialog"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FriendIdentity(
                initial = friend.initial,
                nickname = stringResource(R.string.friends_assignment_dialog_title, friend.nickname),
                subtitle = stringResource(R.string.friends_subtitle)
            )
            if (uiState.friendDetailLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            AssignmentSummaryBlock(
                sent = uiState.friendAssignmentSummary?.sent,
                received = uiState.friendAssignmentSummary?.received
            )
            AssignmentPreviewList(
                title = stringResource(R.string.friends_assignment_sent_title),
                items = uiState.friendSentAssignedTodos
            )
            AssignmentPreviewList(
                title = stringResource(R.string.friends_assignment_received_title),
                items = uiState.friendReceivedAssignedTodos
            )
            AssignmentDraftForm(
                uiState = uiState,
                onAction = onAction
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onAction(FriendsAction.OnCloseFriendDetail) }) {
                    Text(stringResource(R.string.friends_remove_cancel))
                }
                TextButton(
                    onClick = { onAction(FriendsAction.OnSendAssignmentDrafts) },
                    enabled = uiState.assignmentDraftItems.isNotEmpty() ||
                        uiState.assignmentTitleInput.trim().isNotEmpty(),
                    modifier = Modifier.testTag("friends_assignment_send_batch")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.friends_assignment_send_batch),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
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
            progress = sent?.progressPercent ?: 0,
            modifier = Modifier.weight(1f)
        )
        AssignmentMetric(
            label = stringResource(R.string.friends_assignment_received_metric),
            count = received?.totalCount ?: 0,
            progress = received?.progressPercent ?: 0,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AssignmentMetric(
    label: String,
    count: Int,
    progress: Int,
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
                text = stringResource(R.string.friends_assignment_metric_value, count, progress),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF303440)
            )
        }
    }
}

@Composable
private fun AssignmentPreviewList(
    title: String,
    items: List<AssignedTodo>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF4E5D73)
        )
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.friends_assignment_empty),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7A8595)
            )
        } else {
            items.take(3).forEach { item ->
                Text(
                    text = stringResource(
                        R.string.friends_assignment_item_summary,
                        item.title,
                        item.progressPercent
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF303440)
                )
            }
        }
    }
}

@Composable
private fun AssignmentDraftForm(
    uiState: FriendsUiState,
    onAction: (FriendsAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.assignmentTitleInput,
            onValueChange = { onAction(FriendsAction.OnAssignmentTitleChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("friends_assignment_title"),
            placeholder = { Text(stringResource(R.string.friends_assignment_title_placeholder)) },
            singleLine = true
        )
        OutlinedTextField(
            value = uiState.assignmentDescriptionInput,
            onValueChange = { onAction(FriendsAction.OnAssignmentDescriptionChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("friends_assignment_description"),
            placeholder = { Text(stringResource(R.string.friends_assignment_description_placeholder)) },
            minLines = 2
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.assignmentDueDateInput,
                onValueChange = { onAction(FriendsAction.OnAssignmentDueDateChanged(it)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("friends_assignment_due_date"),
                placeholder = { Text(stringResource(R.string.friends_assignment_due_date_placeholder)) },
                singleLine = true
            )
            TodoPriority.entries.forEach { priority ->
                TextButton(
                    onClick = { onAction(FriendsAction.OnAssignmentPriorityChanged(priority)) },
                    modifier = Modifier.testTag("friends_assignment_priority_${priority.name.lowercase()}")
                ) {
                    Text(
                        text = stringResource(priority.shortLabelRes()),
                        color = if (uiState.assignmentPriority == priority) {
                            Color(0xFF676CB4)
                        } else {
                            Color(0xFF647286)
                        }
                    )
                }
            }
        }
        DraftItemsRow(
            items = uiState.assignmentDraftItems,
            onRemove = { onAction(FriendsAction.OnRemoveAssignmentDraft(it)) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { onAction(FriendsAction.OnAddAssignmentDraft) },
                enabled = uiState.assignmentTitleInput.trim().isNotEmpty(),
                modifier = Modifier.testTag("friends_assignment_add_draft")
            ) {
                Text(stringResource(R.string.friends_assignment_add_draft))
            }
            Button(
                onClick = { onAction(FriendsAction.OnSendAssignmentNow) },
                enabled = uiState.assignmentTitleInput.trim().isNotEmpty(),
                modifier = Modifier.testTag("friends_assignment_send_now"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF676CB4))
            ) {
                Text(stringResource(R.string.friends_assignment_send_now))
            }
        }
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
