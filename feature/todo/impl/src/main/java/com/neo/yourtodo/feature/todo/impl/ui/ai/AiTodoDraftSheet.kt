package com.neo.yourtodo.feature.todo.impl.ui.ai

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.aitodo.AiTodoPerson
import com.neo.yourtodo.feature.todo.impl.R
import com.neo.yourtodo.feature.todo.impl.ui.TodoEditorDueDateSelector
import com.neo.yourtodo.feature.todo.impl.ui.TodoEditorDueTimeSelector

@Composable
fun AiTodoDraftRoute(
    onDismiss: () -> Unit,
    viewModel: AiTodoDraftViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { sideEffect ->
            when (sideEffect) {
                AiTodoDraftSideEffect.Saved -> onDismiss()
            }
        }
    }

    AiTodoDraftSheet(
        uiState = uiState,
        onPromptChange = viewModel::onPromptChange,
        onAnalyze = viewModel::onAnalyze,
        onDismiss = onDismiss,
        onSave = viewModel::onSave,
        onDraftSelected = viewModel::onDraftSelected,
        onDraftTitleChange = viewModel::onDraftTitleChange,
        onDraftAssigneeChange = viewModel::onDraftAssigneeChange,
        onDraftDueDateChange = viewModel::onDraftDueDateChange,
        onDraftDueTimeChange = viewModel::onDraftDueTimeChange,
        onDraftPriorityChange = viewModel::onDraftPriorityChange,
        onDraftDelete = viewModel::onDraftDelete
    )
}

@Composable
private fun AiTodoDraftSheet(
    uiState: AiTodoDraftUiState,
    onPromptChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDraftSelected: (String, Boolean) -> Unit,
    onDraftTitleChange: (String, String) -> Unit,
    onDraftAssigneeChange: (String, String?) -> Unit,
    onDraftDueDateChange: (String, String) -> Unit,
    onDraftDueTimeChange: (String, String) -> Unit,
    onDraftPriorityChange: (String, TodoPriority) -> Unit,
    onDraftDelete: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val lockSheetDragConnection = remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (scrollState.value == 0 && available.y > 0f) {
                    Offset(x = 0f, y = available.y)
                } else {
                    Offset.Zero
                }

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (scrollState.value == 0 && available.y > 0f) {
                    Velocity(x = 0f, y = available.y)
                } else {
                    Velocity.Zero
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F8FC), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .nestedScroll(lockSheetDragConnection)
            .verticalScroll(scrollState)
            .testTag("ai_todo_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF5F5391)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.todo_ai_sheet_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF272B36)
                )
                Text(
                    text = stringResource(R.string.todo_ai_sheet_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF697085)
                )
            }
            IconButton(
                onClick = onDismiss,
                enabled = !uiState.isSaving,
                modifier = Modifier.testTag("ai_todo_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.todo_ai_close)
                )
            }
        }

        OutlinedTextField(
            value = uiState.prompt,
            onValueChange = onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .testTag("ai_todo_prompt_input"),
            label = { Text(stringResource(R.string.todo_ai_prompt_label)) },
            placeholder = { Text(stringResource(R.string.todo_ai_prompt_placeholder)) },
            minLines = 4
        )

        uiState.errorMessageRes?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAnalyze,
                enabled = !uiState.isAnalyzing && !uiState.isSaving,
                modifier = Modifier.testTag("ai_todo_analyze_button")
            ) {
                if (uiState.isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.todo_ai_analyze))
            }
            TextButton(onClick = onDismiss, enabled = !uiState.isSaving) {
                Text(stringResource(R.string.todo_editor_cancel))
            }
        }

        if (uiState.draftItems.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    R.plurals.todo_ai_draft_count,
                    uiState.draftItems.size,
                    uiState.draftItems.size
                ),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF2E3442)
            )
            uiState.modelName?.let {
                Text(
                    text = stringResource(R.string.todo_ai_model_label, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF777F93)
                )
            }
            uiState.draftItems.forEach { draft ->
                AiTodoDraftCard(
                    draft = draft,
                    people = uiState.people,
                    onSelectedChange = { onDraftSelected(draft.id, it) },
                    onTitleChange = { onDraftTitleChange(draft.id, it) },
                    onAssigneeChange = { onDraftAssigneeChange(draft.id, it) },
                    onDueDateChange = { onDraftDueDateChange(draft.id, it) },
                    onDueTimeChange = { onDraftDueTimeChange(draft.id, it) },
                    onPriorityChange = { onDraftPriorityChange(draft.id, it) },
                    onDelete = { onDraftDelete(draft.id) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onAnalyze, enabled = !uiState.isAnalyzing && !uiState.isSaving) {
                    Text(stringResource(R.string.todo_ai_reanalyze))
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onSave,
                    enabled = !uiState.isSaving && !uiState.isAnalyzing,
                    modifier = Modifier.testTag("ai_todo_save_button")
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.todo_ai_register_selected))
                }
            }
        }
    }
}

