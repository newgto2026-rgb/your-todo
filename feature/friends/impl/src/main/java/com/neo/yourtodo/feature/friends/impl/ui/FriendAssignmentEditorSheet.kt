package com.neo.yourtodo.feature.friends.impl.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.assignedtodo.AssignmentDraftItem
import com.neo.yourtodo.core.model.assignedtodo.AssignmentMode
import com.neo.yourtodo.core.model.friends.Friend
import com.neo.yourtodo.feature.friends.impl.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendAssignmentEditorSheet(
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

private fun editorDueTimeTextToMinutes(value: String): Int? =
    dueTimeTextToMinutes(value)

private fun editorMinutesToDueTimeText(minutes: Int): String {
    val normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    return LocalTime.of(normalized / 60, normalized % 60).toString()
}
