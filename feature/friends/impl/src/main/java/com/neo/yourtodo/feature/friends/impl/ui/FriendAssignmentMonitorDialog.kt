package com.neo.yourtodo.feature.friends.impl.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.assignedtodo.AssignmentSummary
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.feature.friends.impl.R

@Composable
internal fun FriendAssignmentMonitorDialog(
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

private fun AssignmentTodoStatusStyle.statusChipColor(): Color = when (this) {
    AssignmentTodoStatusStyle.PENDING -> Color(0xFFFFF1D6)
    AssignmentTodoStatusStyle.ACCEPTED -> Color(0xFFE6F0FF)
    AssignmentTodoStatusStyle.IN_PROGRESS -> Color(0xFFE9F5EC)
    AssignmentTodoStatusStyle.DONE -> Color(0xFFE5F6EF)
    AssignmentTodoStatusStyle.REJECTED -> Color(0xFFFCE7EA)
    AssignmentTodoStatusStyle.CANCELED -> Color(0xFFE9ECF2)
}