@Composable
private fun AiTodoDraftCard(
    draft: AiTodoDraftUiModel,
    people: List<AiTodoPerson>,
    onSelectedChange: (Boolean) -> Unit,
    onTitleChange: (String) -> Unit,
    onAssigneeChange: (String?) -> Unit,
    onDueDateChange: (String) -> Unit,
    onDueTimeChange: (String) -> Unit,
    onPriorityChange: (TodoPriority) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = if (draft.needsReview || draft.errorMessageRes != null) {
                Color(0xFFE5A0A0)
            } else {
                Color(0xFFE2E7F0)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = draft.isSelected, onCheckedChange = onSelectedChange)
                Text(
                    text = stringResource(R.string.todo_ai_draft_item),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF4D5668),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.todo_editor_delete),
                        tint = Color(0xFF9C4B4B)
                    )
                }
            }
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.todo_ai_draft_title_label)) },
                singleLine = true,
                isError = draft.errorMessageRes == R.string.todo_error_title_required
            )
            AssigneeMenu(
                people = people,
                selectedAssigneeId = draft.assigneeId,
                onAssigneeChange = onAssigneeChange
            )
            AiDraftDateTimeSection(
                dueDateInput = draft.dueDateInput,
                dueTimeInput = draft.dueTimeInput,
                onDueDateChange = onDueDateChange,
                onDueTimeChange = onDueTimeChange
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodoPriority.entries.forEach { priority ->
                    val selected = draft.priority == priority
                    Surface(
                        onClick = { onPriorityChange(priority) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) Color(0xFF5F63A8) else Color(0xFFF1F3F8),
                        contentColor = if (selected) Color.White else Color(0xFF4F5870),
                        border = BorderStroke(1.dp, if (selected) Color(0xFF5F63A8) else Color(0xFFDCE2EC))
                    ) {
                        Text(
                            text = stringResource(priority.labelRes()),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
            draft.errorMessageRes?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (draft.needsReview && !draft.reviewReason.isNullOrBlank()) {
                Text(
                    text = draft.reviewReason,
                    color = Color(0xFF93630B),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AiDraftDateTimeSection(
    dueDateInput: String,
    dueTimeInput: String,
    onDueDateChange: (String) -> Unit,
    onDueTimeChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.todo_ai_due_date_label),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF7A7F8C)
        )
        TodoEditorDueDateSelector(
            dueDateInput = dueDateInput,
            onDateInputChange = onDueDateChange,
            enabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_todo_due_date_selector")
        )
        Text(
            text = stringResource(R.string.todo_ai_due_time_label),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF7A7F8C)
        )
        TodoEditorDueTimeSelector(
            dueTimeInput = dueTimeInput,
            onDueTimeInputChange = onDueTimeChange,
            enabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_todo_due_time_selector")
        )
    }
}

@Composable
private fun AssigneeMenu(
    people: List<AiTodoPerson>,
    selectedAssigneeId: String?,
    onAssigneeChange: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = people.firstOrNull { it.id == selectedAssigneeId }?.displayName
        ?: stringResource(R.string.todo_ai_assignee_unselected)
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF7F8FC),
            border = BorderStroke(1.dp, Color(0xFFDCE2EC)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedName,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Icon(Icons.Default.ExpandMore, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.todo_ai_assignee_unselected)) },
                onClick = {
                    onAssigneeChange(null)
                    expanded = false
                }
            )
            people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(person.displayName) },
                    onClick = {
                        onAssigneeChange(person.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@StringRes
private fun TodoPriority.labelRes(): Int =
    when (this) {
        TodoPriority.LOW -> R.string.todo_priority_low
        TodoPriority.MEDIUM -> R.string.todo_priority_medium
        TodoPriority.HIGH -> R.string.todo_priority_high
    }
